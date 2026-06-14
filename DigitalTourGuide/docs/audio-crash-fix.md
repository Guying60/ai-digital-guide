# AudioTrack Native Crash 问题分析与修复

## 现象

应用在播放音视频流时发生 native crash，崩溃栈指向 `AVSyncPlayer.audioPlayLoop`：

```
#11 pc 0000000000006e38  classes8.dex (AVSyncPlayer.audioPlayLoop+0)
#16 pc 0000000000006c00  classes8.dex (AVSyncPlayer.$r8$lambda$...+0)
#21 pc 0000000000006900  classes8.dex (AVSyncPlayer$$ExternalSyntheticLambda1.run+0)
```

crash_dump64 进程（pid 29790）捕获了来自应用进程（pid 24539）的 native 信号。

---

## 根因

### 音频 chunk 过大导致 write 长时间阻塞

后端发送的音频 PCM 数据 chunk 过大：

| 指标 | 值 | 说明 |
|------|-----|------|
| pcmLen=55040 | 1.72 秒音频 | 16kHz/16bit/mono |
| pcmLen=78080 | 2.44 秒音频 | 16kHz/16bit/mono |
| write 耗时 | 1890ms / 2978ms | AudioTrack.write() 阻塞到播放完才返回 |

### 竞态条件：write 阻塞期间 AudioTrack 被销毁

时序：

```
音频线程 (av-audio)              主线程/回调线程
─────────────────                ──────────────
audioTrack.write(78080 bytes)
  ↓ 阻塞 2.978 秒...
                                 interrupt() 或 onConversationEnd()
                                   audioQueue.clear()
                                   videoQueue.clear()
                                   releaseDecoder()
                                   audioTrack.release()  ← 销毁 native 对象！
  ↓ write 返回
  ↓ 访问已销毁的 AudioTrack
  ↓ SIGSEGV / native crash
```

`released = true` 在 `audioTrack.release()` 之前设置，但音频线程卡在阻塞 `write()` 里无法检查该标志。

### 触发场景

- 用户发送新消息（触发 `interrupt()`）
- 对话结束（触发 `onConversationEnd()`）
- Activity 销毁（触发 `release()`）

三种路径都存在相同的竞态问题。

---

## 修复方案

### 核心思路：先解除 write 阻塞，再销毁对象

```
pause() + flush()  →  解除 write() 阻塞
       ↓
audioThread.interrupt() + join(500)  →  等待音频线程退出
       ↓
audioTrack.stop() + release()  →  安全销毁
```

### 代码变更

#### 1. 新增 audioThread 引用

```java
private volatile Thread audioThread;  // 用于安全停止音频线程
```

#### 2. audioPlayLoop 保存线程引用 + 异常保护

```java
private void audioPlayLoop() {
    audioThread = Thread.currentThread();  // ← 保存引用
    while (!released) {
        byte[] pkt = audioQueue.poll();
        if (released) break;               // ← poll 后再检查
        if (pkt.length > AUDIO_HDR) {
            byte[] pcm = Arrays.copyOfRange(pkt, AUDIO_HDR, pkt.length);
            try {
                audioTrack.write(pcm, 0, pcm.length);
            } catch (IllegalStateException e) {  // ← 异常保护
                break;
            }
        }
    }
    audioThread = null;
}
```

#### 3. release() / interrupt() / onConversationEnd() 统一安全停止

```java
// 1) 停止 AudioTrack，解除 write() 阻塞
try { audioTrack.pause(); } catch (Exception ignored) {}
try { audioTrack.flush(); } catch (Exception ignored) {}

// 2) 等待音频线程退出
Thread t = audioThread;
if (t != null) {
    t.interrupt();
    try { t.join(500); } catch (InterruptedException ignored) {}
}

// 3) 此时才安全销毁
try { audioTrack.stop(); } catch (Exception ignored) {}
try { audioTrack.release(); } catch (Exception ignored) {}
```

---

## 日志观察到的其他问题

### 收帧突发，队列持续积压

```
onVideoData: interval=569ms → burst 20帧 (interval=0ms)
onVideoData: interval=732ms → burst
onVideoData: interval=465ms → burst
videoQueue: 25 → 86（持续增长，从未清空）
```

后端不是按稳 25fps 推送，而是攒一批帧通过 WebSocket 一次性推过来。解码器按 40ms/帧 消费，但突发推入远超消费速度。

**影响**：内存占用持续增长，首帧到渲染的延迟逐渐加大。

**建议**：后端改为稳速推送，或前端对 videoQueue 设上限（如 30 帧），超出时丢弃旧帧。

### 解码本身无瓶颈

`dequeueInputBuffer`、`dequeueOutputBuffer`、`queueInputBuffer`、`releaseOutputBuffer` 的耗时均在正常范围内（<10ms），MediaCodec 硬解码不是瓶颈。
