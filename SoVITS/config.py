import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent

COSYVOICE_REPO = os.environ.get(
    "COSYVOICE_REPO",
    "/root/autodl-tmp/CosyVoice",
)

MODEL_DIR = os.environ.get(
    "COSYVOICE_MODEL_DIR",
    "/root/autodl-tmp/CosyVoice/pretrained_models/Fun-CosyVoice3-0.5B",
)

VOICES_DIR = Path(os.environ.get("COSYVOICE_VOICES_DIR", "/root/autodl-tmp/voices"))

DEFAULT_VOICE_ID = os.environ.get("COSYVOICE_DEFAULT_VOICE", "default")

# Voices are loaded from {VOICES_DIR}/{attraction_id}.wav. The prompt
# text (the script spoken in the reference clips) is read once from
# {VOICES_DIR}/prompt.txt and shared by all attractions. Falls back to
# PROMPT_TEXT_FALLBACK when prompt.txt is missing. CosyVoice zero-shot
# quality drops sharply without an accurate prompt transcript.
# CosyVoice3 内部采样率为 24kHz；下游 Android / MuseTalk 都要 16kHz 16bit
# 单声道 PCM，引擎统一在输出端重采样。
MODEL_SAMPLE_RATE = 24000
OUTPUT_SAMPLE_RATE = 16000

# Java / OkHttp 单帧体积上限默认 16MB，这里给个宽松上限就行。
WS_MAX_MESSAGE_BYTES = 4 * 1024 * 1024

WARMUP_TEXT = os.environ.get(
    "COSYVOICE_WARMUP_TEXT",
    "你好，欢迎使用语音合成服务，这是一段用于预热模型的测试文本。",
)
TTS_SPEED = float(os.getenv("TTS_SPEED", "0.9"))

# 音频自然化处理开关（提高Whisper识别准确率）
# 当Whisper对TTS音频的特征相似度<0.5时，需要启用此功能
ENABLE_AUDIO_NATURALIZATION = os.getenv("ENABLE_AUDIO_NATURALIZATION", "true").lower() == "true"

LOG_LEVEL = os.environ.get("COSYVOICE_LOG_LEVEL", "INFO")
