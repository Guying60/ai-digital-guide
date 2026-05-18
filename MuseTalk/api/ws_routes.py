import asyncio
import json
import logging
import struct
import time
from typing import List, Optional

from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from utils.helper import encode_frames_gpu

logger = logging.getLogger(__name__)

router = APIRouter()

PING_INTERVAL = 20
PING_TIMEOUT = 45
JPEG_QUALITY = 80
YIELD_EVERY = 16  # 每发送 N 帧让一次 CPU


async def ping_loop(ws: WebSocket, last_pong: dict, shutdown_event: asyncio.Event):
    try:
        while True:
            await asyncio.sleep(PING_INTERVAL)
            elapsed = time.time() - last_pong["ts"]
            if elapsed > PING_TIMEOUT:
                logger.warning(f"[ws] Pong 超时 ({elapsed:.1f}s)，通知主循环关闭")
                shutdown_event.set()
                return
            await ws.send_json({"type": "ping"})
    except Exception as e:
        logger.debug(f"[ws] ping_loop 退出: {e}")


async def _send_jpegs(
    ws: WebSocket,
    jpeg_list: List[bytes],
    frame_index: int,
    is_last_batch: bool,
) -> int:
    """把一批 JPEG 推到 WS。返回更新后的 frame_index。最后一批的最后一帧打 is_last=1。"""
    last_pos = len(jpeg_list) - 1
    for k, jpeg in enumerate(jpeg_list):
        is_last = 1 if (is_last_batch and k == last_pos) else 0
        header = struct.pack(">IB", frame_index, is_last)
        await ws.send_bytes(header + jpeg)
        frame_index += 1
        if frame_index % YIELD_EVERY == 0:
            await asyncio.sleep(0)
    return frame_index


@router.websocket("/ws/infer")
async def websocket_endpoint(ws: WebSocket):
    engine = ws.app.state.engine
    await ws.accept()
    logger.info("[ws] 客户端已连接")
    attraction_id: Optional[str] = None
    audio_buffer = bytearray()

    last_pong = {"ts": time.time()}
    shutdown_event = asyncio.Event()

    inference_queue: asyncio.Queue = asyncio.Queue()

    async def inference_worker():
        while True:
            try:
                task_data = await inference_queue.get()
                if task_data is None:
                    break

                full_audio, attr_id = task_data
                logger.info(
                    f"[ws] 从队列取出任务，开始推理！音频长度: {len(full_audio)}，"
                    f"当前排队任务数: {inference_queue.qsize()}"
                )

                engine.reset_cancel()
                frame_index = 0
                async with engine._lock:
                    # 流水线：本轮 GPU 推理出帧 → encode → 下一轮发送时再 send，
                    # 让发送/编码与下一轮 UNet 重叠。
                    pending_jpegs: Optional[List[bytes]] = None
                    for blended_list in engine.generate_frames(full_audio, attr_id):
                        if shutdown_event.is_set():
                            logger.warning("[ws] 收到超时信号，终止当前推理")
                            engine.cancel()
                            break

                        # 先把上一批 JPEG 发完（与本批 GPU 后处理重叠的窗口）
                        if pending_jpegs is not None:
                            frame_index = await _send_jpegs(
                                ws, pending_jpegs, frame_index, is_last_batch=False
                            )

                        # 本批 GPU JPEG 编码（NVJPEG，失败回落 CPU）
                        pending_jpegs = encode_frames_gpu(blended_list, quality=JPEG_QUALITY)

                    # 收尾：把最后一批发出去，is_last 打在最后一帧
                    if pending_jpegs is not None and not shutdown_event.is_set():
                        frame_index = await _send_jpegs(
                            ws, pending_jpegs, frame_index, is_last_batch=True
                        )

                await ws.send_json({"type": "done"})
                logger.info(f"[ws] 单句推理完成，共发送 {frame_index} 帧")
                inference_queue.task_done()
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"推理工作线程异常: {e}", exc_info=True)

    worker_task = asyncio.create_task(inference_worker())
    ping_task = asyncio.create_task(ping_loop(ws, last_pong, shutdown_event))

    try:
        raw = await ws.receive()
        msg = json.loads(raw["text"])
        if msg.get("type") == "init":
            attraction_id = msg.get("attraction_id")
            try:
                await asyncio.to_thread(engine.load_avatar, attraction_id)
                await ws.send_json({"type": "ready"})
            except Exception as e:
                await ws.send_json({"type": "error", "message": f"预处理崩溃: {e}"})
                return

        while True:
            if shutdown_event.is_set():
                await ws.close(code=1001)
                break

            raw = await ws.receive()

            if raw.get("type") == "websocket.disconnect":
                break

            if "text" in raw:
                try:
                    msg = json.loads(raw["text"])
                except Exception:
                    continue

                last_pong["ts"] = time.time()
                if msg.get("type") == "pong":
                    continue

                if msg.get("type") == "audio_end":
                    if not audio_buffer:
                        continue
                    full_audio = bytes(audio_buffer)
                    audio_buffer.clear()

                    await inference_queue.put((full_audio, attraction_id))
                    logger.info(f"[ws] 已将一条音频推入推理队列，继续监听网络...")
                    continue

            if "bytes" in raw:
                last_pong["ts"] = time.time()
                audio_buffer.extend(raw["bytes"])

    except WebSocketDisconnect:
        logger.info("[ws] 客户端异常断开")
        engine.cancel()
    except Exception as e:
        engine.cancel()
        if not shutdown_event.is_set():
            try:
                await ws.send_json({"type": "error", "message": str(e)})
            except Exception:
                pass
    finally:
        ping_task.cancel()
        await inference_queue.put(None)
        worker_task.cancel()
