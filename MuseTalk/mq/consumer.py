import asyncio
import json
import logging

import aio_pika
import aiohttp
import redis.asyncio as aioredis

from config import (
    MQ_URL, MQ_QUEUE_NAME, MQ_DELETE_QUEUE_NAME, MQ_TEST_QUEUE_NAME,
    BASE_VIDEO_DIR, VOICES_DIR, TEST_VIDEO_DIR, COSYVOICE_URL,
    REDIS_TEST_VIDEO_KEY, REDIS_TEST_VIDEO_TTL, DEFAULT_TEST_TEXT_PATH,
)
from utils.helper import download_file

logger = logging.getLogger(__name__)


async def convert_audio(audio_path: str, digital_human_id: str) -> str:
    output_path = VOICES_DIR / f"{digital_human_id}.wav"
    VOICES_DIR.mkdir(parents=True, exist_ok=True)

    proc = await asyncio.create_subprocess_exec(
        "ffmpeg", "-i", audio_path,
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


_redis: aioredis.Redis | None = None


async def _set_preload_status(digital_human_id: str, status: str) -> None:
    """将预加载状态写入 Java 后端 Redis，供前端轮询。

    status 值需与 Java TaskStatusEnum 一致: PROCESSING, SUCCESS, FAILED
    """
    global _redis
    if _redis is None:
        from config import REDIS_HOST, REDIS_PORT, REDIS_DB, REDIS_PASSWORD
        _redis = await aioredis.from_url(
            f"redis://:{REDIS_PASSWORD}@{REDIS_HOST}:{REDIS_PORT}/{REDIS_DB}",
            decode_responses=True,
        )
    from config import REDIS_PRELOAD_STATUS_KEY, REDIS_PRELOAD_STATUS_TTL
    key = f"{REDIS_PRELOAD_STATUS_KEY}{digital_human_id}"
    try:
        await _redis.set(key, status, ex=REDIS_PRELOAD_STATUS_TTL)
        logger.info(f"[Redis] 设置预加载状态: {key} -> {status}")
    except Exception as e:
        logger.warning(f"[Redis] 设置状态失败（不影响主流程）: {e}")


async def _clear_preload_status(digital_human_id: str) -> None:
    """删除预加载状态 key，用于删除数字人时清理"""
    global _redis
    if _redis is None:
        from config import REDIS_HOST, REDIS_PORT, REDIS_DB, REDIS_PASSWORD
        _redis = await aioredis.from_url(
            f"redis://:{REDIS_PASSWORD}@{REDIS_HOST}:{REDIS_PORT}/{REDIS_DB}",
            decode_responses=True,
        )
    from config import REDIS_PRELOAD_STATUS_KEY
    key = f"{REDIS_PRELOAD_STATUS_KEY}{digital_human_id}"
    try:
        await _redis.delete(key)
        logger.info(f"[Redis] 已清除预加载状态: {key}")
    except Exception as e:
        logger.warning(f"[Redis] 清除状态失败（不影响主流程）: {e}")


async def _set_test_video_status(digital_human_id: str, status: str) -> None:
    """将测试视频生成状态写入 Redis。"""
    global _redis
    if _redis is None:
        from config import REDIS_HOST, REDIS_PORT, REDIS_DB, REDIS_PASSWORD
        _redis = await aioredis.from_url(
            f"redis://:{REDIS_PASSWORD}@{REDIS_HOST}:{REDIS_PORT}/{REDIS_DB}",
            decode_responses=True,
        )
    key = f"{REDIS_TEST_VIDEO_KEY}{digital_human_id}"
    try:
        await _redis.set(key, status, ex=REDIS_TEST_VIDEO_TTL)
        logger.info(f"[Redis] 设置测试视频状态: {key} -> {status}")
    except Exception as e:
        logger.warning(f"[Redis] 设置测试视频状态失败（不影响主流程）: {e}")


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
                test_queue = await channel.declare_queue(
                    MQ_TEST_QUEUE_NAME,
                    durable=True,
                )
                logger.info(f"[MQ] 开始监听队列: {MQ_QUEUE_NAME}, {MQ_DELETE_QUEUE_NAME}, {MQ_TEST_QUEUE_NAME}")

                async def handle_preload(message: aio_pika.IncomingMessage):
                    async with message.process():
                        digital_human_id = "unknown"
                        try:
                            body = json.loads(message.body.decode())
                            digital_human_id = str(body["digitalHumanId"])
                            video_url = body["videoUrl"]
                            audio_url = body["audioUrl"]
                            BASE_VIDEO_DIR.mkdir(parents=True, exist_ok=True)
                            VOICES_DIR.mkdir(parents=True, exist_ok=True)
                            video_dest = str(BASE_VIDEO_DIR / f"{digital_human_id}.mp4")
                            audio_source = VOICES_DIR / f"{digital_human_id}.source"

                            logger.info(f"[MQ] 收到预加载消息: digitalHumanId={digital_human_id}")
                            await _set_preload_status(digital_human_id, "PROCESSING")
                            await download_file(video_url, video_dest)
                            logger.info(f"[MQ] 视频下载完成: {video_dest}")

                            await download_file(audio_url, str(audio_source))
                            audio_path = await convert_audio(str(audio_source), digital_human_id)
                            logger.info(f"[MQ] 音频下载并转码完成: {audio_path}")

                            # 驱逐旧缓存，确保更新场景也能重新加载
                            engine.avatar_cache.pop(digital_human_id, None)

                            await asyncio.to_thread(engine.load_avatar, digital_human_id)
                            logger.info(f"[MQ] 预加载完成: {digital_human_id}")
                            await _set_preload_status(digital_human_id, "SUCCESS")
                            await notify_cosyvoice_reload()
                        except Exception as e:
                            logger.error(f"[MQ] 处理预加载消息失败: {e}", exc_info=True)
                            if digital_human_id != "unknown":
                                await _set_preload_status(digital_human_id, "FAILED")
                        finally:
                            try:
                                if "audio_source" in locals() and audio_source.exists():
                                    audio_source.unlink()
                            except Exception as e:
                                logger.warning(f"[MQ] 清理临时音频失败: {e}")

                async def handle_delete(message: aio_pika.IncomingMessage):
                    async with message.process():
                        try:
                            body = json.loads(message.body.decode())
                            digital_human_id = str(body["digitalHumanId"])
                            logger.info(f"[MQ] 收到删除消息: digitalHumanId={digital_human_id}")

                            engine.avatar_cache.pop(digital_human_id, None)
                            logger.info(f"[MQ] 已清除缓存: {digital_human_id}")

                            video_path = BASE_VIDEO_DIR / f"{digital_human_id}.mp4"
                            if video_path.exists():
                                video_path.unlink()
                                logger.info(f"[MQ] 已删除视频: {video_path}")

                            audio_path = VOICES_DIR / f"{digital_human_id}.wav"
                            if audio_path.exists():
                                audio_path.unlink()
                                logger.info(f"[MQ] 已删除音频: {audio_path}")
                                await notify_cosyvoice_reload()
                            await _clear_preload_status(digital_human_id)
                        except Exception as e:
                            logger.error(f"[MQ] 处理删除消息失败: {e}", exc_info=True)

                async def handle_test_video(message: aio_pika.IncomingMessage):
                    async with message.process():
                        try:
                            body = json.loads(message.body.decode())
                            digital_human_id = str(body["digitalHumanId"])
                            test_text = body.get("testText") or ""

                            if not test_text.strip():
                                if DEFAULT_TEST_TEXT_PATH.exists():
                                    test_text = DEFAULT_TEST_TEXT_PATH.read_text(encoding="utf-8").strip()
                                else:
                                    test_text = "你好，欢迎参观本景区。"

                            logger.info(f"[MQ] 收到测试视频消息: digitalHumanId={digital_human_id}, text={test_text[:60]}")

                            await _set_test_video_status(digital_human_id, "PROCESSING")

                            # 调用 SoVITS REST 接口获取 TTS 音频
                            timeout = aiohttp.ClientTimeout(total=60)
                            async with aiohttp.ClientSession(timeout=timeout) as session:
                                async with session.post(
                                    f"{COSYVOICE_URL}/tts/offline",
                                    json={"text": test_text, "digital_human_id": digital_human_id},
                                ) as resp:
                                    if resp.status != 200:
                                        raise RuntimeError(f"TTS 请求失败: HTTP {resp.status}")
                                    wav_bytes = await resp.read()

                            # 从 WAV 提取 PCM（跳过 44 字节 WAV 头）
                            if len(wav_bytes) < 44:
                                raise RuntimeError("TTS 返回的 WAV 数据太短")
                            pcm = wav_bytes[44:]

                            output_path = str(TEST_VIDEO_DIR / f"{digital_human_id}.mp4")
                            # 清除旧测试视频（如果存在）
                            import os as _os
                            try:
                                _os.unlink(output_path)
                            except OSError:
                                pass

                            await asyncio.to_thread(
                                engine.generate_video_file, pcm, digital_human_id, output_path
                            )
                            logger.info(f"[MQ] 测试视频生成完成: {digital_human_id}")
                            await _set_test_video_status(digital_human_id, "SUCCESS")
                        except Exception as e:
                            logger.error(f"[MQ] 测试视频生成失败: {e}", exc_info=True)
                            await _set_test_video_status(digital_human_id, "FAILED")

                await preload_queue.consume(handle_preload)
                await delete_queue.consume(handle_delete)
                await test_queue.consume(handle_test_video)

                # 保持连接存活
                await asyncio.Future()
        except Exception as e:
            logger.error(f"[MQ] 连接断开，5秒后重连: {e}", exc_info=True)
            await asyncio.sleep(5)
