import asyncio
import glob
import logging
import os
import sys
import tempfile
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from typing import Callable, Dict, List, Optional, Tuple

import cv2
import numpy as np
import torch
import torch.nn.functional as F

from config import MUSETALK_ROOT, BASE_VIDEO_DIR, DEVICE

os.chdir(MUSETALK_ROOT)
if str(MUSETALK_ROOT) not in sys.path:
    sys.path.insert(0, str(MUSETALK_ROOT))

from musetalk.utils.utils import load_all_model
from musetalk.whisper.audio2feature import Audio2Feature
from musetalk.utils.preprocessing import get_landmark_and_bbox
from musetalk.utils.face_parsing import FaceParsing
from musetalk.utils.blending import get_image_prepare_material
from scripts.realtime_inference import video2imgs

logger = logging.getLogger(__name__)


# 输出分辨率（W, H）。与 helper.encode_frame 默认值一致。
TARGET_W, TARGET_H = 480, 854
BATCH = 32


class MuseTalkEngine:
    def __init__(self, musetalk_root=None, base_video_dir=None, device=None):
        self.device = device or DEVICE
        self.musetalk_root = Path(musetalk_root) if musetalk_root else MUSETALK_ROOT
        self.base_video_dir = Path(base_video_dir) if base_video_dir else BASE_VIDEO_DIR
        self._lock = asyncio.Lock()
        self._cancel_flag = False

        self.avatar_cache: dict = {}

        # 推理通用加速开关
        torch.backends.cudnn.benchmark = True
        try:
            torch.set_float32_matmul_precision("high")  # 启用 TF32 matmul
            torch.backends.cuda.matmul.allow_tf32 = True
            torch.backends.cudnn.allow_tf32 = True
        except Exception:
            pass

        logger.info("正在加载 MuseTalk 模型，请稍候...")
        self.vae, self.unet, self.pe = load_all_model(
            unet_model_path=str(self.musetalk_root / "models/musetalk/pytorch_model.bin"),
            vae_type="sd-vae",
            unet_config=str(self.musetalk_root / "models/musetalk/musetalk.json"),
            device=self.device
        )
        self.vae.vae = self.vae.vae.half().to(self.device)
        self.unet.model = self.unet.model.half().to(self.device)
        self.vae.vae = self.vae.vae.to(memory_format=torch.channels_last)
        self.unet.model = self.unet.model.to(memory_format=torch.channels_last)
        self.unet.model.eval()
        try:
            self.unet.model.fuse_qkv_projections()
            logger.info("[engine] UNet QKV projections 已融合")
        except AttributeError:
            logger.debug("[engine] 当前 UNet 不支持 fuse_qkv_projections，跳过")
        except Exception as e:
            logger.warning(f"[engine] fuse_qkv_projections 失败: {e}")
        self.vae.vae.eval()

        # 探测 sd-vae 的 scaling factor，decode 时需要用 latents / scale
        self._vae_scale: float = float(getattr(self.vae.vae.config, "scaling_factor", 0.18215))

        self.audio_processor = Audio2Feature(
            model_path=str(self.musetalk_root / "models/whisper/tiny.pt")
        )
        self.fp = FaceParsing(left_cheek_width=90, right_cheek_width=90)

        # 懒编译 + 第二条 CUDA 流（后处理）
        self._unet_compiled: Optional[Callable] = None
        self._vae_compiled = None
        try:
            self._post_stream: Optional[torch.cuda.Stream] = torch.cuda.Stream()
        except Exception as e:
            logger.warning(f"[engine] 创建 post_stream 失败，将使用默认流：{e}")
            self._post_stream = None

        # 用于把 CPU 端 audio2feat 与 GPU 端预热 / 上一次推理尾巴并行起来
        self._audio_executor = ThreadPoolExecutor(
            max_workers=1, thread_name_prefix="musetalk-audio"
        )

        self._warmed_up = False

        self._tea_cache_audio: Optional[torch.Tensor] = None   # 上一 batch 的 audio embedding
        self._tea_cache_latents: Optional[torch.Tensor] = None  # 上一 batch 的 UNet 输出
        self.tea_cache_threshold: float = 0.97  # 相似度阈值，可调

        logger.info("MuseTalk 模型加载完毕！")

    def get_portrait_path(self, attraction_id: str) -> str:
        video_path = self.base_video_dir / f"{attraction_id}.mp4"
        if not video_path.exists():
            raise FileNotFoundError(f"未找到景点视频: {video_path}")
        return str(video_path)

    # --------------------------------------------------------------
    # load_avatar：一次性预处理 + 把所有静态资源搬到 GPU
    # --------------------------------------------------------------
    def load_avatar(self, attraction_id: str):
        if attraction_id in self.avatar_cache:
            return

        try:
            video_path = self.get_portrait_path(attraction_id)
            logger.info(f"开始预处理景点 {attraction_id} 的视频底模...")

            with tempfile.TemporaryDirectory() as temp_dir:
                video2imgs(video_path, temp_dir, ext='.png')
                input_img_list = sorted(glob.glob(os.path.join(temp_dir, '*.[pP][nN][gG]')))
                coord_list, frame_list = get_landmark_and_bbox(input_img_list, 0)

                latents_cpu: List[torch.Tensor] = []
                ori_resized_list: List[np.ndarray] = []      # (Ht, Wt, 3) uint8 RGB
                masks_resized_list: List[np.ndarray] = []    # (ch_t, cw_t) float32 [0,1]
                # 每帧两个矩形：face_box（recon 贴入区，valid_bbox 映射到目标）+ crop_box（mask 软混合区）
                # 形如 (face_box, crop_box)，每个都是 (x, y, x1, y1)
                box_pairs: List[Tuple[Tuple[int, int, int, int], Tuple[int, int, int, int]]] = []
                coord_placeholder = (0.0, 0.0, 0.0, 0.0)

                for bbox, frame in zip(coord_list, frame_list):
                    if bbox == coord_placeholder:
                        continue

                    H_orig, W_orig = frame.shape[:2]
                    sx = TARGET_W / W_orig
                    sy = TARGET_H / H_orig

                    x1, y1, x2, y2 = bbox
                    x1i = max(0, int(round(x1)))
                    y1i = max(0, int(round(y1)))
                    x2i = min(W_orig, int(round(x2)))
                    y2i = min(H_orig, int(round(y2 + 10)))
                    valid_bbox = [x1i, y1i, x2i, y2i]

                    crop_frame = frame[y1i:y2i, x1i:x2i]
                    resized_crop_frame = cv2.resize(
                        crop_frame, (256, 256), interpolation=cv2.INTER_LANCZOS4
                    )
                    latent = self.vae.get_latents_for_unet(resized_crop_frame)
                    latents_cpu.append(latent.detach())

                    # mask_full 是 crop_box 大小的 uint8 alpha mask（已 GaussianBlur）；
                    # crop_box = (x_s, y_s, x_e, y_e) 在原图坐标
                    mask_full, crop_box = get_image_prepare_material(frame, valid_bbox, fp=self.fp)
                    cx_s, cy_s, cx_e, cy_e = [int(round(v)) for v in crop_box]
                    cx_s = max(0, cx_s); cy_s = max(0, cy_s)
                    cx_e = min(W_orig, cx_e); cy_e = min(H_orig, cy_e)

                    # 整帧 BGR → 目标尺寸 → RGB
                    ori_t = cv2.resize(frame, (TARGET_W, TARGET_H), interpolation=cv2.INTER_AREA)
                    ori_t = cv2.cvtColor(ori_t, cv2.COLOR_BGR2RGB)

                    # face_box（recon 的硬贴入区）和 crop_box（mask 的软混合区）映射到目标坐标系
                    fx  = max(0, min(TARGET_W, int(round(x1i * sx))))
                    fy  = max(0, min(TARGET_H, int(round(y1i * sy))))
                    fx1 = max(0, min(TARGET_W, int(round(x2i * sx))))
                    fy1 = max(0, min(TARGET_H, int(round(y2i * sy))))

                    cxs = max(0, min(TARGET_W, int(round(cx_s * sx))))
                    cys = max(0, min(TARGET_H, int(round(cy_s * sy))))
                    cxe = max(0, min(TARGET_W, int(round(cx_e * sx))))
                    cye = max(0, min(TARGET_H, int(round(cy_e * sy))))

                    # face_box 必须落在 crop_box 内（兜底夹一下）
                    fx  = max(fx,  cxs); fy  = max(fy,  cys)
                    fx1 = min(fx1, cxe); fy1 = min(fy1, cye)

                    if fx1 <= fx or fy1 <= fy or cxe <= cxs or cye <= cys:
                        latents_cpu.pop()
                        continue

                    ori_resized_list.append(ori_t)
                    box_pairs.append(((fx, fy, fx1, fy1), (cxs, cys, cxe, cye)))

                    # mask 缩到目标坐标系下 crop_box 的尺寸
                    cw_t, ch_t = cxe - cxs, cye - cys
                    if mask_full.size == 0:
                        mask_resized = np.full((ch_t, cw_t), 255, dtype=np.uint8)
                    else:
                        mask_resized = cv2.resize(
                            mask_full, (cw_t, ch_t), interpolation=cv2.INTER_LINEAR
                        )
                    masks_resized_list.append(mask_resized.astype(np.float32) / 255.0)

                if not latents_cpu:
                    raise RuntimeError(f"avatar {attraction_id} 没有有效帧")

                # ---- stack 到 GPU ----
                latents_gpu = torch.cat(latents_cpu, dim=0).to(
                    self.device, dtype=torch.float16, non_blocking=True
                ).contiguous()  # (N, 4, 32, 32)

                ori_np = np.stack(ori_resized_list, axis=0)  # (N, Ht, Wt, 3) uint8 RGB
                ori_resized_gpu = torch.from_numpy(ori_np).to(
                    self.device, non_blocking=True
                ).permute(0, 3, 1, 2).contiguous()  # (N, 3, Ht, Wt) uint8

                # mask 大小不一致（每帧脸框不同），存 list of cuda tensors
                masks_gpu: List[torch.Tensor] = []
                for m in masks_resized_list:
                    masks_gpu.append(
                        torch.from_numpy(m).to(self.device, dtype=torch.float16,
                                               non_blocking=True).unsqueeze(0).contiguous()
                    )  # (1, bh, bw) fp16

                N = ori_resized_gpu.shape[0]

                # ---- 预计算 per-k 的 face_box 区域常量，blend 时不再做重复转换 ----
                # 关键观察：原始 blend 在 crop_box 外保持 ori 不变；在 crop_box 内 face_box
                # 之外（mask 软边区）也等价于 ori（body_crop == ori_crop）。所以输出与 ori
                # 唯一不同的就是 face_box 区域。我们只需 face_box 内的 ori 与 mask。
                ori_face_norm_list: List[torch.Tensor] = []   # (3, fbh, fbw) fp16 [0,1]
                mask_face_list: List[torch.Tensor] = []       # (1, fbh, fbw) fp16 [0,1]
                face_boxes_list: List[Tuple[int, int, int, int]] = []
                for k in range(N):
                    (fx, fy, fx1, fy1), (cxs, cys, cxe, cye) = box_pairs[k]
                    fbh, fbw = fy1 - fy, fx1 - fx
                    cch, ccw = cye - cys, cxe - cxs

                    mask_full_fp16 = masks_gpu[k]  # (1, mh, mw) fp16，对应原始 crop_box 大小
                    if mask_full_fp16.shape[-2:] != (cch, ccw):
                        mask_crop = F.interpolate(
                            mask_full_fp16.unsqueeze(0), size=(cch, ccw),
                            mode="bilinear", align_corners=False, antialias=False,
                        ).squeeze(0)
                    else:
                        mask_crop = mask_full_fp16
                    # face_box 在 crop_box 内的相对坐标
                    fy_lo, fy_hi = fy - cys, fy1 - cys
                    fx_lo, fx_hi = fx - cxs, fx1 - cxs
                    mask_face = mask_crop[:, fy_lo:fy_hi, fx_lo:fx_hi].contiguous()
                    mask_face_list.append(mask_face)

                    ori_face_norm = (
                        ori_resized_gpu[k, :, fy:fy1, fx:fx1].to(torch.float16) / 255.0
                    ).contiguous()
                    ori_face_norm_list.append(ori_face_norm)
                    face_boxes_list.append((fx, fy, fx1, fy1))

                # 把帧按 (fbh, fbw) 分组，blend 时同组用一次批量 F.interpolate
                size_groups: Dict[Tuple[int, int], List[int]] = {}
                for k, (fx, fy, fx1, fy1) in enumerate(face_boxes_list):
                    key = (fy1 - fy, fx1 - fx)
                    size_groups.setdefault(key, []).append(k)

                indices = list(range(N)) + list(range(N))[::-1]  # 前进 + 倒放

                self.avatar_cache[attraction_id] = {
                    "latents_gpu": latents_gpu,
                    "ori_resized_gpu": ori_resized_gpu,
                    "masks_gpu": masks_gpu,
                    "box_pairs": box_pairs,          # list[(face_box, crop_box)] 目标坐标系
                    # 以下为 blend 加速所需的 per-k 预计算资源
                    "ori_face_norm_list": ori_face_norm_list,   # list[(3, fbh, fbw) fp16]
                    "mask_face_list": mask_face_list,           # list[(1, fbh, fbw) fp16]
                    "face_boxes": face_boxes_list,              # list[(fx, fy, fx1, fy1)]
                    "size_groups": size_groups,                 # {(fbh, fbw): [k, ...]}
                    "indices": indices,
                    "current_idx": 0,
                    "N": N,
                }
                vram_gb = (
                    latents_gpu.numel() * latents_gpu.element_size()
                    + ori_resized_gpu.numel() * ori_resized_gpu.element_size()
                    + sum(m.numel() * m.element_size() for m in masks_gpu)
                    + sum(t.numel() * t.element_size() for t in ori_face_norm_list)
                    + sum(t.numel() * t.element_size() for t in mask_face_list)
                ) / (1024 ** 3)
                logger.info(
                    f"✅ 景点 {attraction_id} 预处理完成！有效帧={N}，"
                    f"循环长度={len(indices)}，GPU 占用≈{vram_gb:.2f} GB"
                )
        except Exception as e:
            logger.error(f"预处理发生异常: {e}", exc_info=True)
            raise

    def reset_cancel(self):
        self._cancel_flag = False
        self._tea_cache_audio = None
        self._tea_cache_latents = None

    def cancel(self):
        self._cancel_flag = True

    # --------------------------------------------------------------
    # UNet 懒编译 + 失败回落
    # --------------------------------------------------------------
    def _get_or_compile_unet(self) -> Callable:
        if self._unet_compiled is not None:
            return self._unet_compiled
        try:
            self._unet_compiled = torch.compile(
                self.unet.model,
                mode="reduce-overhead",
                fullgraph=True,
                dynamic=False,
            )
            logger.info("[engine] UNet torch.compile 已启用 (reduce-overhead)")
        except Exception as e:
            logger.warning(f"[engine] torch.compile 失败，使用 eager UNet：{e}")
            self._unet_compiled = self.unet.model
        return self._unet_compiled

    # --------------------------------------------------------------
    # VAE decoder 懒编译 + 失败回落
    # --------------------------------------------------------------
    def _get_or_compile_vae(self) -> Callable:
        if self._vae_compiled is not None:
            return self._vae_compiled
        try:
            self._vae_compiled = torch.compile(
                self.vae.vae.decode,
                mode="reduce-overhead",
                fullgraph=False,
            )
            logger.info("[engine] VAE torch.compile 已启用")
        except Exception as e:
            logger.warning(f"[engine] VAE torch.compile 失败，使用 eager VAE：{e}")
            self._vae_compiled = self.vae.vae.decode
        return self._vae_compiled

    # --------------------------------------------------------------
    # 端到端预热：触发 torch.compile/CUDAGraph、VAE decode kernel 选择、
    # whisper 首调初始化、post_stream blend、NVJPEG 编码初始化
    # --------------------------------------------------------------
    def warmup(self, attraction_id: str) -> None:
        if self._warmed_up:
            return
        avatar = self.avatar_cache.get(attraction_id)
        if not avatar:
            logger.warning(f"[engine] warmup 跳过，avatar={attraction_id} 未加载")
            return

        from utils.helper import encode_frames_gpu

        t0 = time.time()
        # 25fps × BATCH(32) ≈ 1.28s，给到 2s 保证至少跑满一个 padded batch
        silent_pcm = b"\x00\x00" * int(16000 * 2.0)

        saved_idx = avatar["current_idx"]
        saved_cancel = self._cancel_flag
        self._cancel_flag = False
        try:
            for blended_list in self.generate_frames(silent_pcm, attraction_id):
                try:
                    encode_frames_gpu(blended_list, quality=80)
                except Exception:
                    pass
            if torch.cuda.is_available():
                torch.cuda.synchronize()
        except Exception as e:
            logger.warning(f"[engine] warmup 异常（不影响服务启动）：{e}", exc_info=True)
        finally:
            avatar["current_idx"] = saved_idx
            self._cancel_flag = saved_cancel
            self._warmed_up = True
        logger.info(f"[engine] 端到端预热完成，耗时 {time.time() - t0:.2f}s")

    # --------------------------------------------------------------
    # VAE decode：直接拿 fp16 tensor，省 D2H/H2D
    # --------------------------------------------------------------
    @torch.inference_mode()
    def _vae_decode_tensor(self, latents: torch.Tensor) -> torch.Tensor:
        """latents: (B,4,32,32) fp16 → (B,3,256,256) fp16 in [0,1] RGB."""
        vae_fn = self._get_or_compile_vae()
        imgs = vae_fn(latents / self._vae_scale).sample  # fp16 in [-1,1]
        imgs = imgs.to(memory_format=torch.contiguous_format)
        imgs = (imgs.clamp(-1.0, 1.0) + 1.0) * 0.5
        return imgs

    # --------------------------------------------------------------
    # 批量 GPU 混合：把整批 recon 一次性贴回 ori
    # --------------------------------------------------------------
    @torch.inference_mode()
    def _blend_batch_gpu(
        self,
        recon: torch.Tensor,        # (B, 3, 256, 256) fp16 [0,1] RGB
        k_list: List[int],          # 长度 B，每个元素是 avatar 帧索引 k
        avatar: dict,
    ) -> List[torch.Tensor]:
        """等价于对每个 j 调用一次 _blend_one_gpu，但合并到几次大 kernel。

        关键观察：原版 blend 的输出与 ori 仅在 face_box 区域不同（crop_box 内 face_box
        之外的 mask 软边区，body_crop == ori_crop，blend 结果等于 ori 自身）。
        所以这里我们只需要：
          1) 把整批 ori 一次 index_select 拷出来作为输出底；
          2) 按 face_box 尺寸 (fbh, fbw) 分组，对同组的 recon 做一次批量 F.interpolate；
          3) 在每帧 face_box 区域用预计算好的 ori_face_norm + mask_face 做 α 混合并写回。
        """
        ori_gpu: torch.Tensor = avatar["ori_resized_gpu"]
        ori_face_norm_list: List[torch.Tensor] = avatar["ori_face_norm_list"]
        mask_face_list: List[torch.Tensor] = avatar["mask_face_list"]
        face_boxes: List[Tuple[int, int, int, int]] = avatar["face_boxes"]
        size_groups: Dict[Tuple[int, int], List[int]] = avatar["size_groups"]

        B = recon.shape[0]
        k_tensor = torch.as_tensor(k_list, device=recon.device, dtype=torch.long)
        # 一次 index_select 完成整批 ori 的拷贝（替代原本每帧一次的 .clone()）
        out_batch = ori_gpu.index_select(0, k_tensor).contiguous()  # (B,3,Ht,Wt) uint8

        # 把 batch 中出现的 k 按 face_box 尺寸归并：同尺寸的所有 batch 位置一起做插值
        # 注意：batch 里同一个 j 可能多次出现同一个 k，没关系，我们按 j 写回
        per_size: Dict[Tuple[int, int], List[int]] = {}
        for j, k in enumerate(k_list):
            (fx, fy, fx1, fy1) = face_boxes[k]
            per_size.setdefault((fy1 - fy, fx1 - fx), []).append(j)

        for (fbh, fbw), js in per_size.items():
            if not js:
                continue
            j_idx = torch.as_tensor(js, device=recon.device, dtype=torch.long)
            # 一次性 resize 同尺寸的所有 face
            faces = F.interpolate(
                recon.index_select(0, j_idx), size=(fbh, fbw),
                mode="bilinear", align_corners=False, antialias=False,
            ).clamp_(0.0, 1.0)  # (g, 3, fbh, fbw) fp16

            # 收集该组每帧对应的 ori_face_norm 和 mask_face（不同 k 但同尺寸）
            ori_face_stack = torch.stack(
                [ori_face_norm_list[k_list[j]] for j in js], dim=0
            )  # (g, 3, fbh, fbw) fp16
            mask_face_stack = torch.stack(
                [mask_face_list[k_list[j]] for j in js], dim=0
            )  # (g, 1, fbh, fbw) fp16

            blended = ori_face_stack * (1.0 - mask_face_stack) + faces * mask_face_stack
            blended_u8 = (blended.clamp_(0.0, 1.0) * 255.0 + 0.5).to(torch.uint8)

            # 写回到 out_batch 各自的 face_box
            for local_i, j in enumerate(js):
                (fx, fy, fx1, fy1) = face_boxes[k_list[j]]
                out_batch[j, :, fy:fy1, fx:fx1] = blended_u8[local_i]

        return list(out_batch.unbind(0))

    # --------------------------------------------------------------
    # generate_frames：音频 → 帧（list[Tensor cuda uint8]）
    # --------------------------------------------------------------
    def _audio_to_chunks(self, audio_chunk: bytes):
        """纯 CPU/whisper 路径，可在线程池里跑，避免阻塞 GPU 调度。"""
        try:
            audio_np = np.frombuffer(audio_chunk, dtype=np.int16).astype(np.float32) / 32768.0
            whisper_feature = self.audio_processor.audio2feat(audio_np)
        except Exception as e:
            logger.warning(f"[engine] audio2feat(ndarray) 失败，回落 wav 落盘：{e}")
            import wave
            tmpdir = "/dev/shm" if os.path.isdir("/dev/shm") else None
            with tempfile.NamedTemporaryFile(dir=tmpdir, suffix=".wav", delete=True) as tmp:
                with wave.open(tmp.name, "wb") as wf:
                    wf.setnchannels(1)
                    wf.setsampwidth(2)
                    wf.setframerate(16000)
                    wf.writeframes(audio_chunk)
                whisper_feature = self.audio_processor.audio2feat(tmp.name)
        return self.audio_processor.feature2chunks(whisper_feature, fps=25)

    def generate_frames(self, audio_chunk: bytes, attraction_id: str):
        avatar = self.avatar_cache.get(attraction_id)
        if not avatar:
            return

        # 1) audio2feat 立刻丢到后台线程跑（NumPy / whisper-tiny 主要是 CPU + 小 GPU op，
        #    GIL 大部分时间会释放）。主线程同时做 GPU 端的预热与首批准备。
        chunks_future = self._audio_executor.submit(self._audio_to_chunks, audio_chunk)

        latents_gpu: torch.Tensor = avatar["latents_gpu"]
        indices: List[int] = avatar["indices"]
        idx0: int = avatar["current_idx"]
        nN: int = len(indices)
        unet_fn = self._get_or_compile_unet()

        # 在等 audio2feat 期间触发一次 cudaStreamSynchronize 之外的轻量 GPU 调度，
        # 让 CUDA context / cudnn handle 保持热（成本几乎为零，但能消除冷启动空隙）。
        if torch.cuda.is_available():
            _kicker = latents_gpu.narrow(0, 0, 1).sum()  # 极小 kernel
            del _kicker

        whisper_chunks = chunks_future.result()
        n = len(whisper_chunks)
        if n == 0:
            return

        default_stream = torch.cuda.current_stream() if torch.cuda.is_available() else None
        post_stream = self._post_stream

        # 一拍流水线：上一批的 blend 与下一批的 UNet 在不同 stream 上并行
        prev_blend: Optional[List[torch.Tensor]] = None
        prev_event: Optional[torch.cuda.Event] = None
        prev_bs: int = 0

        for i in range(0, n, BATCH):
            if self._cancel_flag:
                return

            batch_chunks = whisper_chunks[i:i + BATCH]
            bs = len(batch_chunks)
            if bs < BATCH:
                batch_chunks = list(batch_chunks) + [batch_chunks[-1]] * (BATCH - bs)

            # 取 latents（GPU index_select）+ k 列表（仅 bs 个有效，pad 部分对最终输出不暴露）
            k_list_full = [indices[(idx0 + i + j) % nN] for j in range(BATCH)]
            sel = torch.as_tensor(k_list_full, device=self.device, dtype=torch.long)
            latent_batch = latents_gpu.index_select(0, sel).contiguous()  # (B,4,32,32)
            latent_batch = latent_batch.to(memory_format=torch.channels_last)
            audio_batch = torch.from_numpy(np.stack(batch_chunks)).to(
                self.device, dtype=torch.float16, non_blocking=True
            )

            # TeaCache：检查本 batch audio 是否与上一批高度相似
            _use_cache = False
            if self._tea_cache_audio is not None and self._tea_cache_latents is not None:
                _sim = F.cosine_similarity(
                    audio_batch.to(torch.float32).flatten(1),
                    self._tea_cache_audio.to(torch.float32).flatten(1),
                    dim=1
                ).mean().item()
                if _sim >= self.tea_cache_threshold:
                    pred_latents = self._tea_cache_latents
                    _use_cache = True
                    logger.debug(f"[engine] TeaCache 命中，相似度={_sim:.4f}，跳过 UNet")

            with torch.inference_mode():
                if not _use_cache:
                    pred_latents = unet_fn(
                        latent_batch, 0, encoder_hidden_states=audio_batch
                    ).sample
                    if self._unet_compiled is not self.unet.model:
                        pred_latents = pred_latents.clone()
                    # 更新 TeaCache
                    self._tea_cache_audio = audio_batch.detach()
                    self._tea_cache_latents = pred_latents.detach()

                recon = self._vae_decode_tensor(pred_latents)

            # 在 post_stream 上排布本批 blend：等默认流的 recon 就绪后开跑
            if post_stream is not None and default_stream is not None:
                post_stream.wait_stream(default_stream)
                with torch.cuda.stream(post_stream):
                    blend_list = self._blend_batch_gpu(recon, k_list_full[:bs], avatar)
                    # 让张量内存被默认流引用期间不会被 caching allocator 回收
                    for t in blend_list:
                        t.record_stream(default_stream)
                event = torch.cuda.Event()
                event.record(post_stream)
            else:
                blend_list = self._blend_batch_gpu(recon, k_list_full[:bs], avatar)
                event = None

            # 关键：先把"上一批"的结果交付出去，再进入下一轮 UNet。
            # 这样本批 blend（在 post_stream 上）就能与下一批 UNet（默认流）真正并行。
            if prev_blend is not None:
                if prev_event is not None:
                    prev_event.synchronize()  # CPU 等 GPU；等待期间默认流仍在跑下一批 UNet
                yield prev_blend
                avatar["current_idx"] = (avatar["current_idx"] + prev_bs) % nN
                if self._cancel_flag:
                    return

            prev_blend = blend_list
            prev_event = event
            prev_bs = bs

        # 收尾：交付最后一批
        if prev_blend is not None:
            if prev_event is not None:
                prev_event.synchronize()
            yield prev_blend
            avatar["current_idx"] = (avatar["current_idx"] + prev_bs) % nN

    # --------------------------------------------------------------
    # generate_video_file：离线生成测试视频 MP4
    # --------------------------------------------------------------
    def generate_video_file(self, audio_pcm: bytes, attraction_id: str, output_path: str) -> None:
        """收集 generate_frames 的所有帧，通过 ffmpeg 合成为 H.264 MP4。"""
        import wave
        import tempfile
        import subprocess

        from config import TEST_VIDEO_DIR

        TEST_VIDEO_DIR.mkdir(parents=True, exist_ok=True)

        frames = []
        for batch in self.generate_frames(audio_pcm, attraction_id):
            for frame in batch:
                frames.append(frame.permute(1, 2, 0).cpu().numpy())

        if not frames:
            raise RuntimeError(f"未生成任何帧: attraction_id={attraction_id}")

        logger.info(f"[test-video] 共收集 {len(frames)} 帧，开始 ffmpeg 合成...")

        fd, wav_path = tempfile.mkstemp(suffix=".wav")
        os.close(fd)
        try:
            with wave.open(wav_path, "wb") as wf:
                wf.setnchannels(1)
                wf.setsampwidth(2)
                wf.setframerate(16000)
                wf.writeframes(audio_pcm)

            ffmpeg_cmd = [
                "ffmpeg", "-y",
                "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{TARGET_W}x{TARGET_H}",
                "-r", "25", "-i", "pipe:0",
                "-i", wav_path,
                "-c:v", "libx264", "-preset", "fast", "-crf", "23",
                "-c:a", "aac", "-b:a", "128k",
                "-pix_fmt", "yuv420p", "-shortest",
                output_path,
            ]

            proc = subprocess.Popen(ffmpeg_cmd, stdin=subprocess.PIPE,
                                    stderr=subprocess.PIPE, stdout=subprocess.DEVNULL)
            for frame in frames:
                proc.stdin.write(frame.tobytes())
            proc.stdin.close()
            ret = proc.wait(timeout=120)
            if ret != 0:
                stderr = proc.stderr.read().decode(errors="ignore")
                raise RuntimeError(f"ffmpeg 退出码 {ret}: {stderr[-500:]}")
        finally:
            try:
                os.unlink(wav_path)
            except OSError:
                pass

        logger.info(f"[test-video] MP4 生成完成: {output_path}")
