import asyncio
import json
import logging
import uuid
from typing import Optional

from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from services.cosyvoice_engine import get_engine

logger = logging.getLogger(__name__)

router = APIRouter()


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


@router.websocket("/ws/tts")
async def ws_tts(ws: WebSocket) -> None:
    await ws.accept()
    sid = str(uuid.uuid4())[:8]
    logger.info("[tts] ws connected sid=%s", sid)
    engine = get_engine()

    # 单连接内的会话上下文
    attraction_id: Optional[str] = None
    job_queue: asyncio.Queue = asyncio.Queue()        # (text, attraction_id, session_id)
    interrupt_flag = asyncio.Event()                   # 当前句中断信号
    current_task: Optional[asyncio.Task] = None        # 当前 worker 正在跑的句子任务
    shutdown = asyncio.Event()

    async def synthesize_one(
        text: str, attr: Optional[str], session_id: Optional[str]
    ) -> None:
        try:
            async for pcm in engine.stream_pcm(text, attr):
                if interrupt_flag.is_set() or shutdown.is_set():
                    logger.info("[tts] sid=%s 中断当前句，停止发送 PCM", sid)
                    break
                await ws.send_bytes(pcm)
            if not interrupt_flag.is_set() and not shutdown.is_set():
                await _send_chunk_end(ws, session_id)
        except asyncio.CancelledError:
            raise
        except Exception as exc:
            logger.exception("[tts] synth failed sid=%s", sid)
            try:
                await _send_error(ws, str(exc), session_id)
            except Exception:
                pass

    async def worker() -> None:
        nonlocal current_task
        while not shutdown.is_set():
            try:
                text, attr, session_id = await job_queue.get()
            except asyncio.CancelledError:
                break
            interrupt_flag.clear()
            current_task = asyncio.create_task(synthesize_one(text, attr, session_id))
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
                    await _send_chunk_end(ws, session_id)
                    continue
                logger.info(
                    "[tts] sid=%s session=%s attraction=%s text=%r",
                    sid, session_id, attr, text[:60],
                )
                await job_queue.put((text, attr, session_id))
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


async def _send_chunk_end(ws: WebSocket, session_id: Optional[str]) -> None:
    payload = {"type": "chunk_end"}
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
