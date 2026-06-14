import logging
from typing import List, Optional

import aiofiles
import aiohttp
import cv2
import numpy as np
import torch

logger = logging.getLogger(__name__)


def encode_frame(frame_np: np.ndarray, quality: int = 80, target_size: tuple = (720, 1280)) -> bytes:
    """CPU JPEG 编码（兼容旧调用方）。

    target_size 为 (W, H)；与 generate_frames 返回 ndarray 时的旧路径配合。
    """
    if target_size is not None and frame_np.shape[:2] != (target_size[1], target_size[0]):
        frame_np = cv2.resize(frame_np, target_size, interpolation=cv2.INTER_AREA)

    encode_param = [int(cv2.IMWRITE_JPEG_QUALITY), quality]
    success, buffer = cv2.imencode('.jpg', frame_np, encode_param)
    if not success:
        return b""
    return buffer.tobytes()


def _peek_ahead(iterable):
    it = iter(iterable)
    try:
        first = next(it)
    except StopIteration:
        return
    for second in it:
        yield first, False
        first = second
    yield first, True


# ---------------------------------------------------------------------------
# GPU JPEG 编码（NVJPEG 路径，失败时回落到 CPU cv2.imencode）
# ---------------------------------------------------------------------------

# 三态：None=未试过，True=可用，False=已确认不可用（永久回落 CPU）
_JPEG_GPU_OK: Optional[bool] = None


def encode_frames_gpu(imgs_cuda_u8: List[torch.Tensor], quality: int = 50) -> List[bytes]:
    """批量 JPEG 编码。

    imgs_cuda_u8: list of (3, H, W) uint8 RGB tensor on cuda。
    返回每张图的 JPEG bytestream。
    """
    global _JPEG_GPU_OK
    if not imgs_cuda_u8:
        return []

    if _JPEG_GPU_OK is False:
        return _encode_frames_cpu(imgs_cuda_u8, quality)

    try:
        from torchvision.io import encode_jpeg
        out = encode_jpeg(imgs_cuda_u8, quality=quality)
        if isinstance(out, torch.Tensor):
            out = [out]
        result = [t.cpu().numpy().tobytes() for t in out]
        if _JPEG_GPU_OK is None:
            logger.info("[helper] GPU JPEG (NVJPEG) 路径已启用")
            _JPEG_GPU_OK = True
        return result
    except Exception as e:
        if _JPEG_GPU_OK is None:
            logger.warning(f"[helper] GPU JPEG encode 不可用，永久回落 CPU：{e}")
        _JPEG_GPU_OK = False
        return _encode_frames_cpu(imgs_cuda_u8, quality)


def _encode_frames_cpu(imgs_cuda_u8: List[torch.Tensor], quality: int) -> List[bytes]:
    """CPU 回落：把 cuda uint8 RGB CHW → numpy HWC BGR → cv2.imencode。"""
    out: List[bytes] = []
    encode_param = [int(cv2.IMWRITE_JPEG_QUALITY), quality]
    for t in imgs_cuda_u8:
        arr = t.detach().permute(1, 2, 0).contiguous().cpu().numpy()
        arr_bgr = cv2.cvtColor(arr, cv2.COLOR_RGB2BGR)
        ok, buf = cv2.imencode('.jpg', arr_bgr, encode_param)
        out.append(buf.tobytes() if ok else b"")
    return out


async def download_video(url: str, dest_path: str) -> None:
    timeout = aiohttp.ClientTimeout(total=600)
    async with aiohttp.ClientSession(timeout=timeout) as session:
        async with session.get(url) as resp:
            resp.raise_for_status()
            async with aiofiles.open(dest_path, 'wb') as f:
                async for chunk in resp.content.iter_chunked(8192):
                    await f.write(chunk)
