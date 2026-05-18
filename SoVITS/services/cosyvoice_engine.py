import asyncio
import logging
import sys
import threading
from pathlib import Path
from typing import AsyncIterator, Dict, Optional

import numpy as np
import torch
import torchaudio

from config import (
    COSYVOICE_REPO,
    DEFAULT_VOICE_ID,
    MODEL_DIR,
    MODEL_SAMPLE_RATE,
    OUTPUT_SAMPLE_RATE,
    VOICES_DIR,
    WARMUP_TEXT,
)

if COSYVOICE_REPO not in sys.path:
    sys.path.insert(0, COSYVOICE_REPO)

logger = logging.getLogger(__name__)


def _load_prompt_wav(wav_path: Path) -> str:
    """Return the wav path as a string. CosyVoice's frontend loads it
    internally via soundfile (load_wav inside _extract_speech_feat /
    _extract_speech_token / _extract_spk_embedding), so we must hand it
    the path, not a pre-loaded tensor.
    """
    return str(wav_path)


# CosyVoice3 LLM 强制要求 prompt_text 中包含 <|endofprompt|> (token 151646)。
# 官方 example.py 的写法是在真正的 prompt 转录文本前加一段 system prompt，
# 用 <|endofprompt|> 收尾。我们对所有读到的 prompt 文本都自动补这个前缀。
COSYVOICE3_SYSTEM_PROMPT = "You are a helpful assistant.<|endofprompt|>"


def _wrap_prompt_text(text: str) -> str:
    if "<|endofprompt|>" in text:
        return text
    return COSYVOICE3_SYSTEM_PROMPT + text


def _load_shared_prompt_text() -> str:
    """Read VOICES_DIR/prompt.txt once; all attractions share it."""
    txt_path = VOICES_DIR / "prompt.txt"
    if not txt_path.exists():
        raise FileNotFoundError(f"prompt text not found: {txt_path}")
    text = txt_path.read_text(encoding="utf-8").strip()
    if not text:
        raise ValueError(f"prompt text is empty: {txt_path}")
    return _wrap_prompt_text(text)


class CosyVoiceEngine:
    """Wraps CosyVoice3 zero-shot streaming inference.

    Voices live in VOICES_DIR/{attraction_id}.wav, all sharing a single
    VOICES_DIR/prompt.txt transcript. On startup all voices are registered
    with the frontend via add_zero_shot_spk so the per-call frontend skips
    speaker feature extraction. POST /voices/reload re-runs the scan.

    A single GPU lock serialises generate() calls — CosyVoice's torch
    model is not reentrant on a shared CUDA context.
    """

    def __init__(self) -> None:
        self._model = None
        self._gpu_lock = threading.Lock()
        self._voice_lock = threading.Lock()
        # spk_id -> prompt text used at registration (kept for diagnostics
        # and so we can re-pass it through inference_zero_shot, since v3
        # still consumes prompt_text token even when the speaker info is
        # cached via spk_id).
        self._voice_prompt_texts: Dict[str, str] = {}
        self._loaded = False

    @property
    def model_sample_rate(self) -> int:
        return self._model.sample_rate if self._model is not None else MODEL_SAMPLE_RATE

    @property
    def output_sample_rate(self) -> int:
        return OUTPUT_SAMPLE_RATE

    def load(self) -> None:
        if self._loaded:
            return
        from cosyvoice.cli.cosyvoice import CosyVoice3

        logger.info("Loading CosyVoice3 from %s", MODEL_DIR)
        # 5090 上 fp16 推理更快；trt 由用户后续按需开启。
        self._model = CosyVoice3(MODEL_DIR, fp16=True)
        logger.info("CosyVoice3 loaded; native sample_rate=%d", self._model.sample_rate)
        self.reload_voices()
        self._warmup()
        self._loaded = True

    def reload_voices(self) -> Dict[str, str]:
        """Scan VOICES_DIR for *.wav and (re)register each as a zero-shot
        speaker. Returns mapping {spk_id: status}.
        """
        if self._model is None:
            raise RuntimeError("engine not loaded")

        VOICES_DIR.mkdir(parents=True, exist_ok=True)
        report: Dict[str, str] = {}

        wavs = sorted(VOICES_DIR.glob("*.wav"))
        if not wavs:
            logger.warning("no voices found under %s", VOICES_DIR)

        prompt_text = _load_shared_prompt_text()
        with self._voice_lock:
            for wav_path in wavs:
                spk_id = wav_path.stem
                try:
                    prompt_wav = _load_prompt_wav(wav_path)
                    with self._gpu_lock:
                        self._model.add_zero_shot_spk(prompt_text, prompt_wav, spk_id)
                    self._voice_prompt_texts[spk_id] = prompt_text
                    report[spk_id] = "ok"
                    logger.info("registered voice spk_id=%s", spk_id)
                except Exception as exc:
                    report[spk_id] = f"error: {exc}"
                    logger.exception("failed to register voice %s", spk_id)

        if DEFAULT_VOICE_ID not in self._voice_prompt_texts:
            logger.warning(
                "default voice '%s' missing — synthesize() with an unknown "
                "attraction_id will fail until %s/%s.wav is provided",
                DEFAULT_VOICE_ID, VOICES_DIR, DEFAULT_VOICE_ID,
            )
        return report

    def list_voices(self) -> Dict[str, str]:
        with self._voice_lock:
            return dict(self._voice_prompt_texts)

    def _resolve_voice(self, attraction_id: Optional[str]) -> str:
        if attraction_id and attraction_id in self._voice_prompt_texts:
            return attraction_id
        if DEFAULT_VOICE_ID in self._voice_prompt_texts:
            if attraction_id:
                logger.info(
                    "voice '%s' not loaded, falling back to default", attraction_id
                )
            return DEFAULT_VOICE_ID
        raise RuntimeError(
            f"voice '{attraction_id}' not loaded and no default voice available"
        )

    def _to_pcm16_bytes(self, speech: torch.Tensor) -> bytes:
        """speech: float tensor [1, T] at MODEL_SAMPLE_RATE -> 16kHz s16le bytes."""
        if speech.dim() == 2:
            speech = speech.squeeze(0)
        speech = speech.detach().to("cpu", dtype=torch.float32)
        if self.model_sample_rate != OUTPUT_SAMPLE_RATE:
            speech = torchaudio.functional.resample(
                speech, orig_freq=self.model_sample_rate, new_freq=OUTPUT_SAMPLE_RATE
            )
        samples = speech.numpy()
        np.clip(samples, -1.0, 1.0, out=samples)
        pcm = (samples * 32767.0).astype(np.int16, copy=False)
        return pcm.tobytes()

    def _warmup(self) -> None:
        if not self._voice_prompt_texts:
            logger.info("skip warmup: no voice registered")
            return
        spk_id = (
            DEFAULT_VOICE_ID
            if DEFAULT_VOICE_ID in self._voice_prompt_texts
            else next(iter(self._voice_prompt_texts))
        )
        prompt_text = self._voice_prompt_texts[spk_id]
        wav_path = VOICES_DIR / f"{spk_id}.wav"
        try:
            prompt_wav = _load_prompt_wav(wav_path)
            with self._gpu_lock:
                for _ in self._model.inference_zero_shot(
                    WARMUP_TEXT,
                    prompt_text,
                    prompt_wav,
                    zero_shot_spk_id=spk_id,
                    stream=False,
                ):
                    pass
            logger.info("warmup done with spk_id=%s", spk_id)
        except Exception:
            logger.exception("warmup failed (non-fatal)")

    async def stream_pcm(
        self,
        text: str,
        attraction_id: Optional[str],
    ) -> AsyncIterator[bytes]:
        """Async generator yielding 16kHz s16le PCM chunks for `text`."""
        if self._model is None:
            raise RuntimeError("engine not loaded")
        if not text or not text.strip():
            return

        spk_id = self._resolve_voice(attraction_id)
        prompt_text = self._voice_prompt_texts[spk_id]
        wav_path = VOICES_DIR / f"{spk_id}.wav"
        prompt_wav = _load_prompt_wav(wav_path)

        loop = asyncio.get_running_loop()
        queue: asyncio.Queue = asyncio.Queue(maxsize=32)
        sentinel = object()

        def producer() -> None:
            try:
                with self._gpu_lock:
                    for output in self._model.inference_zero_shot(
                        text,
                        prompt_text,
                        prompt_wav,
                        zero_shot_spk_id=spk_id,
                        stream=True,
                    ):
                        pcm = self._to_pcm16_bytes(output["tts_speech"])
                        if pcm:
                            asyncio.run_coroutine_threadsafe(
                                queue.put(pcm), loop
                            ).result()
            except Exception as exc:
                asyncio.run_coroutine_threadsafe(queue.put(exc), loop).result()
            finally:
                asyncio.run_coroutine_threadsafe(queue.put(sentinel), loop).result()

        task = loop.run_in_executor(None, producer)
        try:
            while True:
                item = await queue.get()
                if item is sentinel:
                    break
                if isinstance(item, BaseException):
                    raise item
                yield item
        finally:
            await task


_engine: Optional[CosyVoiceEngine] = None


def get_engine() -> CosyVoiceEngine:
    global _engine
    if _engine is None:
        _engine = CosyVoiceEngine()
    return _engine
