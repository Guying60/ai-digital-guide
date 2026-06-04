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


async def _send_jpegs(
    ws: WebSocket,
    jpeg_list: List[bytes],
    frame_index: int,
    sentence_id: int,
    max_frames: int,
    shutdown_event: asyncio.Event,
) -> int:
    """
    把一批 JPEG 全量 burst 推到 WS，返回更新后的 frame_index。
    超过 max_frames 的帧会被丢弃，防止视频超出音频时长。
    视频同步由客户端 PTS 驱动渲染循环负责，服务端不再限速。

    二进制帧格式（Java MuseTalkConnector 会在最前面再加 0x02，安卓最终收到）：
      [sentence_id]   2 字节  big-endian，同一句话所有帧相同，与音频 sentence_id 对应
      [pts_ms]        4 字节  big-endian，该帧在本句中的显示时间（毫秒）
      [JPEG data]     N 字节  原始 JPEG 图像数据

    安卓最终收到（含 Java 添加的 0x02）：
      [0x02][sentence_id: 2B][pts_ms: 4B][JPEG...]
    """
    for k, jpeg in enumerate(jpeg_list):
        if shutdown_event.is_set():
            return frame_index
        if frame_index >= max_frames:
            return frame_index
        pts_ms = frame_index * _MS_PER_FRAME
        header = struct.pack(">HI", sentence_id, pts_ms)
        await ws.send_bytes(header + jpeg)
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
                                sentence_id=sentence_id,
                                max_frames=max_frames,
                                shutdown_event=shutdown_event,
                            )

                        pending_jpegs = encode_frames_gpu(blended_list, quality=JPEG_QUALITY)

                    if pending_jpegs is not None and not shutdown_event.is_set():
                        frame_index = await _send_jpegs(
                            ws, pending_jpegs, frame_index,
                            sentence_id=sentence_id,
                            max_frames=max_frames,
                            shutdown_event=shutdown_event,
                        )

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