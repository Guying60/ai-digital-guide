import asyncio
import io
import json
import logging
import struct
import uuid
import wave
from typing import Optional

from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from fastapi.responses import Response

from services.cosyvoice_engine import get_engine

logger = logging.getLogger(__name__)

router = APIRouter()

# 音频格式常量：16kHz 单声道 s16le
# pts_ms = cumulative_pcm_bytes // (16000 * 1 * 2 / 1000) = cumulative_pcm_bytes // 32
_PCM_BYTES_PER_MS = 32


@router.get("/health")
async def health() -> dict:
    engine = get_engine()
    return {
        "status": "ok" if engine._loaded else "loading",
        "voices": list(engine.list_voices().keys()),
    }


@router.get("/voices")
async def list_voices() -> dict:
    return {"voices": list(get_engine().list_voices().keys())}


@router.post("/voices/reload")
async def reload_voices() -> dict:
    report = get_engine().reload_voices()
    return {"reloaded": report}


@router.post("/tts/offline")
async def tts_offline(request: dict) -> Response:
    """离线 TTS：返回完整 WAV 文件，供测试视频生成等离线场景使用。"""
    text = (request.get("text") or "").strip()
    attraction_id = str(request.get("attraction_id")) if request.get("attraction_id") else None
    if not text:
        return Response(content=b"", media_type="audio/wav", status_code=400)

    engine = get_engine()
    buf = io.BytesIO()
    with wave.open(buf, "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(16000)
        async for pcm in engine.stream_pcm(text, attraction_id):
            wf.writeframes(pcm)

    return Response(content=buf.getvalue(), media_type="audio/wav")


@router.websocket("/ws/tts")
async def ws_tts(ws: WebSocket) -> None:
    await ws.accept()
    sid = str(uuid.uuid4())[:8]
    logger.info("[tts] ws connected sid=%s", sid)
    engine = get_engine()

    # 单连接内的会话上下文
    attraction_id: Optional[str] = None
    # job_queue: (text, attraction_id, session_id, sentence_id)
    job_queue: asyncio.Queue = asyncio.Queue()
    interrupt_flag = asyncio.Event()                   # 当前句中断信号
    current_task: Optional[asyncio.Task] = None        # 当前 worker 正在跑的句子任务
    shutdown = asyncio.Event()
    # 每个 synthesize 请求递增，用于音视频对齐（0-65535 循环）
    sentence_counter = 0

    async def synthesize_one(
        text: str,
        attr: Optional[str],
        session_id: Optional[str],
        sentence_id: int,
    ) -> None:
        """
        流式推理并发送带时间戳 header 的二进制帧给 Java 后端（最终到安卓）。

        二进制帧格式：
          [0x01]           1 字节  类型标识（音频）
          [sentence_id]    2 字节  big-endian，同一句话所有 chunk 相同
          [pts_ms]         4 字节  big-endian，该 chunk 在本句中的起始毫秒偏移
          [PCM data]       N 字节  16kHz 单声道 s16le 裸 PCM
        """
        cumulative_bytes = 0
        try:
            async for pcm in engine.stream_pcm(text, attr):
                if interrupt_flag.is_set() or shutdown.is_set():
                    logger.info("[tts] sid=%s sentence_id=%d 中断，停止发送 PCM", sid, sentence_id)
                    break

                pts_ms = cumulative_bytes // _PCM_BYTES_PER_MS
                # struct.pack: B=1字节, H=2字节 big-endian, I=4字节 big-endian
                header = struct.pack(">BHI", 0x01, sentence_id, pts_ms)
                await ws.send_bytes(header + pcm)
                cumulative_bytes += len(pcm)

            if not interrupt_flag.is_set() and not shutdown.is_set():
                await _send_chunk_end(ws, session_id, sentence_id)
        except asyncio.CancelledError:
            raise
        except Exception as exc:
            logger.exception("[tts] synth failed sid=%s sentence_id=%d", sid, sentence_id)
            try:
                await _send_error(ws, str(exc), session_id)
            except Exception:
                pass

    async def worker() -> None:
        nonlocal current_task
        while not shutdown.is_set():
            try:
                text, attr, session_id, sentence_id = await job_queue.get()
            except asyncio.CancelledError:
                break
            interrupt_flag.clear()
            current_task = asyncio.create_task(
                synthesize_one(text, attr, session_id, sentence_id)
            )
            try:
                await current_task
            except asyncio.CancelledError:
                pass
            finally:
                current_task = None
                job_queue.task_done()

    worker_task = asyncio.create_task(worker())

    try:
        while True:
            raw = await ws.receive_text()
            try:
                msg = json.loads(raw)
            except json.JSONDecodeError:
                await _send_error(ws, "invalid json")
                continue

            mtype = msg.get("type")

            if mtype == "init":
                attraction_id = (
                    str(msg["attraction_id"])
                    if msg.get("attraction_id") is not None
                    else None
                )
                logger.info("[tts] sid=%s init attraction=%s", sid, attraction_id)
                continue

            if mtype == "ping":
                await ws.send_text(json.dumps({"type": "pong"}))
                continue

            if mtype == "pong":
                # Java 端目前不主动 ping，但保留以备将来
                continue

            if mtype == "interrupt":
                logger.info(
                    "[tts] sid=%s 收到 interrupt，丢弃队列 + 取消当前句", sid
                )
                # 1) 抽干 job_queue
                drained = 0
                while not job_queue.empty():
                    try:
                        job_queue.get_nowait()
                        job_queue.task_done()
                        drained += 1
                    except Exception:
                        break
                # 2) 中断当前正在跑的句子
                interrupt_flag.set()
                if current_task is not None and not current_task.done():
                    current_task.cancel()
                logger.info(
                    "[tts] sid=%s interrupt 丢弃 %d 个待推理任务", sid, drained
                )
                continue

            if mtype == "synthesize":
                text: str = msg.get("text") or ""
                attr = (
                    str(msg["attraction_id"])
                    if msg.get("attraction_id") is not None
                    else attraction_id
                )
                session_id: Optional[str] = msg.get("session_id")
                if not text.strip():
                    await _send_chunk_end(ws, session_id, sentence_counter)
                    continue
                # 递增句子计数器（2字节上限循环）
                sentence_counter = (sentence_counter + 1) & 0xFFFF
                logger.info(
                    "[tts] sid=%s session=%s sentence_id=%d attraction=%s text=%r",
                    sid, session_id, sentence_counter, attr, text[:60],
                )
                await job_queue.put((text, attr, session_id, sentence_counter))
                continue

            await _send_error(ws, f"unsupported type: {mtype}")
    except WebSocketDisconnect:
        logger.info("[tts] ws disconnected sid=%s", sid)
    except Exception:
        logger.exception("[tts] ws error sid=%s", sid)
    finally:
        shutdown.set()
        interrupt_flag.set()
        if current_task is not None:
            current_task.cancel()
        worker_task.cancel()
        try:
            await worker_task
        except (asyncio.CancelledError, Exception):
            pass
        try:
            await ws.close()
        except Exception:
            pass


async def _send_chunk_end(
    ws: WebSocket, session_id: Optional[str], sentence_id: int = 0
) -> None:
    """
    通知 Java 后端一整句 PCM 推送完毕。
    sentence_id 透传给 Java，Java 需将其写入 audio_end 消息转发给 MuseTalk，
    以便 MuseTalk 为视频帧打上相同的 sentence_id，安卓才能做音视频对齐。
    """
    payload = {"type": "chunk_end", "sentence_id": sentence_id}
    if session_id:
        payload["session_id"] = session_id
    await ws.send_text(json.dumps(payload))


async def _send_error(
    ws: WebSocket, message: str, session_id: Optional[str] = None
) -> None:
    payload = {"type": "error", "message": message}
    if session_id:
        payload["session_id"] = session_id
    try:
        await ws.send_text(json.dumps(payload))
    except Exception:
        pass