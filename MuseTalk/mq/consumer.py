import asyncio
import json
import logging

import aio_pika
import aiohttp

from config import MQ_URL, MQ_QUEUE_NAME, BASE_VIDEO_DIR, VOICES_DIR, COSYVOICE_URL
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
                queue = await channel.declare_queue(
                    MQ_QUEUE_NAME,
                    durable=True,
                )
                logger.info(f"[MQ] 开始监听队列: {MQ_QUEUE_NAME}")

                async with queue.iterator() as queue_iter:
                    async for message in queue_iter:
                        async with message.process():
                            try:
                                body = json.loads(message.body.decode())
                                attraction_id = str(body["attractionId"])
                                video_url = body["videoUrl"]
                                dest = str(BASE_VIDEO_DIR / f"{attraction_id}.mp4")

                                logger.info(f"[MQ] 收到消息: attractionId={attraction_id}")
                                await download_video(video_url, dest)
                                logger.info(f"[MQ] 下载完成: {dest}")

                                await asyncio.to_thread(engine.load_avatar, attraction_id)
                                logger.info(f"[MQ] 预加载完成: {attraction_id}")

                                try:
                                    audio_path = await extract_audio(dest, attraction_id)
                                    logger.info(f"[MQ] 音频抽取完成: {audio_path}")
                                    await notify_cosyvoice_reload()
                                except Exception as e:
                                    logger.warning(f"[MQ] 音频抽取失败（不影响主流程）: {e}", exc_info=True)
                            except Exception as e:
                                logger.error(f"[MQ] 处理消息失败: {e}", exc_info=True)
        except Exception as e:
            logger.error(f"[MQ] 连接断开，5秒后重连: {e}", exc_info=True)
            await asyncio.sleep(5)
