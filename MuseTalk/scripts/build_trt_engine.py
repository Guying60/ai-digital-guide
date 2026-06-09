#!/usr/bin/env python3
"""
从 ONNX 构建 TensorRT FP16 engine。

用法：
    python scripts/build_trt_engine.py \
        --onnx models/musetalk/unet.onnx \
        --output models/musetalk/unet.engine \
        --min-batch 1 --opt-batch 48 --max-batch 64
"""

import argparse
import sys
from pathlib import Path

try:
    import tensorrt as trt
except ImportError:
    print("错误：需要安装 tensorrt-cu12。在 conda env mt 中：")
    print("  pip install tensorrt-cu12")
    sys.exit(1)


def build_engine(
    onnx_path: str,
    engine_path: str,
    min_batch: int = 1,
    opt_batch: int = 48,
    max_batch: int = 64,
    fp16: bool = True,
    workspace_mb: int = 4096,
):
    logger = trt.Logger(trt.Logger.WARNING)
    builder = builder = trt.Builder(logger)
    # TRT 10.x 默认 explicit batch，无需设置 flag
    network = builder.create_network()
    parser = trt.OnnxParser(network, logger)

    # 解析 ONNX
    print(f"正在解析 ONNX: {onnx_path}")
    with open(onnx_path, "rb") as f:
        if not parser.parse(f.read()):
            for i in range(parser.num_errors):
                print(f"  ONNX 解析错误: {parser.get_error(i)}")
            sys.exit(1)
    print("  ONNX 解析成功")

    # 打印输入输出信息
    for i in range(network.num_inputs):
        t = network.get_input(i)
        print(f"  输入 {i}: name={t.name}, shape={t.shape}, dtype={t.dtype}")
    for i in range(network.num_outputs):
        t = network.get_output(i)
        print(f"  输出 {i}: name={t.name}, shape={t.shape}, dtype={t.dtype}")

    # 配置 builder
    config = builder.create_builder_config()
    config.set_memory_pool_limit(trt.MemoryPoolType.WORKSPACE, workspace_mb * 1024 * 1024)
    if fp16:
        if hasattr(trt.BuilderFlag, "FP16"):
            config.set_flag(trt.BuilderFlag.FP16)
        else:
            # TRT 10.x: 部分版本 FP16 默认启用，或通过 builder 直接设置
            try:
                builder.fp16_mode = True
            except AttributeError:
                pass
        print("  启用 FP16")

    # 设置动态 batch 维度的 optimization profile
    profile = builder.create_optimization_profile()

    # sample 输入: (B, 8, 32, 32)
    sample_name = network.get_input(0).name
    profile.set_shape(
        sample_name,
        (min_batch, 8, 32, 32),   # min
        (opt_batch, 8, 32, 32),   # opt
        (max_batch, 8, 32, 32),   # max
    )

    # encoder_hidden_states 输入: (B, 50, 384)
    enc_name = network.get_input(1).name
    profile.set_shape(
        enc_name,
        (min_batch, 50, 384),
        (opt_batch, 50, 384),
        (max_batch, 50, 384),
    )

    # timestep 输入: 标量 () — 如果存在
    if network.num_inputs > 2:
        ts_name = network.get_input(2).name
        ts_shape = tuple(network.get_input(2).shape)
        profile.set_shape(ts_name, ts_shape, ts_shape, ts_shape)

    config.add_optimization_profile(profile)

    # 构建 engine
    print(f"\n正在构建 TRT engine (batch: {min_batch}/{opt_batch}/{max_batch})...")
    serialized = builder.build_serialized_network(network, config)
    if serialized is None:
        print("错误：engine 构建失败")
        sys.exit(1)

    # 保存
    out = Path(engine_path)
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "wb") as f:
        f.write(serialized)
    size_mb = out.stat().st_size / 1024 / 1024
    print(f"\nTRT engine 已保存: {out} ({size_mb:.1f} MB)")


def main():
    parser = argparse.ArgumentParser(description="Build TensorRT engine from ONNX")
    parser.add_argument("--onnx", type=str, default="models/musetalk/unet.onnx")
    parser.add_argument("--output", type=str, default="models/musetalk/unet.engine")
    parser.add_argument("--min-batch", type=int, default=1)
    parser.add_argument("--opt-batch", type=int, default=48)
    parser.add_argument("--max-batch", type=int, default=64)
    parser.add_argument("--fp16", action="store_true", default=True)
    parser.add_argument("--workspace-mb", type=int, default=4096)
    args = parser.parse_args()

    build_engine(
        onnx_path=args.onnx,
        engine_path=args.output,
        min_batch=args.min_batch,
        opt_batch=args.opt_batch,
        max_batch=args.max_batch,
        fp16=args.fp16,
        workspace_mb=args.workspace_mb,
    )


if __name__ == "__main__":
    main()
