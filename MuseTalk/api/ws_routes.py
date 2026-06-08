import asyncio
import json
import logging
import struct
import time
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


async def _send_h264(
    ws: WebSocket,
    au_list: List[tuple],
    frame_index: int,
    sentence_id: int,
    max_frames: int,
    shutdown_event: asyncio.Event,
    send_state: dict,
) -> int:
    """
    把一批 H.264 access unit 按 25fps 实时节拍推到 WS，返回更新后的 frame_index。
    au_list: [(annexb_bytes, is_keyframe), ...]
    超过 max_frames 的帧会被丢弃，防止视频超出音频时长。

    整流策略：用单调时钟控制每帧发送时刻（40ms/帧），削平句内突发灌入。
    send_state: {"next_send_ns": float}，跨批次维持发送时钟，首次调用时传 {"next_send_ns": 0}。

    二进制帧格式（Java MuseTalkConnector 会在最前面再加 0x03，安卓最终收到）：
      [sentence_id]   2 字节  big-endian，同一句话所有帧相同，与音频 sentence_id 对应
      [pts_ms]        4 字节  big-endian，该帧在本句中的显示时间（毫秒）
      [is_keyframe]   1 字节  1=IDR(含 in-band SPS/PPS)，0=非关键帧
      [H.264 AU]      N 字节  H.264 Annex-B access unit

    安卓最终收到（含 Java 添加的 0x03）：
      [0x03][sentence_id: 2B][pts_ms: 4B][is_keyframe: 1B][H.264 AU...]
    """
    frame_interval = _MS_PER_FRAME / 1000.0  # 0.04s
    now_ns = time.monotonic_ns()

    for au_bytes, is_keyframe in au_list:
        if shutdown_event.is_set():
            return frame_index
        if frame_index >= max_frames:
            return frame_index

        # 整流：等到下一帧发送时刻再发
        next_ns = send_state["next_send_ns"]
        if next_ns > 0:
            wait_s = (next_ns - now_ns) / 1_000_000_000.0
            if wait_s > 0:
                await asyncio.sleep(wait_s)
        # 更新下一帧时刻（单调递增，不追落后的 wall clock）
        send_state["next_send_ns"] = max(
            now_ns, send_state.get("next_send_ns", now_ns)
        ) + int(frame_interval * 1_000_000_000)
        now_ns = time.monotonic_ns()

        pts_ms = frame_index * _MS_PER_FRAME
        header = struct.pack(">HIB", sentence_id, pts_ms, 1 if is_keyframe else 0)
        await ws.send_bytes(header + au_bytes)
        frame_index += 1
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

    # (full_audio, attraction_id, sentence_id)
    inference_queue: asyncio.Queue = asyncio.Queue()

    async def inference_worker():
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

                full_audio, attr_id, sentence_id = task_data
                logger.info(
                    f"[ws] 从队列取出任务，sentence_id={sentence_id}，"
                    f"音频长度: {len(full_audio)}，当前排队任务数: {inference_queue.qsize()}"
                )

                engine.reset_cancel()
                frame_index = 0
                # 根据音频实际时长计算视频帧数上界，防止 feature2chunks 向上取整导致视频长于音频
                sample_count = len(full_audio) // 2  # int16 PCM
                audio_duration_ms = sample_count * 1000 // 16000
                max_frames = audio_duration_ms // _MS_PER_FRAME  # floor: 视频 ≤ 音频
                # 发送时钟：跨批次维持，首次调用时从当前时刻开始
                send_state: dict = {"next_send_ns": 0}
                async with engine._lock:
                    # 每句话一个独立 H.264 编码器：首包必为带 in-band SPS/PPS 的 IDR
                    encoder = H264StreamEncoder(width=TARGET_W, height=TARGET_H, fps=FPS)
                    pending_aus: Optional[List[tuple]] = None  # 延迟一拍发送，重叠编码与网络 I/O
                    try:
                        for blended_list in engine.generate_frames(full_audio, attr_id):
                            if shutdown_event.is_set():
                                logger.warning("[ws] 收到关闭信号，终止当前推理")
                                engine.cancel()
                                break

                            if pending_aus is not None:
                                frame_index = await _send_h264(
                                    ws, pending_aus, frame_index,
                                    sentence_id=sentence_id,
                                    max_frames=max_frames,
                                    shutdown_event=shutdown_event,
                                    send_state=send_state,
                                )

                            # 逐帧编码当前批次为 H.264 access unit（编码器有状态，须按序）
                            batch_aus: List[tuple] = []
                            for tensor in blended_list:
                                batch_aus.extend(encoder.encode_frame(tensor))
                            pending_aus = batch_aus

                        if pending_aus is not None and not shutdown_event.is_set():
                            frame_index = await _send_h264(
                                ws, pending_aus, frame_index,
                                sentence_id=sentence_id,
                                max_frames=max_frames,
                                shutdown_event=shutdown_event,
                                send_state=send_state,
                            )

                        # flush 编码器残留包（zerolatency 下通常为空）
                        if not shutdown_event.is_set():
                            tail_aus = encoder.close()
                            if tail_aus:
                                frame_index = await _send_h264(
                                    ws, tail_aus, frame_index,
                                    sentence_id=sentence_id,
                                    max_frames=max_frames,
                                    shutdown_event=shutdown_event,
                                    send_state=send_state,
                                )
                    finally:
                        encoder.close()  # 幂等，确保异常路径也释放编码器

                if not shutdown_event.is_set():
                    # sentence_id 透传，安卓用于确认哪句视频推完
                    await ws.send_json({"type": "done", "sentence_id": sentence_id})
                    logger.info(
                        f"[ws] sentence_id={sentence_id} 推理完成，共发送 {frame_index} 帧"
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

                    # sentence_id 由 Java CosyVoiceConnector 转发 chunk_end 时携带
                    # Java 尚未更新时默认 0，不影响现有逻辑
                    sentence_id: int = msg.get("sentence_id", 0)
                    await inference_queue.put((full_audio, attraction_id, sentence_id))
                    logger.info(
                        f"[ws] sentence_id={sentence_id} 音频已推入推理队列，继续监听网络..."
                    )
                    continue

                if msg.get("type") == "interrupt":
                    logger.info(
                        "[ws] 收到 interrupt，丢弃 audio_buffer + 清空推理队列 + 取消当前推理"
                    )
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