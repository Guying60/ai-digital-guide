# MuseTalk GPU 推理架构升级记录

> 日期：2026-06-08
> 基于：`docs/GPU推理性能与架构现状.md` 分析结论
> 目标：提升 GPU 利用率，减少句间串行等待

---

## 一、问题诊断

### 1.1 核心瓶颈

| 问题 | 现状 | 影响 |
|---|---|---|
| 句间严格串行 | MuseTalk 单队列 + `engine._lock` 全局互斥 | GPU 利用率仅 30-50% |
| audio2feat 延迟 | 等前一句 GPU 推理完成后才开始 CPU 侧 Whisper | 浪费 sm% 12-25% 的空档 |
| H.264 编码阻塞 | libx264 在推理协程内同步执行 | GPU 等待 CPU 编码 |
| 缺少量化指标 | 只能靠肉眼读 `nvidia-smi dmon` | 无法精确调优 |

### 1.2 GPU 利用率分析（5090 实测）

```
sm% 脉冲式：0% → 40-72% → 100% → 12-25% → 再 100% → 0%
平均利用率：30-50%
峰值 100%：TTS 与 MuseTalk 在同一时刻争抢 SM
低谷 12-25%：两边均不在跑重 kernel（等 Whisper/H.264/发包）
```

---

## 二、优化方案

### 2.1 流水线重叠 — audio2feat 预计算

**原理**：`audio2feat` 是纯 CPU 操作（Whisper-tiny），不占 GPU。在前一句 GPU 推理期间提前为下一句做 audio2feat，省去串行等待。

**改动文件**：

- `services/musetalk_engine.py`
- `api/ws_routes.py`

**改动详情**：

**musetalk_engine.py** — 新增公开方法：

```python
def precompute_audio_chunks(self, audio_chunk: bytes):
    """公开方法：预计算 whisper chunks（纯 CPU，不占 GPU）。

    用于流水线重叠：在前一句 GPU 推理期间，提前为下一句做 audio2feat。
    可安全在 asyncio.to_thread 或 ThreadPoolExecutor 中调用。
    """
    return self._audio_to_chunks(audio_chunk)
```

**musetalk_engine.py** — 修改 `generate_frames` 签名：

```python
def generate_frames(self, audio_chunk: bytes, attraction_id: str, precomputed_chunks=None):
    # ...
    # 1) audio2feat：若已预计算则直接使用，否则丢到后台线程跑
    if precomputed_chunks is not None:
        whisper_chunks = precomputed_chunks
    else:
        chunks_future = self._audio_executor.submit(self._audio_to_chunks, audio_chunk)
    # ...
    if precomputed_chunks is None:
        whisper_chunks = chunks_future.result()
```

**ws_routes.py** — `audio_end` 处理时立即启动预计算：

```python
if msg.get("type") == "audio_end":
    full_audio = bytes(audio_buffer)
    audio_buffer.clear()
    sentence_id: int = msg.get("sentence_id", 0)

    # 流水线重叠：立即在后台线程启动 audio2feat 预计算（纯 CPU，不占 GPU），
    # 这样前一句 GPU 推理完成后可直接进入 UNet，省去 audio2feat 等待。
    chunks_future = asyncio.to_thread(
        engine.precompute_audio_chunks, full_audio
    )
    await inference_queue.put(
        (chunks_future, len(full_audio), attraction_id, sentence_id)
    )
```

**ws_routes.py** — `inference_worker` 等待预计算结果：

```python
chunks_future_or_list, full_audio_len, attr_id, sentence_id = task_data

# 等待预计算的 audio2feat 结果（大部分情况下已在后台完成）
t_audio_start = time.monotonic()
precomputed_chunks = await chunks_future_or_list
t_audio_done = time.monotonic()

# ... 传入 generate_frames
for blended_list in engine.generate_frames(b"", attr_id, precomputed_chunks=precomputed_chunks):
```

**时序对比**：

```
优化前：
  句N GPU推理 ████████████████ | audio2feat(句N+1) ██ | 句N+1 GPU推理 ████████████████

优化后：
  句N GPU推理 ████████████████ | 句N+1 GPU推理 ████████████████
                   audio2feat(句N+1) ██ ↑ 与句N GPU推理重叠
```

---

### 2.2 增大 BATCH（32 → 48）

**原理**：增大单次批处理量，提高 GPU kernel 效率，减少 sm% 抖动。5090 32GB 显存可承受。

**改动文件**：`services/musetalk_engine.py`

**改动详情**：

```python
# 优化前
BATCH = 32

# 优化后
BATCH = 48
```

同步调整 warmup 的 silent_pcm 时长：

```python
# 优化前：25fps × BATCH(32) ≈ 1.28s，给到 2s 保证至少跑满一个 padded batch
silent_pcm = b"\x00\x00" * int(16000 * 2.0)

# 优化后：25fps × BATCH(48) ≈ 1.92s，给到 2.5s 保证至少跑满一个 padded batch
silent_pcm = b"\x00\x00" * int(16000 * 2.5)
```

**显存影响**：峰值额外约 1-2GB，32GB 显存可承受。

---

### 2.3 H.264 编码独立线程池

**原理**：libx264 编码是 CPU 密集操作，当前在推理协程内同步执行，阻塞下一批 UNet 启动。挪到独立线程池可实现编码与 GPU 推理并行。

**改动文件**：`api/ws_routes.py`

**改动详情**：

新增线程池和辅助函数：

```python
from concurrent.futures import ThreadPoolExecutor

# H.264 编码线程池：将 CPU 密集的 libx264 编码从 asyncio 事件循环中卸载，
# 使下一批 GPU 推理（UNet）能与当前批的 H.264 编码并行执行。
_h264_executor = ThreadPoolExecutor(max_workers=2, thread_name_prefix="h264-enc")


def _encode_batch_sync(
    encoder: H264StreamEncoder, blended_list: List
) -> List[tuple]:
    """将一批 blend 后的帧编码为 H.264 access unit（在编码线程池中运行）。

    blended_list 中的 tensor 会被同步拷贝到 CPU 并编码，调用方无需额外持有 CUDA 引用。
    """
    batch_aus: List[tuple] = []
    for tensor in blended_list:
        batch_aus.extend(encoder.encode_frame(tensor))
    return batch_aus
```

重构推理循环：

```python
# 优化前：同步编码，阻塞 GPU
pending_aus: Optional[List[tuple]] = None
for blended_list in engine.generate_frames(...):
    if pending_aus is not None:
        await _send_h264(ws, pending_aus, ...)
    # 同步编码（阻塞）
    batch_aus = []
    for tensor in blended_list:
        batch_aus.extend(encoder.encode_frame(tensor))
    pending_aus = batch_aus

# 优化后：异步编码，与 GPU 推理并行
pending_aus_future: Optional[asyncio.Future] = None
for blended_list in engine.generate_frames(...):
    # 等待上一批编码完成并发送
    if pending_aus_future is not None:
        pending_aus = await pending_aus_future
        await _send_h264(ws, pending_aus, ...)
    # 提交当前批编码到线程池，与下一批 GPU 推理并行
    pending_aus_future = asyncio.wrap_future(
        _h264_executor.submit(_encode_batch_sync, encoder, blended_list)
    )
```

**时序对比**：

```
优化前：
  UNet(批N) ██ | VAE ██ | blend ██ | 编码(批N) ██ | UNet(批N+1) ██ | ...

优化后：
  UNet(批N) ██ | VAE ██ | blend ██ | UNet(批N+1) ██ | ...
                              编码(批N) ██ ↑ 与 UNet(批N+1) 重叠
```

---

### 2.4 性能日志

**原理**：量化每句推理的各阶段耗时，便于精确调优。

**改动文件**：`api/ws_routes.py`

**改动详情**：

```python
# 性能日志：推理耗时 vs 音频时长（实时倍率）
t_infer_done = time.monotonic()
infer_elapsed = t_infer_done - t_audio_start
audio_duration_s = full_audio_len / 2 / 16000  # int16 PCM → 秒
rt_ratio = audio_duration_s / infer_elapsed if infer_elapsed > 0 else float("inf")
logger.info(
    f"[perf] sentence_id={sentence_id} 推理完成 | "
    f"帧数={frame_index} | 音频={audio_duration_s:.2f}s | "
    f"推理耗时={infer_elapsed:.3f}s | "
    f"audio2feat={t_audio_done - t_audio_start:.3f}s | "
    f"实时倍率={rt_ratio:.2f}x | "
    f"队列深度={inference_queue.qsize()}"
)
```

**日志示例**：

```
[perf] sentence_id=1 推理完成 | 帧数=120 | 音频=4.80s | 推理耗时=2.15s | audio2feat=0.32s | 实时倍率=2.23x | 队列深度=0
```

**判读标准**：

| 指标 | 目标 | 含义 |
|---|---|---|
| 实时倍率 | > 1.5x | 单路有余量，可应对突发 |
| 实时倍率 | < 1.0x | 推理慢于实时，用户会感知卡顿 |
| audio2feat | < 0.5s | CPU 侧预计算正常 |
| 队列深度 | = 0 | 无积压，串行等待已消除 |

---

## 三、涉及文件索引

| 文件 | 改动类型 | 说明 |
|---|---|---|
| `MuseTalk/services/musetalk_engine.py` | 修改 | BATCH 增大、新增 `precompute_audio_chunks`、`generate_frames` 支持预计算 |
| `MuseTalk/api/ws_routes.py` | 修改 | 流水线重叠、H.264 线程池、性能日志 |
| `MuseTalk/utils/h264_encoder.py` | 未改动 | 线程安全，可直接在线程池中使用 |

---

## 四、验证方式

### 4.1 日志验证

启动服务后，通过 Android 客户端发送连续多句对话，观察日志：

```bash
# 查看性能日志
grep "[perf]" /path/to/musetalk.log
```

### 4.2 GPU 监控

```bash
# 持续监控 GPU 算力 / 显存 / 功耗
nvidia-smi dmon -s pucvmet -d 1

# 每秒刷新汇总
watch -n 1 'nvidia-smi --query-gpu=utilization.gpu,memory.used,memory.total,power.draw --format=csv'
```

### 4.3 对比指标

| 指标 | 优化前 | 优化后目标 |
|---|---|---|
| 平均 sm% | 30-50% | 50-70% |
| 句间空档 | 明显（audio2feat 等待） | 基本消除 |
| 实时倍率 | 未量化 | > 1.5x |
| sm% 波动 | 脉冲式大幅抖动 | 更平稳 |

---

## 五、待验证项

- [ ] 实测 `BATCH=48` 对 5090 显存峰值的影响
- [ ] 量化优化后的实时倍率（目标 > 1.5x）
- [ ] 重叠段 sm% 均值与 P95 对比
- [ ] 多用户并发场景下的压测（若业务需要）
- [ ] 评估 `BATCH=64` 的可行性（需实测显存）
