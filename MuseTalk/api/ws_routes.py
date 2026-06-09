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
JPEG_QUALITY = 80

# 视频帧率（MuseTalk 标准输出帧率）
FPS = 25
_MS_PER_FRAME = 1000 // FPS  # 40ms/帧

# H.264 编码线程池：将 CPU 密集的 libx264 编码从 asyncio 事件循环中卸载，
# 使下一批 GPU 推理（UNet）能与当前批的 H.264 编码并行执行。
_h264_executor = ThreadPoolExecutor(max_workers=2, thread_name_prefix="h264-enc")


def _encode_batch_sync(
    encoder: H264StreamEncoder, blended_list: List
) -> List[tuple]:
    """将一批 blend 后的帧编码为 H.264 access unit（在编码线程池中运行）。

    blended_list 中的 tensor 会被同步拷贝到 CPU 并编码，调用方无需额外持有 CUDA 引用。
    """
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


def _enqueue_h264_batch(
    frame_send_queue: asyncio.Queue,
    au_list: List[tuple],
    frame_index: int,
    sentence_id: int,
    max_frames: int,
    pts_base_ms: int,
) -> int:
    """把一批编码好的 AU 放入发送队列（非阻塞），返回更新后的 frame_index。"""
    for au_bytes, is_keyframe in au_list:
        if frame_index >= max_frames:
            return frame_index
        pts_ms = pts_base_ms + frame_index * _MS_PER_FRAME
        frame_send_queue.put_nowait(
            ("frame", au_bytes, is_keyframe, pts_ms, sentence_id)
        )
        frame_index += 1
    return frame_index


async def _send_one_frame(
    ws: WebSocket,
    au_bytes: bytes,
    is_keyframe: bool,
    pts_ms: int,
    sentence_id: int,
    shutdown_event: asyncio.Event,
    send_state: dict,
) -> bool:
    """按 25fps 节拍发送单帧；返回 False 表示连接已关闭。"""
    if shutdown_event.is_set():
        return False

    frame_interval_ns = int((_MS_PER_FRAME / 1000.0) * 1_000_000_000)
    now_ns = time.monotonic_ns()
    next_ns = send_state["next_send_ns"]
    if next_ns > 0:
        wait_s = (next_ns - now_ns) / 1_000_000_000.0
        if wait_s > 0:
            await asyncio.sleep(wait_s)
    send_state["next_send_ns"] = max(
        now_ns, send_state.get("next_send_ns", now_ns)
    ) + frame_interval_ns

    header = struct.pack(">HIB", sentence_id, pts_ms, 1 if is_keyframe else 0)
    await ws.send_bytes(header + au_bytes)
    return True


async def frame_send_worker(
    ws: WebSocket,
    frame_send_queue: asyncio.Queue,
    shutdown_event: asyncio.Event,
    send_state: dict,
):
    """独立发送协程：与 GPU 推理并行，跨句维持 25fps 节拍，消除句间死区。"""
    while True:
        item = await frame_send_queue.get()
        if item is None:
            frame_send_queue.task_done()
            break

        kind = item[0]
        try:
            if shutdown_event.is_set():
                continue
            if kind == "frame":
                _, au_bytes, is_keyframe, pts_ms, sentence_id = item
                await _send_one_frame(
                    ws, au_bytes, is_keyframe, pts_ms, sentence_id,
                    shutdown_event, send_state,
                )
            elif kind == "eos":
                _, sentence_id = item
                if not shutdown_event.is_set():
                    await ws.send_json({"type": "done", "sentence_id": sentence_id})
        except Exception as e:
            logger.error(f"[ws] 发送协程异常: {e}", exc_info=True)
        finally:
            frame_send_queue.task_done()


@router.websocket("/ws/infer")
async def websocket_endpoint(ws: WebSocket):
    engine = ws.app.state.engine
    await ws.accept()
    logger.info("[ws] 客户端已连接")
    attraction_id: Optional[str] = None
    audio_buffer = bytearray()

    last_pong = {"ts": time.time()}
    shutdown_event = asyncio.Event()

    # (chunks_future_or_list, full_audio_len, attraction_id, sentence_id)
    # chunks_future_or_list: 预计算的 asyncio.Future 或已就绪的 list
    inference_queue: asyncio.Queue = asyncio.Queue()
    # 发送队列：推理协程只负责入队，frame_send_worker 独立按 25fps 推流
    frame_send_queue: asyncio.Queue = asyncio.Queue()
    # 跨句共享：单调发送时钟（25fps 节拍跨句连续）
    send_state: dict = {"next_send_ns": 0}
    # H.264 编码器跨句复用：避免每句话重新初始化（libx264 init 约 10~20ms）
    # 每句话 close() 后重建，保证首帧为带 SPS/PPS 的 IDR
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

                # 等待预计算的 audio2feat 结果（大部分情况下已在后台完成）
                t_audio_start = time.monotonic()
                precomputed_chunks = await chunks_future_or_list
                t_audio_done = time.monotonic()

                engine.reset_cancel()
                frame_index = 0
                # 根据音频实际时长计算视频帧数上界，防止 feature2chunks 向上取整导致视频长于音频
                sample_count = full_audio_len // 2  # int16 PCM
                audio_duration_ms = sample_count * 1000 // 16000
                max_frames = audio_duration_ms // _MS_PER_FRAME  # floor: 视频 ≤ 音频
                # PTS 句内局部（每句从 0 开始），客户端 flushForNewSentence 重置时钟锚点
                pts_base_ms = 0
                async with engine._lock:
                    # 延迟一拍：上一批编码 future 与当前批 GPU 推理并行
                    pending_aus_future: Optional[asyncio.Future] = None
                    try:
                        for blended_list in engine.generate_frames(b"", attr_id, precomputed_chunks=precomputed_chunks):
                            if shutdown_event.is_set():
                                logger.warning("[ws] 收到关闭信号，终止当前推理")
                                engine.cancel()
                                break

                            # 等待上一批编码完成并入发送队列（不阻塞在 25fps 节拍上）
                            if pending_aus_future is not None:
                                pending_aus = await pending_aus_future
                                frame_index = _enqueue_h264_batch(
                                    frame_send_queue, pending_aus, frame_index,
                                    sentence_id, max_frames, pts_base_ms,
                                )

                            # 提交当前批编码到线程池，与下一批 GPU 推理并行
                            pending_aus_future = asyncio.wrap_future(
                                _h264_executor.submit(_encode_batch_sync, encoder, blended_list)
                            )

                        # 等待最后一批编码完成并入队
                        if pending_aus_future is not None and not shutdown_event.is_set():
                            pending_aus = await pending_aus_future
                            frame_index = _enqueue_h264_batch(
                                frame_send_queue, pending_aus, frame_index,
                                sentence_id, max_frames, pts_base_ms,
                            )

                        # flush 编码器残留包（zerolatency 下通常为空）
                        if not shutdown_event.is_set():
                            tail_aus = encoder.close()
                            if tail_aus:
                                frame_index = _enqueue_h264_batch(
                                    frame_send_queue, tail_aus, frame_index,
                                    sentence_id, max_frames, pts_base_ms,
                                )
                    finally:
                        encoder.close()  # 幂等，确保异常路径也释放编码器
                    # 下一句重建编码器（保证首包为 IDR + SPS/PPS）
                    encoder = H264StreamEncoder(width=TARGET_W, height=TARGET_H, fps=FPS)

                if not shutdown_event.is_set():
                    await frame_send_queue.put(("eos", sentence_id))

                    # 性能日志：推理耗时 vs 音频时长（实时倍率）
                    t_infer_done = time.monotonic()
                    infer_elapsed = t_infer_done - t_audio_start
                    audio_duration_s = full_audio_len / 2 / 16000  # int16 PCM → 秒
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
    send_task = asyncio.create_task(
        frame_send_worker(ws, frame_send_queue, shutdown_event, send_state)
    )
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

                    # sentence_id 由 Java CosyVoiceConnector 转发 chunk_end 时携带
                    # Java 尚未更新时默认 0，不影响现有逻辑
                    sentence_id: int = msg.get("sentence_id", 0)

                    # 流水线重叠：立即在后台线程启动 audio2feat 预计算（纯 CPU，不占 GPU），
                    # 这样前一句 GPU 推理完成后可直接进入 UNet，省去 audio2feat 等待。
                    chunks_future = asyncio.to_thread(
                        engine.precompute_audio_chunks, full_audio
                    )
                    await inference_queue.put(
                        (chunks_future, len(full_audio), attraction_id, sentence_id)
                    )
                    logger.info(
                        f"[ws] sentence_id={sentence_id} 音频已推入推理队列，audio2feat 预计算已启动，继续监听网络..."
                    )
                    continue

                if msg.get("type") == "interrupt":
                    logger.info(
                        "[ws] 收到 interrupt，丢弃 audio_buffer + 清空推理/发送队列 + 取消当前推理"
                    )
                    audio_buffer.clear()
                    engine.cancel()
                    engine.reset_cancel()
                    send_state["next_send_ns"] = 0
                    drained = 0
                    while not inference_queue.empty():
                        try:
                            inference_queue.get_nowait()
                            inference_queue.task_done()
                            drained += 1
                        except Exception:
                            break
                    send_drained = 0
                    while not frame_send_queue.empty():
                        try:
                            frame_send_queue.get_nowait()
                            frame_send_queue.task_done()
                            send_drained += 1
                        except Exception:
                            break
                    logger.info(
                        f"[ws] interrupt 已丢弃 {drained} 个待推理任务、"
                        f"{send_drained} 个待发视频帧"
                    )
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
        await frame_send_queue.put(None)
        worker_task.cancel()
        send_task.cancel()
        logger.info("[ws] 连接清理完成")