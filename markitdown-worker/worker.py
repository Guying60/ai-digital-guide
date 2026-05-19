import asyncio
import struct
import json
import logging
import os
import sys
import tempfile
import wave
import cv2
import time
import glob
from pathlib import Path
from typing import Optional

import numpy as np
import torch
from fastapi import FastAPI, WebSocket, WebSocketDisconnect

# ---------------------------------------------------------------------------
# Project Setup & MuseTalk Imports
# ---------------------------------------------------------------------------
PROJECT_ROOT = Path("/root/autodl-tmp/DigitalHuman")
MUSETALK_ROOT = Path("/root/autodl-tmp/MuseTalk")

os.chdir(MUSETALK_ROOT)
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))
if str(MUSETALK_ROOT) not in sys.path:
    sys.path.insert(0, str(MUSETALK_ROOT))

from musetalk.utils.utils import load_all_model
from musetalk.whisper.audio2feature import Audio2Feature
from musetalk.utils.preprocessing import get_landmark_and_bbox
from musetalk.utils.face_parsing import FaceParsing
from musetalk.utils.blending import get_image_prepare_material, get_image_blending
from scripts.realtime_inference import video2imgs

# ---------------------------------------------------------------------------
# Logging & Utils
# ---------------------------------------------------------------------------
logging.basicConfig(level=logging.INFO, format="%(asctime)s | %(levelname)s | %(message)s")
logger = logging.getLogger(__name__)


def encode_frame(frame_np: np.ndarray, quality: int = 80) -> bytes:
    encode_param = [int(cv2.IMWRITE_JPEG_QUALITY), quality]
    _, buffer = cv2.imencode('.jpg', frame_np, encode_param)
    return buffer.tobytes()


def _peek_ahead(iterable):
    it = iter(iterable)
    try:
        first = next(it)
    except StopIteration:
        return
    for second in it:
        yield first, False
        first = second
    yield first, True


# ---------------------------------------------------------------------------
# MuseTalk Engine
# ---------------------------------------------------------------------------
class MuseTalkEngine:
    def __init__(self, musetalk_root: Path, base_video_dir: str = "/root/autodl-tmp/videos", device: str = "cuda"):
        self.device = device
        self.musetalk_root = musetalk_root
        self.base_video_dir = Path(base_video_dir)
        self._lock = asyncio.Lock()
        self._cancel_flag = False

        self.avatar_cache = {}

        logger.info("正在加载 MuseTalk 模型，请稍候...")
        self.vae, self.unet, self.pe = load_all_model(
            unet_model_path=str(self.musetalk_root / "models/musetalk/pytorch_model.bin"),
            vae_type="sd-vae",
            unet_config=str(self.musetalk_root / "models/musetalk/musetalk.json"),
            device=self.device
        )
        self.vae.vae = self.vae.vae.half().to(self.device)
        self.unet.model = self.unet.model.half().to(self.device)

        self.audio_processor = Audio2Feature(
            model_path=str(self.musetalk_root / "models/whisper/tiny.pt")
        )
        self.fp = FaceParsing(left_cheek_width=90, right_cheek_width=90)
        logger.info("MuseTalk 模型加载完毕！")

    def get_portrait_path(self, attraction_id: str) -> str:
        video_path = self.base_video_dir / f"{attraction_id}.mp4"
        if not video_path.exists():
            raise FileNotFoundError(f"未找到景点视频: {video_path}")
        return str(video_path)

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

                latents_list = []
                mask_coords_list = []
                mask_list = []
                valid_frame_list = []  # ✅ 只保留 bbox 有效的帧
                valid_coord_list = []  # ✅ 只保留 bbox 有效的坐标
                coord_placeholder = (0.0, 0.0, 0.0, 0.0)

                for idx, (bbox, frame) in enumerate(zip(coord_list, frame_list)):
                    if bbox == coord_placeholder:
                        continue

                    x1, y1, x2, y2 = bbox
                    # ✅ 统一取整，消除 round vs int 的 1px 差异
                    x1 = int(round(x1))
                    y1 = int(round(y1))
                    x2 = int(round(x2))
                    y2 = int(round(y2))
                    y2 = min(y2 + 10, frame.shape[0])
                    # 再 clamp 一下 x 防越界
                    x1 = max(0, x1)
                    y1 = max(0, y1)
                    x2 = min(frame.shape[1], x2)

                    valid_bbox = [x1, y1, x2, y2]  # 全 int，和 get_image_prepare_material 一致

                    crop_frame = frame[y1:y2, x1:x2]
                    resized_crop_frame = cv2.resize(crop_frame, (256, 256), interpolation=cv2.INTER_LANCZOS4)
                    latent = self.vae.get_latents_for_unet(resized_crop_frame)

                    mask, crop_box = get_image_prepare_material(frame, valid_bbox, fp=self.fp)
                    crop_box = [int(round(v)) for v in crop_box]

                    valid_frame_list.append(frame)
                    valid_coord_list.append(valid_bbox)
                    latents_list.append(latent)
                    mask_list.append(mask)
                    mask_coords_list.append(crop_box)

                self.avatar_cache[attraction_id] = {
                    "frames": valid_frame_list + valid_frame_list[::-1],  # ✅ 用 valid
                    "coords": valid_coord_list + valid_coord_list[::-1],  # ✅ 用 valid
                    "latents": latents_list + latents_list[::-1],
                    "masks": mask_list + mask_list[::-1],
                    "mask_coords": mask_coords_list + mask_coords_list[::-1],
                    "current_idx": 0
                }
                logger.info(
                    f"✅ 景点 {attraction_id} 预处理完成！"
                    f"有效帧: {len(valid_frame_list)} / 总帧: {len(frame_list)}，"
                    f"常驻内存帧数: {len(self.avatar_cache[attraction_id]['frames'])}"
                )
        except Exception as e:
            logger.error(f"WS error: {e}", exc_info=True)
            logger.error(f"预处理发生异常: {e}", exc_info=True)
            raise e

    def reset_cancel(self):
        self._cancel_flag = False

    def cancel(self):
        self._cancel_flag = True

    def generate_frames(self, audio_chunk: bytes, attraction_id: str):
        avatar = self.avatar_cache.get(attraction_id)
        if not avatar:
            return

        # ⚠️ 必须要有这一行来创建临时文件！它不仅定义了 temp_audio，还能保证用完后自动删除
        with tempfile.NamedTemporaryFile(suffix=".wav", delete=True) as temp_audio:

            # 👇 注意：下面这些都要往右缩进一层，包在上面的 with 块里面
            with wave.open(temp_audio.name, 'wb') as wav_file:
                wav_file.setnchannels(1)  # 1: 单声道 (Mono)
                wav_file.setsampwidth(2)  # 2: 16位 (16-bit = 2 bytes)
                wav_file.setframerate(16000)  # 16000: 阿里云 NLS 的标准采样率
                wav_file.writeframes(audio_chunk)  # audio_chunk 就是阿里云传来的 PCM 字节

            # 此时 temp_audio 已经是一个包含标准头的 WAV 文件了，交由 Whisper 处理
            whisper_feature = self.audio_processor.audio2feat(temp_audio.name)
            whisper_chunks = self.audio_processor.feature2chunks(whisper_feature, fps=25)
            # 👆 处理完毕，退出这个 with 块后，临时文件会被系统自动清理

        num_frames = len(avatar["latents"])
        batch_size = 8

        for i in range(0, len(whisper_chunks), batch_size):
            if self._cancel_flag:
                break

            batch_audio = whisper_chunks[i: i + batch_size]
            bs = len(batch_audio)

            latent_batch = torch.cat(
                [avatar["latents"][(avatar["current_idx"] + j) % num_frames] for j in range(bs)], dim=0
            ).to(self.device, dtype=torch.float16)

            audio_batch = torch.from_numpy(
                np.stack(batch_audio)
            ).to(self.device, dtype=torch.float16)

            with torch.no_grad():
                pred_latents = self.unet.model(
                    latent_batch, 0, encoder_hidden_states=audio_batch
                ).sample

            recon_imgs = self.vae.decode_latents(pred_latents)

            for idx, recon_img in enumerate(recon_imgs):
                loop_idx = (avatar["current_idx"] + idx) % num_frames

                ori_frame = avatar["frames"][loop_idx]
                mask = avatar["masks"][loop_idx]
                bbox = avatar["coords"][loop_idx]
                mask_crop_box = avatar["mask_coords"][loop_idx]

                # ✅ 核心修复：decode_latents 输出 256×256，但 blending 期望人脸真实尺寸
                x, y, x1, y1 = bbox  # 已经是 int（load_avatar 里取整过了）
                face_w = x1 - x
                face_h = y1 - y
                recon_img_resized = cv2.resize(recon_img, (face_w, face_h),
                                               interpolation=cv2.INTER_LINEAR)

                combine_frame = get_image_blending(ori_frame, recon_img_resized, bbox, mask, mask_crop_box)
                yield combine_frame

            avatar["current_idx"] = (avatar["current_idx"] + bs) % num_frames


# ---------------------------------------------------------------------------
# FastAPI App
# ---------------------------------------------------------------------------
app = FastAPI()

engine = MuseTalkEngine(musetalk_root=MUSETALK_ROOT, base_video_dir="/root/autodl-tmp/videos")


@app.websocket("/ws/infer")
async def websocket_endpoint(ws: WebSocket):
    await ws.accept()
    logger.info("[ws] 客户端已连接")
    attraction_id: Optional[str] = None

    last_pong = {"ts": time.time()}          # ← 用 dict 让 ping_loop 可以修改
    ping_task = asyncio.create_task(ping_loop(ws, last_pong))   # ← 启动心跳

    try:
        raw = await ws.receive()
        msg = json.loads(raw["text"])
        if msg.get("type") == "init":
            attraction_id = msg.get("attraction_id")
            try:
                await asyncio.to_thread(engine.load_avatar, attraction_id)
                await ws.send_json({"type": "ready"})
            except Exception as e:
                logger.error(f"WS error: {e}", exc_info=True)
                await ws.send_json({"type": "error", "message": f"预处理崩溃: {e}"})
                return

        while True:
            raw = await ws.receive()

            if raw.get("type") == "websocket.disconnect":
                logger.info("[ws] 客户端正常退出")
                break

            # ↓ 处理 pong 回包，更新时间戳
            if "text" in raw:
                try:
                    msg = json.loads(raw["text"])
                    if msg.get("type") == "pong":
                        last_pong["ts"] = time.time()
                        logger.debug("[ws] ← pong")
                        continue
                except Exception:
                    pass

            if "bytes" not in raw:
                continue

            audio_chunk = raw["bytes"]
            engine.reset_cancel()
            frame_index = 0

            async with engine._lock:
                frame_gen = engine.generate_frames(audio_chunk, attraction_id)
                for frame_np, is_last in _peek_ahead(iter(frame_gen)):
                    jpeg_bytes = encode_frame(frame_np)
                    header = struct.pack(">IB", frame_index, 1 if is_last else 0)
                    await ws.send_bytes(header + jpeg_bytes)
                    frame_index += 1

            await ws.send_json({"type": "done"})

    except WebSocketDisconnect:
        logger.info("[ws] 客户端异常断开")
        engine.cancel()
    except Exception as e:
        logger.error(f"WS error: {e}", exc_info=True)
        engine.cancel()
        try:
            await ws.send_json({"type": "error", "message": str(e)})
        except:
            pass
    finally:
        ping_task.cancel()          # ← 连接结束时无论如何都取消心跳任务
        logger.info("[ws] ping_loop 已取消")

# ---------------------------------------------------------------------------
# Ping/Pong 配置
# ---------------------------------------------------------------------------
PING_INTERVAL = 20   # 每 20 秒发一次 ping
PING_TIMEOUT  = 45   # 45 秒内没收到 pong → 主动关闭连接

# ---------------------------------------------------------------------------
# Ping Loop（作为独立协程与主循环并发运行）
# ---------------------------------------------------------------------------
async def ping_loop(ws: WebSocket, last_pong: dict):
    """
    每隔 PING_INTERVAL 秒向客户端发送 {"type":"ping"}，
    并检查距离上次收到 pong 的时间，超过 PING_TIMEOUT 则关闭连接。
    """
    import time
    try:
        while True:
            await asyncio.sleep(PING_INTERVAL)
            elapsed = time.time() - last_pong["ts"]
            if elapsed > PING_TIMEOUT:
                logger.warning(f"[ws] Pong 超时 ({elapsed:.1f}s)，主动关闭连接")
                await ws.close(code=1001)
                return
            await ws.send_json({"type": "ping"})
            logger.debug("[ws] → ping")
    except Exception as e:
        logger.debug(f"[ws] ping_loop 退出: {e}")


if __name__ == "__main__":
    import uvicorn

    # 将 ws_ping_interval 和 ws_ping_timeout 设为 None，彻底关闭 Uvicorn 的心跳超时检测
    uvicorn.run(
        app,
        host="0.0.0.0",
        port=6006,
        ws_ping_interval=None,
        ws_ping_timeout=None
    )