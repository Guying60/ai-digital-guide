import asyncio
import logging
import time
from contextlib import asynccontextmanager

from fastapi import FastAPI

from config import BASE_VIDEO_DIR, HOST, PORT, TEST_VIDEO_DIR
from services.musetalk_engine import MuseTalkEngine
from api.ws_routes import router as ws_router
from api.admin_routes import router as admin_router
from mq.consumer import start_consumer

logging.basicConfig(level=logging.INFO, format="%(asctime)s | %(levelname)s | %(message)s")
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    engine = MuseTalkEngine()
    app.state.engine = engine

    mq_task = asyncio.create_task(start_consumer(engine))

    async def _cleanup_test_videos():
        TEST_VIDEO_DIR.mkdir(parents=True, exist_ok=True)
        while True:
            await asyncio.sleep(3600)
            now = time.time()
            for f in TEST_VIDEO_DIR.glob("*.mp4"):
                try:
                    if now - f.stat().st_mtime > 86400:
                        f.unlink()
                        logger.info(f"[清理] 已删除过期测试视频: {f.name}")
                except OSError:
                    pass

    cleanup_task = asyncio.create_task(_cleanup_test_videos())

    mp4_files = list(BASE_VIDEO_DIR.glob("*.mp4"))
    logger.info(f"[启动] 扫描到 {len(mp4_files)} 个已有视频，开始预加载...")
    first_loaded: str | None = None
    for mp4_file in mp4_files:
        attraction_id = mp4_file.stem
        try:
            await asyncio.to_thread(engine.load_avatar, attraction_id)
            if first_loaded is None:
                first_loaded = attraction_id
        except Exception as e:
            logger.error(f"[启动] 预加载失败 {attraction_id}: {e}", exc_info=True)

    if first_loaded is not None:
        logger.info(f"[启动] 开始端到端预热（attraction={first_loaded}）...")
        try:
            await asyncio.to_thread(engine.warmup, first_loaded)
        except Exception as e:
            logger.error(f"[启动] 预热失败（不影响服务）：{e}", exc_info=True)

    logger.info("[启动] 预加载全部完成，开始对外服务")
    yield

    mq_task.cancel()
    cleanup_task.cancel()
    try:
        await mq_task
    except asyncio.CancelledError:
        pass
    try:
        await cleanup_task
    except asyncio.CancelledError:
        pass
    logger.info("[关闭] 服务已停止")


app = FastAPI(lifespan=lifespan)
app.include_router(ws_router)
app.include_router(admin_router)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        app,
        host=HOST,
        port=PORT,
        ws_ping_interval=None,
        ws_ping_timeout=None,
    )
