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
    try:
        while True:
            raw = await ws.receive_text()
            try:
                msg = json.loads(raw)
            except json.JSONDecodeError:
                await _send_error(ws, "invalid json")
                continue

            if msg.get("type") != "synthesize":
                await _send_error(ws, f"unsupported type: {msg.get('type')}")
                continue

            text: str = msg.get("text") or ""
            attraction_id: Optional[str] = (
                str(msg["attraction_id"]) if msg.get("attraction_id") is not None else None
            )
            session_id: Optional[str] = msg.get("session_id")

            if not text.strip():
                await _send_chunk_end(ws, session_id)
                continue

            logger.info(
                "[tts] sid=%s session=%s attraction=%s text=%r",
                sid, session_id, attraction_id, text[:60],
            )
            try:
                async for pcm in engine.stream_pcm(text, attraction_id):
                    await ws.send_bytes(pcm)
                await _send_chunk_end(ws, session_id)
            except Exception as exc:
                logger.exception("[tts] synth failed sid=%s", sid)
                await _send_error(ws, str(exc), session_id)
    except WebSocketDisconnect:
        logger.info("[tts] ws disconnected sid=%s", sid)
    except Exception:
        logger.exception("[tts] ws error sid=%s", sid)
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
