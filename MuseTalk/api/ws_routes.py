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
    shutdown_event: asyncio.Event,
) -> int:
    """把一批 JPEG 推到 WS。返回更新后的 frame_index。最后一批的最后一帧打 is_last=1。"""
    last_pos = len(jpeg_list) - 1
    for k, jpeg in enumerate(jpeg_list):
        # 发每帧前先检查连接是否已关闭
        if shutdown_event.is_set():
            return frame_index
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

                # 取出任务后先检查连接是否已断开，是则清空队列退出
                if shutdown_event.is_set():
                    logger.info("[ws] 连接已断开，丢弃剩余推理任务")
                    inference_queue.task_done()
                    while not inference_queue.empty():
                        try:
                            inference_queue.get_nowait()
                            inference_queue.task_done()
                        except Exception:
                            pass
                    break

                full_audio, attr_id = task_data
                logger.info(
                    f"[ws] 从队列取出任务，开始推理！音频长度: {len(full_audio)}，"
                    f"当前排队任务数: {inference_queue.qsize()}"
                )
                TAIL_SILENCE_MS = 160
                tail_silence = b'\x00\x00' * int(16000 * TAIL_SILENCE_MS / 1000)
                full_audio = full_audio + tail_silence

                engine.reset_cancel()
                frame_index = 0
                async with engine._lock:
                    pending_jpegs: Optional[List[bytes]] = None
                    for blended_list in engine.generate_frames(full_audio, attr_id):
                        if shutdown_event.is_set():
                            logger.warning("[ws] 收到关闭信号，终止当前推理")
                            engine.cancel()
                            break

                        if pending_jpegs is not None:
                            frame_index = await _send_jpegs(
                                ws, pending_jpegs, frame_index,
                                is_last_batch=False,
                                shutdown_event=shutdown_event,
                            )

                        pending_jpegs = encode_frames_gpu(blended_list, quality=JPEG_QUALITY)

                    if pending_jpegs is not None and not shutdown_event.is_set():
                        frame_index = await _send_jpegs(
                            ws, pending_jpegs, frame_index,
                            is_last_batch=True,
                            shutdown_event=shutdown_event,
                        )

                if not shutdown_event.is_set():
                    await ws.send_json({"type": "done"})
                    logger.info(f"[ws] 单句推理完成，共发送 {frame_index} 帧")

                inference_queue.task_done()

            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"推理工作线程异常: {e}", exc_info=True)
                # 异常后若连接已断开则退出 worker
                if shutdown_event.is_set():
                    break

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
                logger.info("[ws] 客户端正常退出")
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

                if msg.get("type") == "interrupt":
                    logger.info(
                        "[ws] 收到 interrupt，丢弃 audio_buffer + 清空推理队列 + 取消当前推理"
                    )
                    # 1) 清空尚未 flush 进队列的 PCM 缓冲
                    audio_buffer.clear()
                    # 2) 标记 engine 取消当前正在生成的批次（worker 会在下一次循环退出）
                    engine.cancel()
                    # 3) 抽干队列中尚未开始推理的任务
                    drained = 0
                    while not inference_queue.empty():
                        try:
                            inference_queue.get_nowait()
                            inference_queue.task_done()
                            drained += 1
                        except Exception:
                            break
                    logger.info(f"[ws] interrupt 已丢弃 {drained} 个待推理任务")
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
        shutdown_event.set()  # 确保 worker 能感知到连接已断开
        ping_task.cancel()
        await inference_queue.put(None)
        worker_task.cancel()
        logger.info("[ws] 连接清理完成")