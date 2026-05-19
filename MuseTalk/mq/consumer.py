import asyncio
import json
import logging

import aio_pika
import aiohttp

from config import MQ_URL, MQ_QUEUE_NAME, MQ_DELETE_QUEUE_NAME, BASE_VIDEO_DIR, VOICES_DIR, COSYVOICE_URL
from utils.helper import download_video

logger = logging.getLogger(__name__)


async def extract_audio(video_path: str, attraction_id: str) -> str:
    output_path = VOICES_DIR / f"{attraction_id}.wav"
    VOICES_DIR.mkdir(parents=True, exist_ok=True)

    proc = await asyncio.create_subprocess_exec(
        "ffmpeg", "-i", video_path,
        "-t", "30",
        "-ar", "16000",
        "-ac", "1",
        "-vn",
        "-y", str(output_path),
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    _, stderr = await proc.communicate()
    if proc.returncode != 0:
        raise RuntimeError(f"ffmpeg 退出码 {proc.returncode}: {stderr.decode(errors='ignore')[-500:]}")
    return str(output_path)


async def notify_cosyvoice_reload() -> None:
    try:
        timeout = aiohttp.ClientTimeout(total=10)
        async with aiohttp.ClientSession(timeout=timeout) as session:
            async with session.post(f"{COSYVOICE_URL}/voices/reload") as resp:
                logger.info(f"[MQ] CosyVoice 热加载结果: {resp.status}")
    except Exception as e:
        logger.warning(f"[MQ] 通知 CosyVoice 热加载失败（不影响主流程）: {e}")


async def start_consumer(engine) -> None:
    while True:
        try:
            connection = await aio_pika.connect_robust(MQ_URL)
            async with connection:
                channel = await connection.channel()

                preload_queue = await channel.declare_queue(
                    MQ_QUEUE_NAME,
                    durable=True,
                )
                delete_queue = await channel.declare_queue(
                    MQ_DELETE_QUEUE_NAME,
                    durable=True,
                )
                logger.info(f"[MQ] 开始监听队列: {MQ_QUEUE_NAME}, {MQ_DELETE_QUEUE_NAME}")

                async def handle_preload(message: aio_pika.IncomingMessage):
                    async with message.process():
                        try:
                            body = json.loads(message.body.decode())
                            attraction_id = str(body["attractionId"])
                            video_url = body["videoUrl"]
                            dest = str(BASE_VIDEO_DIR / f"{attraction_id}.mp4")

                            logger.info(f"[MQ] 收到预加载消息: attractionId={attraction_id}")
                            await download_video(video_url, dest)
                            logger.info(f"[MQ] 下载完成: {dest}")

                            # 驱逐旧缓存，确保更新场景也能重新加载
                            engine.avatar_cache.pop(attraction_id, None)

                            await asyncio.to_thread(engine.load_avatar, attraction_id)
                            logger.info(f"[MQ] 预加载完成: {attraction_id}")

                            try:
                                audio_path = await extract_audio(dest, attraction_id)
                                logger.info(f"[MQ] 音频抽取完成: {audio_path}")
                                await notify_cosyvoice_reload()
                            except Exception as e:
                                logger.warning(f"[MQ] 音频抽取失败（不影响主流程）: {e}", exc_info=True)
                        except Exception as e:
                            logger.error(f"[MQ] 处理预加载消息失败: {e}", exc_info=True)

                async def handle_delete(message: aio_pika.IncomingMessage):
                    async with message.process():
                        try:
                            body = json.loads(message.body.decode())
                            attraction_id = str(body["attractionId"])
                            logger.info(f"[MQ] 收到删除消息: attractionId={attraction_id}")

                            engine.avatar_cache.pop(attraction_id, None)
                            logger.info(f"[MQ] 已清除缓存: {attraction_id}")

                            video_path = BASE_VIDEO_DIR / f"{attraction_id}.mp4"
                            if video_path.exists():
                                video_path.unlink()
                                logger.info(f"[MQ] 已删除视频: {video_path}")

                            audio_path = VOICES_DIR / f"{attraction_id}.wav"
                            if audio_path.exists():
                                audio_path.unlink()
                                logger.info(f"[MQ] 已删除音频: {audio_path}")
                                await notify_cosyvoice_reload()
                        except Exception as e:
                            logger.error(f"[MQ] 处理删除消息失败: {e}", exc_info=True)

                await preload_queue.consume(handle_preload)
                await delete_queue.consume(handle_delete)

                # 保持连接存活
                await asyncio.Future()
        except Exception as e:
            logger.error(f"[MQ] 连接断开，5秒后重连: {e}", exc_info=True)
            await asyncio.sleep(5)
