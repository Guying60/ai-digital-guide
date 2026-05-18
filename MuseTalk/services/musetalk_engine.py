import asyncio
import glob
import logging
import os
import sys
import tempfile
import time
from pathlib import Path
from typing import Callable, List, Optional, Tuple

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
        self.unet.model.eval()
        self.vae.vae.eval()

        # 探测 sd-vae 的 scaling factor，decode 时需要用 latents / scale
        self._vae_scale: float = float(getattr(self.vae.vae.config, "scaling_factor", 0.18215))

        self.audio_processor = Audio2Feature(
            model_path=str(self.musetalk_root / "models/whisper/tiny.pt")
        )
        self.fp = FaceParsing(left_cheek_width=90, right_cheek_width=90)

        # 懒编译 + 第二条 CUDA 流（后处理）
        self._unet_compiled: Optional[Callable] = None
        try:
            self._post_stream: Optional[torch.cuda.Stream] = torch.cuda.Stream()
        except Exception as e:
            logger.warning(f"[engine] 创建 post_stream 失败，将使用默认流：{e}")
            self._post_stream = None

        self._warmed_up = False

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
                indices = list(range(N)) + list(range(N))[::-1]  # 前进 + 倒放

                self.avatar_cache[attraction_id] = {
                    "latents_gpu": latents_gpu,
                    "ori_resized_gpu": ori_resized_gpu,
                    "masks_gpu": masks_gpu,
                    "box_pairs": box_pairs,          # list[(face_box, crop_box)] 目标坐标系
                    "indices": indices,
                    "current_idx": 0,
                    "N": N,
                }
                vram_gb = (
                    latents_gpu.numel() * latents_gpu.element_size()
                    + ori_resized_gpu.numel() * ori_resized_gpu.element_size()
                    + sum(m.numel() * m.element_size() for m in masks_gpu)
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
        imgs = self.vae.vae.decode(latents / self._vae_scale).sample  # fp16 in [-1,1]
        imgs = (imgs.clamp(-1.0, 1.0) + 1.0) * 0.5
        return imgs

    # --------------------------------------------------------------
    # 单帧 GPU 混合：F.interpolate + α blend
    # --------------------------------------------------------------
    @torch.inference_mode()
    def _blend_one_gpu(
        self,
        recon_face: torch.Tensor,         # (3, 256, 256) fp16 [0,1] RGB
        ori_full_u8: torch.Tensor,        # (3, Ht, Wt) uint8 RGB
        mask_fp16: torch.Tensor,          # (1, ch, cw) fp16 [0,1]，对应 crop_box 大小
        box_pair: Tuple[Tuple[int, int, int, int], Tuple[int, int, int, int]],
    ) -> torch.Tensor:
        """复刻 MuseTalk 原版 get_image_blending：
        recon → resize 到 face_box → 硬贴入 ori 的 face_box 区域 →
        在 crop_box 区域用 mask 做 α 混合（face_box ⊆ crop_box）。
        """
        (fx, fy, fx1, fy1), (cxs, cys, cxe, cye) = box_pair
        fbh, fbw = fy1 - fy, fx1 - fx
        cch, ccw = cye - cys, cxe - cxs

        out = ori_full_u8.clone()  # (3, Ht, Wt) uint8

        # 1) recon resize 到 face_box，硬贴入 out（uint8）
        face = F.interpolate(
            recon_face.unsqueeze(0), size=(fbh, fbw),
            mode="bilinear", align_corners=False, antialias=False,
        ).squeeze(0)  # (3, fbh, fbw) fp16 [0,1]
        out[:, fy:fy1, fx:fx1] = (face.clamp(0.0, 1.0) * 255.0 + 0.5).to(torch.uint8)

        # 2) 在 crop_box 区域，用 mask 把硬贴帧与原图做 α 混合
        if mask_fp16.shape[-2:] != (cch, ccw):
            mask = F.interpolate(
                mask_fp16.unsqueeze(0), size=(cch, ccw),
                mode="bilinear", align_corners=False, antialias=False,
            ).squeeze(0)
        else:
            mask = mask_fp16

        body_crop = out[:, cys:cye, cxs:cxe].to(torch.float16) / 255.0
        ori_crop = ori_full_u8[:, cys:cye, cxs:cxe].to(torch.float16) / 255.0
        blended = ori_crop * (1.0 - mask) + body_crop * mask
        out[:, cys:cye, cxs:cxe] = (blended.clamp(0.0, 1.0) * 255.0 + 0.5).to(torch.uint8)
        return out

    # --------------------------------------------------------------
    # generate_frames：音频 → 帧（list[Tensor cuda uint8]）
    # --------------------------------------------------------------
    def generate_frames(self, audio_chunk: bytes, attraction_id: str):
        avatar = self.avatar_cache.get(attraction_id)
        if not avatar:
            return

        # 1) PCM int16 mono 16k → fp32 ndarray，无落盘
        try:
            audio_np = np.frombuffer(audio_chunk, dtype=np.int16).astype(np.float32) / 32768.0
            whisper_feature = self.audio_processor.audio2feat(audio_np)
        except Exception as e:
            # 极端情况下 vendored whisper 不接 ndarray，回落到 wav 落盘
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

        whisper_chunks = self.audio_processor.feature2chunks(whisper_feature, fps=25)

        latents_gpu: torch.Tensor = avatar["latents_gpu"]
        ori_gpu: torch.Tensor = avatar["ori_resized_gpu"]
        masks_gpu: List[torch.Tensor] = avatar["masks_gpu"]
        box_pairs = avatar["box_pairs"]
        indices: List[int] = avatar["indices"]
        idx0: int = avatar["current_idx"]
        nN: int = len(indices)
        unet_fn = self._get_or_compile_unet()

        n = len(whisper_chunks)
        for i in range(0, n, BATCH):
            if self._cancel_flag:
                return

            batch_chunks = whisper_chunks[i:i + BATCH]
            bs = len(batch_chunks)

            # pad-to-BATCH，保持 torch.compile 单一 shape
            if bs < BATCH:
                pad_count = BATCH - bs
                batch_chunks = list(batch_chunks) + [batch_chunks[-1]] * pad_count

            # 取 latents（GPU index_select）
            sel = torch.tensor(
                [indices[(idx0 + i + j) % nN] for j in range(BATCH)],
                device=self.device, dtype=torch.long,
            )
            latent_batch = latents_gpu.index_select(0, sel).contiguous()  # (B,4,32,32)
            audio_batch = torch.from_numpy(np.stack(batch_chunks)).to(
                self.device, dtype=torch.float16, non_blocking=True
            )

            with torch.inference_mode():
                pred_latents = unet_fn(
                    latent_batch, 0, encoder_hidden_states=audio_batch
                ).sample
                # CUDAGraph: 输出在下一次 replay 会被覆盖，立刻 clone 再走 VAE
                if self._unet_compiled is not self.unet.model:
                    pred_latents = pred_latents.clone()
                recon = self._vae_decode_tensor(pred_latents)  # (B,3,256,256) fp16 RGB

            # 后处理放到第二条 stream，与下一批 UNet 在默认流并行
            blended_list: List[torch.Tensor] = []
            if self._post_stream is not None:
                with torch.cuda.stream(self._post_stream):
                    self._post_stream.wait_stream(torch.cuda.default_stream())
                    for j in range(bs):
                        k = indices[(idx0 + i + j) % nN]
                        blended_list.append(self._blend_one_gpu(
                            recon[j], ori_gpu[k], masks_gpu[k], box_pairs[k]
                        ))
                torch.cuda.current_stream().wait_stream(self._post_stream)
            else:
                for j in range(bs):
                    k = indices[(idx0 + i + j) % nN]
                    blended_list.append(self._blend_one_gpu(
                        recon[j], ori_gpu[k], masks_gpu[k], box_pairs[k]
                    ))

            yield blended_list  # list[(3,Ht,Wt) uint8 cuda]

            avatar["current_idx"] = (avatar["current_idx"] + bs) % nN
