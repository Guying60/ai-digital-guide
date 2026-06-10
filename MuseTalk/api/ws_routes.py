import asyncio
import json
import logging
import struct
import time
from concurrent.futures import ThreadPoolExecutor
from typing import List, Optional

from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from utils.h264_encoder import H264StreamEncoder
from services.musetalk_engine import TARGET_W, TARGET_H

logger = logging.getLogger(__name__)

router = APIRouter()

PING_INTERVAL = 20
PING_TIMEOUT = 45

FPS = 25
_MS_PER_FRAME = 1000 // FPS  # 40ms/帧

# H.264 编码线程池：将 CPU 密集的 libx264 编码从 asyncio 事件循环中卸载，
# 使下一批 GPU 推理（UNet）能与当前批的 H.264 编码并行执行。
_h264_executor = ThreadPoolExecutor(max_workers=2, thread_name_prefix="h264-enc")


def _encode_batch_sync(
    encoder: H264StreamEncoder, blended_list: List
) -> List[tuple]:
    """将一批 blend 后的帧编码为 H.264 access unit（在编码线程池中运行）。"""
    return encoder.encode_frames_batch(blended_list)


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
    encoder = H264StreamEncoder(width=TARGET_W, height=TARGET_H, fps=FPS)

    async def inference_worker():
        nonlocal encoder
        while True:
            try:
                task_data = await inference_queue.get()
                if task_data is None:
                    break

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

                chunks_future_or_list, full_audio_len, attr_id, sentence_id = task_data
                logger.info(
                    f"[ws] 从队列取出任务，sentence_id={sentence_id}，"
                    f"音频长度: {full_audio_len}，当前排队任务数: {inference_queue.qsize()}"
                )

                t_audio_start = time.monotonic()
                precomputed_chunks = await chunks_future_or_list
                t_audio_done = time.monotonic()

                engine.reset_cancel()
                sample_count = full_audio_len // 2
                audio_duration_ms = sample_count * 1000 // 16000
                max_frames = audio_duration_ms // _MS_PER_FRAME
                frame_index = 0
                pts_ms = 0

                async with engine._lock:
                    pending_aus_future: Optional[asyncio.Future] = None
                    try:
                        for blended_list in engine.generate_frames(b"", attr_id, precomputed_chunks=precomputed_chunks):
                            if shutdown_event.is_set():
                                logger.warning("[ws] 收到关闭信号，终止当前推理")
                                engine.cancel()
                                break

                            # 等待上一批编码完成并直接发送
                            if pending_aus_future is not None:
                                pending_aus = await pending_aus_future
                                for au_bytes, is_keyframe in pending_aus:
                                    if frame_index >= max_frames:
                                        break
                                    header = struct.pack(">HIB", sentence_id, pts_ms, 1 if is_keyframe else 0)
                                    await ws.send_bytes(header + au_bytes)
                                    pts_ms += _MS_PER_FRAME
                                    frame_index += 1

                            # 提交当前批编码到线程池，与下一批 GPU 推理并行
                            pending_aus_future = asyncio.wrap_future(
                                _h264_executor.submit(_encode_batch_sync, encoder, blended_list)
                            )

                        # 等待最后一批编码完成并发送
                        if pending_aus_future is not None and not shutdown_event.is_set():
                            pending_aus = await pending_aus_future
                            for au_bytes, is_keyframe in pending_aus:
                                if frame_index >= max_frames:
                                    break
                                header = struct.pack(">HIB", sentence_id, pts_ms, 1 if is_keyframe else 0)
                                await ws.send_bytes(header + au_bytes)
                                pts_ms += _MS_PER_FRAME
                                frame_index += 1

                        # flush 编码器残留包
                        if not shutdown_event.is_set():
                            tail_aus = encoder.close()
                            for au_bytes, is_keyframe in tail_aus:
                                if frame_index >= max_frames:
                                    break
                                header = struct.pack(">HIB", sentence_id, pts_ms, 1 if is_keyframe else 0)
                                await ws.send_bytes(header + au_bytes)
                                pts_ms += _MS_PER_FRAME
                                frame_index += 1
                    finally:
                        encoder.close()
                        encoder = H264StreamEncoder(width=TARGET_W, height=TARGET_H, fps=FPS)

                if not shutdown_event.is_set():
                    await ws.send_json({"type": "done", "sentence_id": sentence_id})

                    t_infer_done = time.monotonic()
                    infer_elapsed = t_infer_done - t_audio_start
                    audio_duration_s = full_audio_len / 2 / 16000
                    rt_ratio = audio_duration_s / infer_elapsed if infer_elapsed > 0 else float("inf")
                    logger.info(
                        f"[perf] sentence_id={sentence_id} 推理完成 | "
                        f"帧数={frame_index} | 音频={audio_duration_s:.2f}s | "
                        f"推理耗时={infer_elapsed:.3f}s | "
                        f"audio2feat={t_audio_done - t_audio_start:.3f}s | "
                        f"实时倍率={rt_ratio:.2f}x | "
                        f"队列深度={inference_queue.qsize()}"
                    )

                inference_queue.task_done()

            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"推理工作线程异常: {e}", exc_info=True)
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

                    sentence_id: int = msg.get("sentence_id", 0)

                    chunks_future = asyncio.to_thread(
                        engine.precompute_audio_chunks, full_audio
                    )
                    await inference_queue.put(
                        (chunks_future, len(full_audio), attraction_id, sentence_id)
                    )
                    logger.info(
                        f"[ws] sentence_id={sentence_id} 音频已推入推理队列，audio2feat 预计算已启动"
                    )
                    continue

                if msg.get("type") == "interrupt":
                    logger.info("[ws] 收到 interrupt，丢弃 audio_buffer + 清空推理队列 + 取消当前推理")
                    audio_buffer.clear()
                    engine.cancel()
                    engine.reset_cancel()
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
        shutdown_event.set()
        ping_task.cancel()
        await inference_queue.put(None)
        worker_task.cancel()
        logger.info("[ws] 连接清理完成")
