"""
TensorRT UNet 推理模块。

用 TRT engine 替代 PyTorch UNet forward，预期加速 2-3 倍。
"""

import logging
from pathlib import Path
from typing import Optional

import numpy as np
import torch

logger = logging.getLogger(__name__)


class TRTOutput:
    """模拟 diffusers UNet2DConditionOutput，使 TRT 输出兼容 PyTorch 接口。"""
    __slots__ = ("sample",)

    def __init__(self, sample: torch.Tensor):
        self.sample = sample


class TRTUNet:
    """TensorRT UNet 推理引擎。

    加载预构建的 TRT engine 文件，提供与 PyTorch UNet forward 相同的接口。
    输入输出均为 PyTorch CUDA tensor，无缝对接现有 pipeline。
    """

    def __init__(self, engine_path: str, device: str = "cuda"):
        """初始化 TRT UNet。

        Args:
            engine_path: TRT engine 文件路径（.engine）
            device: CUDA 设备
        """
        import tensorrt as trt

        self.device = device
        self.logger = trt.Logger(trt.Logger.WARNING)

        # 加载 engine
        engine_path = Path(engine_path)
        if not engine_path.exists():
            raise FileNotFoundError(f"TRT engine 文件不存在: {engine_path}")

        logger.info(f"[trt] 正在加载 TensorRT engine: {engine_path}")
        runtime = trt.Runtime(self.logger)
        with open(engine_path, "rb") as f:
            self.engine = runtime.deserialize_cuda_engine(f.read())

        if self.engine is None:
            raise RuntimeError(f"TRT engine 反序列化失败: {engine_path}")

        self.context = self.engine.create_execution_context()

        # 获取输入输出 tensor 信息
        self._setup_bindings()

        # 创建 CUDA stream
        self.stream = torch.cuda.Stream(device=device)

        logger.info(
            f"[trt] TensorRT UNet 已加载 | "
            f"输入: sample={self.input_sample_shape}, enc={self.input_enc_shape} | "
            f"输出: {self.output_shape}"
        )

    def _setup_bindings(self):
        """设置 input/output tensor 的 binding 和预分配 buffer。"""
        import tensorrt as trt

        # 解析 engine 的输入输出
        self.input_names = []
        self.output_names = []

        for i in range(self.engine.num_io_tensors):
            name = self.engine.get_tensor_name(i)
            mode = self.engine.get_tensor_mode(name)
            if mode == trt.TensorIOMode.INPUT:
                self.input_names.append(name)
            else:
                self.output_names.append(name)

        # 获取 tensor shape 和 dtype
        # 注意：TRT 10.x API 使用 get_tensor_shape / get_tensor_dtype
        sample_name = "sample"
        enc_name = "encoder_hidden_states"
        output_name = "output"

        # 如果名字不匹配，尝试按顺序
        if sample_name not in self.input_names:
            sample_name = self.input_names[0]
        if enc_name not in self.input_names:
            enc_name = self.input_names[1] if len(self.input_names) > 1 else self.input_names[0]
        if output_name not in self.output_names:
            output_name = self.output_names[0]

        self.sample_name = sample_name
        self.enc_name = enc_name
        self.output_name = output_name

        # 获取 shape（动态维度 -1 需要替换为实际 batch_size）
        raw_sample_shape = list(self.engine.get_tensor_shape(sample_name))
        raw_enc_shape = list(self.engine.get_tensor_shape(enc_name))
        raw_output_shape = list(self.engine.get_tensor_shape(output_name))

        # 记录原始 shape（保留 -1 表示动态维度）
        self.raw_sample_shape = raw_sample_shape
        self.raw_enc_shape = raw_enc_shape
        self.raw_output_shape = raw_output_shape
        self.batch_size = -1  # 标记尚未分配

        # 确定 dtype（TRT 通常使用 FP16）
        dtype_map = {
            "Float32": torch.float32,
            "Half": torch.float16,
            "Float16": torch.float16,
        }
        sample_dtype_str = str(self.engine.get_tensor_dtype(sample_name))
        self.dtype = dtype_map.get(sample_dtype_str, torch.float16)

        logger.debug(f"[trt] sample dtype: {sample_dtype_str} -> {self.dtype}")

        # buffer 延迟到第一次 __call__ 时按实际 batch 分配
        self.input_sample_buf = None
        self.input_enc_buf = None
        self.output_buf = None

    def __call__(self, sample: torch.Tensor, timestep, encoder_hidden_states: torch.Tensor, return_dict: bool = True) -> torch.Tensor:
        """执行 TRT UNet 推理。

        接口与 PyTorch UNet forward 一致：
            input:  sample (B, 8, 32, 32) fp16, encoder_hidden_states (B, 5, 384) fp16
            output: (B, 4, 32, 32) fp16

        Args:
            sample: 潜在特征 tensor，形状 (B, 8, 32, 32)
            encoder_hidden_states: 音频特征 tensor，形状 (B, seq_len, 384)

        Returns:
            预测的潜在特征 tensor，形状 (B, 4, 32, 32)
        """
        # 动态 batch：首次调用或 batch 变化时重新分配 buffer
        actual_b = sample.shape[0]
        if actual_b != self.batch_size:
            self.batch_size = actual_b
            self.input_sample_shape = tuple(actual_b if d < 0 else d for d in self.raw_sample_shape)
            self.input_enc_shape = tuple(actual_b if d < 0 else d for d in self.raw_enc_shape)
            self.output_shape = tuple(actual_b if d < 0 else d for d in self.raw_output_shape)

            self.input_sample_buf = torch.empty(self.input_sample_shape, dtype=self.dtype, device=self.device)
            self.input_enc_buf = torch.empty(self.input_enc_shape, dtype=self.dtype, device=self.device)
            self.output_buf = torch.empty(self.output_shape, dtype=self.dtype, device=self.device)

            self.context.set_tensor_address(self.sample_name, self.input_sample_buf.data_ptr())
            self.context.set_tensor_address(self.enc_name, self.input_enc_buf.data_ptr())
            self.context.set_tensor_address(self.output_name, self.output_buf.data_ptr())

        # 将输入数据拷贝到预分配的 buffer
        if sample.dtype == self.dtype and sample.device.type == "cuda":
            self.input_sample_buf.copy_(sample)
        else:
            self.input_sample_buf.copy_(sample.to(dtype=self.dtype, device=self.device))

        if encoder_hidden_states.dtype == self.dtype and encoder_hidden_states.device.type == "cuda":
            self.input_enc_buf.copy_(encoder_hidden_states)
        else:
            self.input_enc_buf.copy_(
                encoder_hidden_states.to(dtype=self.dtype, device=self.device)
            )

        # 设置动态输入 shape（TRT 需要知道实际 batch size）
        self.context.set_input_shape(self.sample_name, tuple(sample.shape))
        self.context.set_input_shape(self.enc_name, tuple(encoder_hidden_states.shape))

        # 执行推理（异步）
        success = self.context.execute_async_v3(stream_handle=self.stream.cuda_stream)

        if not success:
            raise RuntimeError("TRT 推理执行失败")

        # 同步（确保推理完成）
        self.stream.synchronize()

        # 返回输出（克隆以避免 buffer 被覆盖）
        # 包装为 TRTOutput 以兼容 diffusers UNet2DConditionOutput 接口
        return TRTOutput(self.output_buf.clone())

    def __del__(self):
        """释放 TRT 资源。"""
        try:
            del self.context
            del self.engine
        except Exception:
            pass


def find_trt_engine(musetalk_root: Path) -> Optional[Path]:
    """查找可用的 TRT engine 文件。

    按优先级搜索：
    1. models/musetalk/unet_fp16.engine
    2. models/musetalk/unet.engine

    Args:
        musetalk_root: MuseTalk 根目录

    Returns:
        engine 文件路径，未找到返回 None
    """
    candidates = [
        musetalk_root / "models/musetalk/unet_fp16.engine",
        musetalk_root / "models/musetalk/unet.engine",
    ]
    for path in candidates:
        if path.exists():
            return path
    return None
