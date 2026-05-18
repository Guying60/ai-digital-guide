import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from api.tts_routes import router as tts_router
from config import LOG_LEVEL
from services.cosyvoice_engine import get_engine

logging.basicConfig(
    level=LOG_LEVEL,
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
)
logger = logging.getLogger("cosyvoice-service")


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("starting CosyVoice service")
    get_engine().load()
    logger.info("CosyVoice service ready")
    yield
    logger.info("CosyVoice service shutting down")


app = FastAPI(title="CosyVoice TTS Service", lifespan=lifespan)
app.include_router(tts_router)


@app.get("/")
async def root() -> dict:
    return {"service": "cosyvoice-tts", "status": "ok"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=6008)
