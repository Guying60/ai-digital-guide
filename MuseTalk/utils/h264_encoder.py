"""H.264 streaming encoder (PyAV / h264_nvenc → libx264 fallback) for the MediaCodec 硬解管线.

把 MuseTalk 推理产出的 (3, H, W) uint8 RGB CUDA tensor 逐帧编码成 H.264 Annex-B
access unit，供 Android MediaCodec 硬解直渲。每句话用一个独立的 H264StreamEncoder
实例（保证首包是带 in-band SPS/PPS 的 IDR），句末 close() flush 残留包。

优化点：
  1. 优先使用 h264_nvenc（GPU 编码，~0.5ms/帧），失败时自动回落 libx264 veryfast。
  2. encode_frames_batch：一次性批量 D2H（一个大 DMA），比逐帧 D2H 快 5~10 倍。
  3. encode_frame 保留兼容旧调用方，内部复用批量路径。
"""

import logging
from fractions import Fraction
from typing import List, Optional, Tuple

import av
import numpy as np
import torch

logger = logging.getLogger(__name__)

# 三态：None=未检测，True=可用，False=不可用
_NVENC_OK: Optional[bool] = None


def _nvenc_available() -> bool:
    """真实探测 NVENC：用一帧实际编码触发 avcodec_open2，而不是只检查 codec 注册。

    旧做法 av.codec.Codec("h264_nvenc", "w") 只能确认 FFmpeg 编译时带了 nvenc，
    无法验证运行时 GPU 是否真实支持（驱动版本/容器权限等可能导致 OpenEncodeSessionEx 失败）。
    """
    global _NVENC_OK
    if _NVENC_OK is not None:
        return _NVENC_OK
    try:
        ctx = av.CodecContext.create("h264_nvenc", "w")
        ctx.width = 128
        ctx.height = 128
        ctx.pix_fmt = "yuv420p"
        ctx.time_base = Fraction(1, 25)
        ctx.options = {"preset": "p1", "tune": "ll", "zerolatency": "1", "bf": "0"}
        # 真实编码一帧，触发 avcodec_open2 + OpenEncodeSessionEx
        frame = av.VideoFrame(128, 128, "yuv420p")
        frame.pts = 0
        list(ctx.encode(frame))
        _NVENC_OK = True
        logger.info("[h264] h264_nvenc GPU 编码可用")
    except Exception as e:
        logger.info(f"[h264] h264_nvenc 不可用（{e}），回落 libx264 veryfast")
        _NVENC_OK = False
    return _NVENC_OK


def _build_context(
    use_nvenc: bool,
    width: int,
    height: int,
    fps: int,
    bit_rate: int,
    gop: int,
) -> av.CodecContext:
    """构造编码器 CodecContext。

    NVENC 选项说明：
      preset=p1    最低延迟档（p1~p7，p1 最快）
      tune=ll      low-latency 模式
      zerolatency  禁止帧缓冲，一帧进一帧出
      bf=0         显式禁止 B 帧（ll 模式已隐含，双保险）

    libx264 回落选项说明：
      preset=veryfast    低延迟 + 可接受的压缩效率
      tune=zerolatency   禁止帧缓冲与前瞻
      repeat-headers     每个 IDR 自带 SPS/PPS（in-band），客户端随时可接入/恢复
    """
    if use_nvenc:
        ctx = av.CodecContext.create("h264_nvenc", "w")
        ctx.width = width
        ctx.height = height
        ctx.pix_fmt = "yuv420p"
        ctx.time_base = Fraction(1, fps)
        ctx.bit_rate = bit_rate
        ctx.gop_size = gop
        ctx.max_b_frames = 0
        ctx.options = {
            "preset":      "p1",
            "tune":        "ll",
            "zerolatency": "1",
            "bf":          "0",
            "g":           str(gop),
        }
    else:
        ctx = av.CodecContext.create("libx264", "w")
        ctx.width = width
        ctx.height = height
        ctx.pix_fmt = "yuv420p"
        ctx.time_base = Fraction(1, fps)
        ctx.bit_rate = bit_rate
        ctx.gop_size = gop
        ctx.max_b_frames = 2
        ctx.options = {
            "preset":         "veryfast",
            "tune":           "zerolatency",
            "repeat-headers": "1",
            "g":              str(gop),
        }
    return ctx


class H264StreamEncoder:
    """单句话的 H.264 流式编码器。

    用法（推荐批量）：
        enc = H264StreamEncoder(width=720, height=1280)
        aus = enc.encode_frames_batch(batch_tensors)  # 一次 D2H，最快
        for au_bytes, is_keyframe in aus:
            send(au_bytes, is_keyframe)
        for au_bytes, is_keyframe in enc.close():     # flush 残留包
            send(au_bytes, is_keyframe)

    兼容旧调用（逐帧）：
        for tensor in frames:
            for au_bytes, is_keyframe in enc.encode_frame(tensor):
                send(au_bytes, is_keyframe)
        for au_bytes, is_keyframe in enc.close():
            send(au_bytes, is_keyframe)
    """

    def __init__(
        self,
        width: int = 720,
        height: int = 1280,
        fps: int = 25,
        bit_rate: int = 4_000_000,
        gop: int = 50,
    ):
        self.width = width
        self.height = height
        self._closed = False
        self._pts = 0

        use_nvenc = _nvenc_available()
        self.ctx = _build_context(use_nvenc, width, height, fps, bit_rate, gop)
        backend = "h264_nvenc" if use_nvenc else "libx264(veryfast, fallback)"
        logger.info(f"[h264] 编码器初始化完成：{backend}  {width}x{height}@{fps}fps  bitrate={bit_rate//1000}kbps")

    # ------------------------------------------------------------------
    # 内部工具
    # ------------------------------------------------------------------

    def _packets_to_aus(self, packets) -> List[Tuple[bytes, bool]]:
        out: List[Tuple[bytes, bool]] = []
        for packet in packets:
            data = bytes(packet)
            if not data:
                continue
            out.append((data, bool(packet.is_keyframe)))
        return out

    def _encode_numpy(self, arr: np.ndarray) -> List[Tuple[bytes, bool]]:
        """arr: (H, W, 3) uint8 RGB HWC numpy → encode → AU list."""
        frame = av.VideoFrame.from_ndarray(arr, format="rgb24")
        frame.pts = self._pts
        frame.time_base = self.ctx.time_base
        self._pts += 1
        return self._packets_to_aus(self.ctx.encode(frame))

    # ------------------------------------------------------------------
    # 公开 API
    # ------------------------------------------------------------------

    def encode_frames_batch(
        self, tensors: List[torch.Tensor]
    ) -> List[Tuple[bytes, bool]]:
        """批量编码，推荐在 generate_frames 的 yield 批次后调用。

        优势：把 N 帧 CUDA tensor 合并成一次大 D2H DMA，
        比 N 次逐帧 .cpu() 调用快 5~10 倍（PCIe 调度开销）。

        tensors: list of (3, H, W) uint8 RGB CUDA tensor，长度任意。
        返回:    [(annexb_bytes, is_keyframe), ...]，顺序与输入帧一致。
        """
        if self._closed:
            raise RuntimeError("encoder already closed")
        if not tensors:
            return []

        # 一次性批量 D2H：stack → permute → contiguous → cpu → numpy
        # 整体走一次 PCIe DMA，比逐帧 .cpu() 快得多
        batch_np: np.ndarray = (
            torch.stack(tensors)      # (B, 3, H, W) uint8
            .permute(0, 2, 3, 1)      # (B, H, W, 3) uint8
            .contiguous()
            .cpu()
            .numpy()
        )

        out: List[Tuple[bytes, bool]] = []
        for arr in batch_np:
            out.extend(self._encode_numpy(arr))
        return out

    def encode_frame(self, tensor: torch.Tensor) -> List[Tuple[bytes, bool]]:
        """编码单帧，兼容旧调用方。内部直接走 encode_frames_batch(1帧)。

        tensor: (3, H, W) uint8 RGB，可在 cuda 上。
        返回:   [(annexb_bytes, is_keyframe), ...]（zerolatency 下通常恰好 1 个）。
        """
        return self.encode_frames_batch([tensor])

    def close(self) -> List[Tuple[bytes, bool]]:
        """flush 编码器残留包并释放底层资源；幂等，重复调用返回 []。"""
        if self._closed:
            return []
        self._closed = True
        out: List[Tuple[bytes, bool]] = []
        try:
            out = self._packets_to_aus(self.ctx.encode(None))
        except Exception as e:  # noqa: BLE001
            logger.warning(f"[h264] flush 失败: {e}")
        try:
            self.ctx.close()
        except Exception:
            pass
        return out
