"""H.264 streaming encoder (PyAV / libx264) for the MediaCodec 硬解管线.

把 MuseTalk 推理产出的 (3, H, W) uint8 RGB CUDA tensor 逐帧编码成 H.264 Annex-B
access unit，供 Android MediaCodec 硬解直渲。每句话用一个独立的 H264StreamEncoder
实例（保证首包是带 in-band SPS/PPS 的 IDR），句末 close() flush 残留包。

替代原 utils.helper.encode_frames_gpu 的 JPEG 路径。
"""

import logging
from fractions import Fraction
from typing import List, Tuple

import av
import torch

logger = logging.getLogger(__name__)


class H264StreamEncoder:
    """单句话的 H.264 流式编码器。

    用法：
        enc = H264StreamEncoder(width=480, height=854)
        for tensor in frames:                 # tensor: (3, H, W) uint8 RGB on cuda
            for au_bytes, is_keyframe in enc.encode_frame(tensor):
                send(au_bytes, is_keyframe)
        for au_bytes, is_keyframe in enc.close():   # flush 残留包
            send(au_bytes, is_keyframe)
    """

    def __init__(
        self,
        width: int = 480,
        height: int = 854,
        fps: int = 25,
        bit_rate: int = 1_200_000,
        gop: int = 50,
    ):
        self.width = width
        self.height = height
        self._closed = False
        self._pts = 0

        # libx264 写模式 CodecContext。Annex-B 是 libx264 裸流默认格式，
        # tune=zerolatency 关 B 帧与前瞻（一帧进一帧出，PTS 严格对应），
        # repeat-headers 让每个 IDR 自带 SPS/PPS（in-band），便于客户端随时接入/恢复。
        self.ctx = av.CodecContext.create("libx264", "w")
        self.ctx.width = width
        self.ctx.height = height
        self.ctx.pix_fmt = "yuv420p"
        self.ctx.time_base = Fraction(1, fps)
        self.ctx.bit_rate = bit_rate
        self.ctx.gop_size = gop
        self.ctx.max_b_frames = 0
        self.ctx.options = {
            "preset": "veryfast",
            "tune": "zerolatency",
            "repeat-headers": "1",
            "g": str(gop),
        }

    def _packets_to_aus(self, packets) -> List[Tuple[bytes, bool]]:
        out: List[Tuple[bytes, bool]] = []
        for packet in packets:
            data = bytes(packet)
            if not data:
                continue
            out.append((data, bool(packet.is_keyframe)))
        return out

    def encode_frame(self, tensor: torch.Tensor) -> List[Tuple[bytes, bool]]:
        """编码一帧，返回 [(annexb_bytes, is_keyframe), ...]（zerolatency 下通常恰好 1 个）。

        tensor: (3, H, W) uint8 RGB，可在 cuda 上。
        """
        if self._closed:
            raise RuntimeError("encoder already closed")

        # (3,H,W) RGB CHW -> (H,W,3) HWC RGB numpy
        arr = tensor.detach().permute(1, 2, 0).contiguous().cpu().numpy()
        frame = av.VideoFrame.from_ndarray(arr, format="rgb24")  # PyAV 内部转 yuv420p
        frame.pts = self._pts
        frame.time_base = self.ctx.time_base
        self._pts += 1
        return self._packets_to_aus(self.ctx.encode(frame))

    def close(self) -> List[Tuple[bytes, bool]]:
        """flush 编码器残留包；幂等，重复调用返回 []。"""
        if self._closed:
            return []
        self._closed = True
        try:
            return self._packets_to_aus(self.ctx.encode(None))
        except Exception as e:  # noqa: BLE001
            logger.warning(f"[h264] flush 失败: {e}")
            return []
