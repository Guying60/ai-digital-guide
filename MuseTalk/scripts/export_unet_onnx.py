#!/usr/bin/env python3
"""
将 MuseTalk UNet 导出为 ONNX 格式，用于 TensorRT 加速。

用法：
    python scripts/export_unet_onnx.py [--batch-size 48] [--output models/musetalk/unet.onnx]

前提：
    - MUSETALK_ROOT 环境变量指向 MuseTalk 根目录（或使用默认值）
    - 模型文件已下载到 MUSETALK_ROOT/models/
"""

import argparse
import json
import os
import sys
from pathlib import Path

import torch

# 将 MuseTalk 根目录加入 sys.path
MUSETALK_ROOT = Path(os.getenv("MUSETALK_ROOT", "/root/autodl-tmp/MuseTalk"))
if str(MUSETALK_ROOT) not in sys.path:
    sys.path.insert(0, str(MUSETALK_ROOT))


class UNetOnnxWrapper(torch.nn.Module):
    """将 diffusers UNet2DConditionModel 包装为 ONNX 可导出的形式。

    UNet2DConditionModel.forward(sample, timestep, encoder_hidden_states) 的签名
    包含复杂的内部逻辑（cross-attention 等），直接导出可能遇到问题。
    此 wrapper 将 timestep 固定为 0，简化导出。
    """

    def __init__(self, unet_model):
        super().__init__()
        self.unet = unet_model

    def forward(self, sample, encoder_hidden_states):
        # timestep 固定为 0（MuseTalk 是单步推理，非扩散模型）
        timestep = torch.zeros(sample.shape[0], dtype=torch.long, device=sample.device)
        output = self.unet(sample, timestep, encoder_hidden_states=encoder_hidden_states)
        return output.sample


def main():
    parser = argparse.ArgumentParser(description="Export MuseTalk UNet to ONNX")
    parser.add_argument("--batch-size", type=int, default=48, help="Batch size (default: 48)")
    parser.add_argument(
        "--output",
        type=str,
        default=str(MUSETALK_ROOT / "models/musetalk/unet.onnx"),
        help="Output ONNX file path",
    )
    parser.add_argument(
        "--unet-config",
        type=str,
        default=str(MUSETALK_ROOT / "models/musetalk/musetalk.json"),
        help="UNet config JSON path",
    )
    parser.add_argument(
        "--unet-weights",
        type=str,
        default=str(MUSETALK_ROOT / "models/musetalk/pytorch_model.bin"),
        help="UNet weights path",
    )
    parser.add_argument("--fp16", action="store_true", default=True, help="Use FP16 (default: True)")
    parser.add_argument("--seq-len", type=int, default=50, help="Audio sequence length (default: 50, = 10 frames * 5 layers)")
    args = parser.parse_args()

    device = "cuda" if torch.cuda.is_available() else "cpu"
    dtype = torch.float16 if args.fp16 else torch.float32

    print(f"MuseTalk Root: {MUSETALK_ROOT}")
    print(f"UNet Config: {args.unet_config}")
    print(f"UNet Weights: {args.unet_weights}")
    print(f"Batch Size: {args.batch_size}")
    print(f"Sequence Length: {args.seq_len}")
    print(f"Dtype: {dtype}")
    print(f"Device: {device}")

    # 1) 加载 UNet 模型（直接加载，不经过 load_all_model 避免 VAE 下载）
    print("\n正在加载 UNet 模型...")
    from diffusers import UNet2DConditionModel

    with open(args.unet_config, "r") as f:
        unet_config = json.load(f)

    model = UNet2DConditionModel(**unet_config)
    checkpoint = torch.load(args.unet_weights, map_location="cpu")
    # 兼容不同保存格式：state_dict 直接加载，或从完整 checkpoint 中提取
    if isinstance(checkpoint, dict) and "state_dict" in checkpoint:
        state_dict = checkpoint["state_dict"]
    elif isinstance(checkpoint, dict) and "model" in checkpoint:
        state_dict = checkpoint["model"]
    else:
        state_dict = checkpoint
    model.load_state_dict(state_dict, strict=False)

    if args.fp16:
        model = model.half()
    model = model.to(device).eval()

    print(f"UNet 模型加载完成，in_channels={model.config.in_channels}")

    # 2) 包装为 ONNX 可导出形式
    wrapper = UNetOnnxWrapper(model).to(device).eval()

    # 3) 创建 dummy inputs
    B = args.batch_size
    in_channels = model.config.in_channels  # 通常是 8
    cross_attention_dim = model.config.cross_attention_dim  # 384

    dummy_sample = torch.randn(B, in_channels, 32, 32, dtype=dtype, device=device)
    dummy_encoder = torch.randn(B, args.seq_len, cross_attention_dim, dtype=dtype, device=device)

    print(f"\nInput shapes:")
    print(f"  sample: {dummy_sample.shape} {dummy_sample.dtype}")
    print(f"  encoder_hidden_states: {dummy_encoder.shape} {dummy_encoder.dtype}")

    # 4) 验证 wrapper 输出
    with torch.no_grad():
        output = wrapper(dummy_sample, dummy_encoder)
    print(f"  output: {output.shape} {output.dtype}")

    # 5) 导出 ONNX
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    print(f"\n正在导出 ONNX 到 {output_path}...")
    torch.onnx.export(
        wrapper,
        (dummy_sample, dummy_encoder),
        str(output_path),
        opset_version=17,
        input_names=["sample", "encoder_hidden_states"],
        output_names=["output"],
        dynamic_axes={
            "sample": {0: "batch"},
            "encoder_hidden_states": {0: "batch"},
            "output": {0: "batch"},
        },
        do_constant_folding=True,
    )

    print(f"✅ ONNX 导出完成: {output_path}")
    print(f"   文件大小: {output_path.stat().st_size / 1024 / 1024:.1f} MB")

    # 6) 验证 ONNX 模型
    print("\n正在验证 ONNX 模型...")
    try:
        import onnx

        onnx_model = onnx.load(str(output_path))
        onnx.checker.check_model(onnx_model)
        print("✅ ONNX 模型验证通过")
    except ImportError:
        print("⚠️  onnx 包未安装，跳过验证（pip install onnx）")
    except Exception as e:
        print(f"❌ ONNX 模型验证失败: {e}")

    print(f"\n下一步：使用 trtexec 构建 TensorRT engine:")
    print(f"  trtexec --onnx={output_path} --saveEngine={output_path.with_suffix('.engine')} --fp16")


if __name__ == "__main__":
    main()
