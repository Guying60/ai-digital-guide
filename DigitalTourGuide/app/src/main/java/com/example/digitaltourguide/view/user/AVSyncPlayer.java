package com.example.digitaltourguide.view.user;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import com.example.digitaltourguide.BuildConfig;

import android.graphics.Matrix;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class AVSyncPlayer {

    private static final String TAG = "AVSyncPlayer";
    private static final boolean LOG = BuildConfig.DEBUG;

    private static final int AUDIO_SAMPLE_RATE = 16000;
    private static final int AUDIO_CHANNEL = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    // MuseTalk server: 25fps → 40ms per frame
    private static final long FRAME_INTERVAL_MS = 40;
    // Data format (type byte NO LONGER stripped by ChatActivity — B1 optimization):
    // Audio: [typeFlag:1B][sentenceId:2B][ptsMs:4B][PCM:N]  → header = 7 bytes
    // Video: [typeFlag:1B][sentenceId:2B][ptsMs:4B][keyFrame:1B][H.264:N] → header = 8 bytes
    private static final int AUDIO_HDR = 7;
    private static final int VIDEO_HDR = 8;
    public static final int VIDEO_WIDTH = 720;
    public static final int VIDEO_HEIGHT = 1280;

    private final TextureView textureView;
    private Consumer<String> subtitleCallback;

    private final AudioTrack audioTrack;
    private final Object surfaceLock = new Object();  // guards surface field + wait/notify
    private volatile Surface surface;
    private volatile MediaCodec h264Decoder;

    private final LinkedBlockingQueue<byte[]> videoQueue = new LinkedBlockingQueue<>();
    private final AtomicInteger videoQueueSize = new AtomicInteger(0);  // O(1) 快照，避免 reader 线程调 size() O(n)
    private final ConcurrentLinkedQueue<byte[]> audioQueue = new ConcurrentLinkedQueue<>();

    private volatile boolean released = false;
    private volatile boolean playbackStarted = false;  // 播放线程是否已启动
    private volatile boolean firstSentenceBuffered = false;  // 首句预缓冲是否完成
    private volatile long generation = 0;  // 对话代际计数器，用于安全重启播放线程

    // ---- 帧接收 & 解码耗时统计 ----
    private long lastVideoRecvTime = 0;
    private long lastAudioRecvTime = 0;
    private int videoRecvCount = 0;
    private int audioRecvCount = 0;
    // WebSocket reader 线程级别的到达时间（区分 WS 层延迟 vs enqueueExecutor 层延迟）
    private long lastWsVideoTime = 0;
    private int wsVideoCount = 0;
    private long audioRecvTotalBytes = 0;   // 累计收到的音频 PCM 字节数（不含 header），用于诊断突发到达

    // ---- 跨线程同步诊断字段（日志分析音画不同步） ----
    private volatile long diagAudioPtsMs = 0;
    private volatile long diagAudioGlobalPtsMs = 0;
    private volatile long diagAudioWallMs = 0;
    private volatile long diagVideoFeedPtsMs = 0;
    private volatile long diagVideoFeedGlobalPtsMs = 0;
    private volatile long diagVideoFeedWallMs = 0;
    private volatile long diagVideoRenderPtsMs = 0;
    private volatile long diagVideoRenderWallMs = 0;

    // ★ decoderExecutor 已移除：videoFeedLoop 改为独立线程（Thread），
    // 避免单线程 executor 导致 startPlayback 被旧 videoFeedLoop 阻塞（Bug 2）。
    // ★ B2: 独立入队线程 — 将帧处理从 WebSocket reader 线程完全卸载，
    // 确保 reader 能全速从 TCP buffer 读取数据，避免帧堆积在 OkHttp/TCP 缓冲区
    private final ThreadPoolExecutor enqueueExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    // ★ 诊断：enqueueExecutor 积压追踪
    private final AtomicInteger enqSubmitted = new AtomicInteger(0);
    private final AtomicInteger enqCompleted = new AtomicInteger(0);
    private volatile Thread audioThread;  // 用于安全停止音频线程
    private volatile Thread videoThread;  // videoFeedLoop 线程引用，用于安全退出
    // ★ 音画同步：视频基于音频播放位置做节流，防止视频跑在音频前面（Bug 1）
    private volatile long audioPlaybackPtsMs = 0;   // 音频当前播放位置的估计 PTS
    private volatile boolean audioStarted = false;   // 首次音频写入后置 true，videoFeedLoop 启动阶段检查
    private final java.util.concurrent.atomic.AtomicBoolean startPlaybackRunning =
            new java.util.concurrent.atomic.AtomicBoolean(false);  // 防重复启动（Bug 2）

    private final byte[] silenceFrame;  // 预分配的静音帧，用于填充缓冲区空隙
    private final int audioBufSize;     // AudioTrack 缓冲区大小（字节）

    public AVSyncPlayer(TextureView textureView) {
        this.textureView = textureView;
        int minBufSize = AudioTrack.getMinBufferSize(AUDIO_SAMPLE_RATE, AUDIO_CHANNEL, AUDIO_FORMAT);
        // 2 倍最小缓冲区，避免 AudioTrack underrun，同时防止 write() 阻塞过久
        audioBufSize = minBufSize * 2;
        audioTrack = new AudioTrack(
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                new AudioFormat.Builder()
                        .setSampleRate(AUDIO_SAMPLE_RATE)
                        .setChannelMask(AUDIO_CHANNEL)
                        .setEncoding(AUDIO_FORMAT)
                        .build(),
                audioBufSize, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
        // 预分配 40ms 静音帧（16000Hz × 2B × 40ms = 1280B）
        silenceFrame = new byte[AUDIO_SAMPLE_RATE * 2 / 25]; // 25fps → 40ms
    }

    // ====================== PUBLIC API (called by ChatActivity) ======================

    public void setSubtitleCallback(Consumer<String> callback) {
        this.subtitleCallback = callback;
    }

    public void onSurfaceReady(Surface surface) {
        synchronized (surfaceLock) {
            this.surface = surface;
            surfaceLock.notifyAll();  // 唤醒等待 Surface 的 videoFeedLoop
        }
        applyAspectRatio();
        if (LOG) Log.i(TAG, "Surface ready");
    }

    public void onSurfaceDestroyed() {
        synchronized (surfaceLock) {
            this.surface = null;
        }
        // ★ 关键：立即释放解码器，防止 releaseOutputBuffer(true) 继续渲染到已销毁的 Surface
        // 下次 videoFeedLoop 检测到 surface 变为新值后会重建 codec
        releaseDecoder();
        if (LOG) Log.i(TAG, "Surface destroyed");
    }

    public void updateTransform() {
        applyAspectRatio();
    }

    /**
     * Scale TextureView content to maintain video aspect ratio (center-crop).
     */
    private void applyAspectRatio() {
        if (textureView == null) return;
        int viewW = textureView.getWidth();
        int viewH = textureView.getHeight();
        if (viewW == 0 || viewH == 0) return;

        float videoAspect = (float) VIDEO_WIDTH / VIDEO_HEIGHT; // 720/1280 = 0.5625
        float viewAspect = (float) viewW / viewH;

        float scaleX, scaleY;
        if (videoAspect > viewAspect) {
            // Video is wider → fit height, crop width
            scaleY = 1f;
            scaleX = videoAspect / viewAspect;
        } else {
            // Video is taller → fit width, crop height
            scaleX = 1f;
            scaleY = viewAspect / videoAspect;
        }

        Matrix matrix = new Matrix();
        matrix.setScale(scaleX, scaleY, viewW / 2f, viewH / 2f);
        textureView.setTransform(matrix);
        if (LOG) Log.i(TAG, "applyAspectRatio: view=" + viewW + "x" + viewH
                + " video=" + VIDEO_WIDTH + "x" + VIDEO_HEIGHT + " scale=" + scaleX + "x" + scaleY);
    }

    public boolean isPlaying() {
        return playbackStarted;
    }

    /**
     * Audio data: [typeFlag:1B][sentenceId:2B][ptsMs:4B][PCM:N]
     * Type flag is now preserved (P1/B1 optimization).
     */
    private void onAudioData(byte[] frameData, long myGen) {
        if (released || frameData == null || frameData.length <= AUDIO_HDR) return;
        if (generation != myGen) {  // ★ P0: 丢弃旧 gen 帧，防止混入新 gen 播放
            if (LOG) Log.w(TAG, "onAudioData: dropped old-gen frame, myGen=" + myGen + " current=" + generation);
            return;
        }
        audioQueue.offer(frameData);
        if (LOG) {
            long now = System.currentTimeMillis();
            long interval = lastAudioRecvTime == 0 ? 0 : now - lastAudioRecvTime;
            lastAudioRecvTime = now;
            audioRecvCount++;
            int pcmLen = frameData.length - AUDIO_HDR;
            audioRecvTotalBytes += pcmLen;
            long recvDurationMs = audioRecvTotalBytes * 1000L / (AUDIO_SAMPLE_RATE * 2); // 16-bit mono
            // ★ 每个音频包都打日志，观察突发到达模式
            if (interval > 50 || audioRecvCount <= 20 || audioRecvCount % 20 == 0) {
                long ptsMs = ((long)(frameData[3]&0xFF)<<24) | ((long)(frameData[4]&0xFF)<<16)
                           | ((long)(frameData[5]&0xFF)<<8) | (frameData[6]&0xFF);
                Log.w(TAG, "onAudioData: interval=" + interval + "ms pts=" + ptsMs
                        + " pcmLen=" + pcmLen + " q=" + audioQueue.size()
                        + " total=" + audioRecvCount + " recvDur=" + recvDurationMs + "ms");
            }
        }
    }

    /**
     * Video data: [typeFlag:1B][sentenceId:2B][ptsMs:4B][keyFrame:1B][H.264 AU:N]
     * Type flag is now preserved (P1/B1 optimization).
     */
    private static final int VIDEO_QUEUE_MAX = 350;  // 8 秒 burst 上限保护，丢弃最旧帧防内存暴涨

    private void onVideoData(byte[] frameData, long myGen) {
        long tEntry = LOG ? System.currentTimeMillis() : 0;
        if (released || frameData == null || frameData.length <= VIDEO_HDR) return;
        if (generation != myGen) {  // ★ P0: 丢弃旧 gen 帧，防止混入新 gen 播放
            if (LOG) Log.w(TAG, "onVideoData: dropped old-gen frame, myGen=" + myGen + " current=" + generation);
            return;
        }
        // 队列满时丢弃最旧的帧，防止 burst 导致内存暴涨
        // ★ 使用 AtomicInteger 快照替代 videoQueue.size()（O(1) vs O(n)），避免锁竞争
        while (videoQueueSize.get() >= VIDEO_QUEUE_MAX) {
            if (videoQueue.poll() != null) {
                videoQueueSize.decrementAndGet();
            }
        }
        videoQueue.offer(frameData);
        int curSize = videoQueueSize.incrementAndGet();  // O(1) 快照，避免后续 size() 调用
        if (LOG) {
            long now = System.currentTimeMillis();
            long interval = lastVideoRecvTime == 0 ? 0 : now - lastVideoRecvTime;
            lastVideoRecvTime = now;
            videoRecvCount++;
            long enqCost = now - tEntry;
            // ★ 诊断：enqueueExecutor 队列深度
            int enqBacklog = enqSubmitted.get() - enqCompleted.get();
            // ★ 每帧都打印（前30帧），之后仅在异常时打印
            if (videoRecvCount <= 30 || interval > 100 || enqCost > 10 || enqBacklog > 5 || videoRecvCount % 50 == 0) {
                long ptsMs = ((long)(frameData[3]&0xFF)<<24) | ((long)(frameData[4]&0xFF)<<16)
                           | ((long)(frameData[5]&0xFF)<<8) | (frameData[6]&0xFF);
                Log.w(TAG, "⬇ onVideoData: interval=" + interval + "ms pts=" + ptsMs + " vQ=" + curSize
                        + " total=" + videoRecvCount + " enqCost=" + enqCost + "ms enqBacklog=" + enqBacklog
                        + " execQ=" + enqueueExecutor.getQueue().size());
            }
        }
        // P0/A2: 不再基于队列水位启动播放，改为等待 onSentenceDone 信号
        // 安全阀：若队列堆积超过 150 帧（6 秒）仍未收到 sentenceDone，兜底启动
        if (!playbackStarted && !released && curSize >= 150) {
            playbackStarted = true;
            if (LOG) Log.i(TAG, "onVideoData: fallback start after buffering " + curSize + " frames (no sentenceDone yet)");
            startPlaybackAsync();
        }
    }

    /**
     * ★ B2: 异步入队 — 将帧处理从 WebSocket reader 线程完全卸载到独立线程。
     * ChatActivity.onMessage(ByteString) 应调用此方法而非直接调用 onVideoData/onAudioData。
     * Reader 线程仅做 one-time copy (toByteArray) + 提交任务，立即返回处理下一条消息，
     * 消除 reader 线程上的所有锁竞争和 I/O 开销。
     */
    public void onVideoDataAsync(byte[] raw) {
        // 快速路径检查，在 reader 线程提前拒绝无效数据，避免提交无用的 executor 任务
        if (released || raw == null || raw.length <= VIDEO_HDR) return;
        // ★ P0: 捕获当前 generation，executor 任务执行时校验，不匹配则丢弃旧 gen 帧
        final long myGen = generation;
        // ★ WebSocket reader 线程级别日志：记录帧真正到达的时间点
        //   对比 enqueueExecutor 线程的 ⬇ onVideoData 日志，可定位延迟在 WS 层还是 enqueue 层
        if (LOG) {
            long wsNow = System.currentTimeMillis();
            long wsInterval = lastWsVideoTime == 0 ? 0 : wsNow - lastWsVideoTime;
            lastWsVideoTime = wsNow;
            wsVideoCount++;
            long ptsMs = ((long)(raw[3]&0xFF)<<24) | ((long)(raw[4]&0xFF)<<16)
                       | ((long)(raw[5]&0xFF)<<8) | (raw[6]&0xFF);
            if (wsVideoCount <= 30 || wsInterval > 100 || wsVideoCount % 50 == 0) {
                Log.w(TAG, "⇩ WS-RECV: interval=" + wsInterval + "ms pts=" + ptsMs
                        + " len=" + raw.length + " total=" + wsVideoCount);
            }
        }
        enqSubmitted.incrementAndGet();
        enqueueExecutor.submit(() -> {
            onVideoData(raw, myGen);
            enqCompleted.incrementAndGet();
        });
    }

    public void onAudioDataAsync(byte[] raw) {
        if (released || raw == null || raw.length <= AUDIO_HDR) return;
        final long myGen = generation;
        enqSubmitted.incrementAndGet();
        enqueueExecutor.submit(() -> {
            onAudioData(raw, myGen);
            enqCompleted.incrementAndGet();
        });
    }

    /**
     * 对话结束时调用，清空队列，释放解码器，准备下一次对话
     */
    public void onConversationEnd() {
        if (LOG) Log.i(TAG, "onConversationEnd: clearing queues");
        generation++;  // ★ 通知运行中的音视频线程立即退出
        audioQueue.clear();
        videoQueue.clear();
        videoQueueSize.set(0);  // ★ 与 clear() 同步重置计数器
        enqueueExecutor.getQueue().clear();  // ★ P0: 丢弃旧 gen 的待处理任务，防止旧帧重新填入队列
        // 停止 AudioTrack，解除 write() 阻塞（pause+flush 是瞬间操作）
        try { audioTrack.pause(); } catch (Exception ignored) {}
        try { audioTrack.flush(); } catch (Exception ignored) {}
        // ★ generation++ 已足够让旧线程退出循环，不再 join 等待阻塞调用线程
        // startPlayback() 会处理残余线程的清理
        releaseDecoder();
        playbackStarted = false;  // 允许下次对话重新启动播放
        firstSentenceBuffered = false;
        videoRecvCount = 0;
        audioRecvCount = 0;
        lastVideoRecvTime = 0;
        lastAudioRecvTime = 0;
        // 重置同步诊断字段
        diagAudioPtsMs = 0;
        diagAudioGlobalPtsMs = 0;
        diagAudioWallMs = 0;
        diagVideoFeedPtsMs = 0;
        diagVideoFeedGlobalPtsMs = 0;
        diagVideoFeedWallMs = 0;
        diagVideoRenderPtsMs = 0;
        diagVideoRenderWallMs = 0;
        audioRecvTotalBytes = 0;
        enqSubmitted.set(0);
        enqCompleted.set(0);
        audioStarted = false;
        audioPlaybackPtsMs = 0;
    }

    /**
     * Sentence done notification from backend.
     * PTS 全局化已由后端完成，此处仅记录日志，不再维护偏移量。
     */
    public void onSentenceDone(int sentenceId) {
        // ★ 使用 AtomicInteger 快照替代 videoQueue.size()（O(1) vs O(n)），避免 reader 线程锁竞争
        int curVideoQ = videoQueueSize.get();
        if (LOG) Log.i(TAG, "onSentenceDone: sid=" + sentenceId
                + " audioQueueSize=" + audioQueue.size() + " videoQueueSize=" + curVideoQ
                + " playbackStarted=" + playbackStarted);
        // P0/A2: 收到第一个句子完成信号后启动播放（此时该句子的所有帧已入队）
        if (!playbackStarted && !released) {
            if (curVideoQ < 10) {
                if (LOG) Log.w(TAG, "onSentenceDone: only " + curVideoQ
                        + " frames buffered, deferring playback start");
                return;
            }
            firstSentenceBuffered = true;
            playbackStarted = true;
            if (LOG) Log.i(TAG, "onSentenceDone: starting playback with " + curVideoQ + " frames buffered");
            startPlaybackAsync();
        }
    }

    public void interrupt() {
        if (LOG) Log.i(TAG, "interrupt");
        generation++;  // ★ 通知运行中的音视频线程立即退出
        audioQueue.clear();
        videoQueue.clear();
        videoQueueSize.set(0);  // ★ 与 clear() 同步重置计数器
        enqueueExecutor.getQueue().clear();  // ★ P0: 丢弃旧 gen 的待处理任务，防止旧帧重新填入队列
        // 停止 AudioTrack，解除 write() 阻塞（pause+flush 是瞬间操作）
        try { audioTrack.pause(); } catch (Exception ignored) {}
        try { audioTrack.flush(); } catch (Exception ignored) {}
        // ★ generation++ 已足够让旧线程退出循环，不再 join 等待阻塞调用线程
        releaseDecoder();
        playbackStarted = false;
        firstSentenceBuffered = false;
        videoRecvCount = 0;
        audioRecvCount = 0;
        lastVideoRecvTime = 0;
        lastAudioRecvTime = 0;
        wsVideoCount = 0;
        lastWsVideoTime = 0;
        // 重置同步诊断字段
        diagAudioPtsMs = 0;
        diagAudioGlobalPtsMs = 0;
        diagAudioWallMs = 0;
        diagVideoFeedPtsMs = 0;
        diagVideoFeedGlobalPtsMs = 0;
        diagVideoFeedWallMs = 0;
        diagVideoRenderPtsMs = 0;
        diagVideoRenderWallMs = 0;
        audioRecvTotalBytes = 0;
        enqSubmitted.set(0);
        enqCompleted.set(0);
        audioStarted = false;
        audioPlaybackPtsMs = 0;
    }

    public void release() {
        if (released) return;
        released = true;
        // 唤醒等待 Surface 的 videoFeedLoop
        synchronized (surfaceLock) { surfaceLock.notifyAll(); }
        // 先停止 AudioTrack，解除 write() 阻塞，再等音频线程退出
        try { audioTrack.pause(); } catch (Exception ignored) {}
        try { audioTrack.flush(); } catch (Exception ignored) {}
        // 等待音频线程完全退出，避免 release() 时 write() 仍在 native 层执行导致 native crash
        Thread t = audioThread;
        if (t != null) {
            t.interrupt();
            for (int i = 0; i < 20 && t.isAlive(); i++) {
                try { t.join(200); } catch (InterruptedException ignored) {}
            }
            if (t.isAlive()) {
                Log.w(TAG, "release: audio thread still alive after 4s, force proceeding");
            }
        }
        enqueueExecutor.shutdownNow();  // ★ B2 cleanup
        releaseDecoder();
        try { audioTrack.stop(); } catch (Exception ignored) {}
        try { audioTrack.release(); } catch (Exception ignored) {}
    }

    // ====================== PLAYBACK CONTROL ======================

    private void startPlayback() {
        // ★ 调用者（onVideoData）已通过 playbackStarted 标记防重入，
        // 且已在 decoderExecutor 上异步调用，此处不再阻塞 WebSocket 线程
        // ★ 确保上一次对话的音频线程已完全退出，避免两个线程同时 write() 导致 native crash
        Thread t = audioThread;
        if (t != null) {
            t.interrupt();
            for (int i = 0; i < 10 && t.isAlive(); i++) {
                try { t.join(200); } catch (InterruptedException ignored) {}
            }
            if (t.isAlive()) {
                Log.w(TAG, "startPlayback: stale audio thread still alive, detaching");
                audioThread = null;
            }
        }

        generation++;  // ★ 更新代际，确保新线程使用新代际

        // ★ 防御：检查 AudioTrack 是否初始化成功，避免 write/play 抛异常导致
        // videoFeedLoop 和 audioPlayLoop 都无法启动（即无画面+无声）
        if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "startPlayback: AudioTrack not initialized, state=" + audioTrack.getState());
            playbackStarted = false;
            return;
        }
        try {
            // ★ 关键修复：onConversationEnd/interrupt 中 pause()+flush() 后，
            // AudioTrack 处于 PAUSED+FLUSHED 状态。某些设备上直接 write()+play() 无法恢复播放，
            // 必须先 stop() 重置到未初始化后的干净状态，再 write()+play()。
            if (audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                try { audioTrack.stop(); } catch (Exception ignored) {}
            }
            // 预填充 2 帧静音（80ms），防止启动瞬间 underrun 产生电流声，同时不过度阻塞后续真实音频写入
            for (int i = 0; i < 2; i++) {
                audioTrack.write(silenceFrame, 0, silenceFrame.length);
            }
            audioTrack.play();
        } catch (IllegalStateException e) {
            Log.e(TAG, "startPlayback: AudioTrack write/play failed", e);
            playbackStarted = false;
            return;
        }
        // ★ videoFeedLoop 使用独立线程（修复 Bug 2：避免与 startPlayback 共享 SingleThreadExecutor）
        Thread vt = new Thread(this::videoFeedLoop, "av-video");
        vt.start();
        videoThread = vt;
        // ★ 重置音画同步状态
        audioStarted = false;
        audioPlaybackPtsMs = 0;
        new Thread(this::audioPlayLoop, "av-audio").start();
    }

    /**
     * ★ 异步启动播放，避免阻塞调用线程（OkHttp reader 或 enqueueExecutor）。
     * 使用 AtomicBoolean 防止重复启动（修复 Bug 2）。
     */
    private void startPlaybackAsync() {
        if (!startPlaybackRunning.compareAndSet(false, true)) {
            if (LOG) Log.w(TAG, "startPlaybackAsync: already running, skipping");
            return;
        }
        new Thread(() -> {
            try {
                startPlayback();
            } finally {
                startPlaybackRunning.set(false);
            }
        }, "av-init").start();
    }

    // ====================== AUDIO PLAYBACK ======================

    private void audioPlayLoop() {
        audioThread = Thread.currentThread();
        final long myGen = generation;  // ★ 记录当前代际，gen 变化时退出
        if (LOG) Log.i(TAG, "audioPlayLoop: started gen=" + myGen);
        int audioWriteCount = 0;
        long audioTotalSamples = 0;     // 累计写入采样数，用于计算预期播放时长
        long audioStartWallMs = System.currentTimeMillis();
        while (!released && generation == myGen) {  // ★ gen 变化立即退出
            byte[] pkt = audioQueue.poll();
            if (pkt == null) {
                if (released) break;
                try {
                    // ★ 只在 AudioTrack 缓冲区不足 80ms 时才补静音帧
                    // 否则仅 sleep 等待，避免填满缓冲区导致后续真实音频 write() 长时间阻塞
                    long headPos = audioTrack.getPlaybackHeadPosition();
                    long bufferedSamples = audioTotalSamples - headPos;
                    // 初始阶段 bufferedSamples 可能为负（startPlayback 中的预填充静音未计入），夹到 >=0
                    long bufferedMs = Math.max(0, bufferedSamples) * 1000 / AUDIO_SAMPLE_RATE;
                    if (bufferedMs < 80) {
                        audioTrack.write(silenceFrame, 0, silenceFrame.length);
                        audioTotalSamples += silenceFrame.length / 2;
                    }
                    Thread.sleep(FRAME_INTERVAL_MS);
                } catch (IllegalStateException e) {
                    if (LOG) Log.w(TAG, "audioPlayLoop: silence write failed: " + e.getMessage());
                    break;
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    // 防御性捕获所有异常，避免 native crash
                    if (LOG) Log.w(TAG, "audioPlayLoop: silence write exception: " + e.getMessage());
                    break;
                }
                continue;
            }
            if (released) break;  // poll 之后再检查一次
            if (pkt.length > AUDIO_HDR) {
                byte[] pcm = Arrays.copyOfRange(pkt, AUDIO_HDR, pkt.length);
                // 校验 PCM 长度为偶数（16-bit 采样 = 2 bytes/sample）
                if (pcm.length % 2 != 0) {
                    if (LOG) Log.w(TAG, "audioPlayLoop: odd PCM length=" + pcm.length + ", skip");
                    continue;
                }
                // 解析音频 PTS（后端已全局化，直接使用）
                long audioPtsMs = ((long)(pkt[3]&0xFF)<<24) | ((long)(pkt[4]&0xFF)<<16)
                                | ((long)(pkt[5]&0xFF)<<8) | (pkt[6]&0xFF);
                long audioGlobalPtsMs = audioPtsMs;

                // ★ 写入前诊断：记录 AudioTrack 缓冲深度，判断是否因缓冲区满而阻塞
                long preHeadPos = audioTrack.getPlaybackHeadPosition();
                long preExpectedMs = audioTotalSamples * 1000 / AUDIO_SAMPLE_RATE;
                long prePlayedMs = preHeadPos * 1000 / AUDIO_SAMPLE_RATE;
                long preBufferedMs = preExpectedMs - prePlayedMs;
                int preQ = audioQueue.size();
                long pcmDurationMs = pcm.length * 1000L / (AUDIO_SAMPLE_RATE * 2);

                long tWrite = System.currentTimeMillis();
                try {
                    // ★ 分块写入，每块 40ms（1280B@16kHz），防止大音频包阻塞 write() 整个时长（Bug 3）
                    // 每写完一个 chunk 更新 audioPlaybackPtsMs，让 videoFeedLoop 实时感知音频进度
                    int chunkSize = AUDIO_SAMPLE_RATE * 2 / 25; // 1280B = 40ms
                    int offset = 0;
                    int written = 0; // 移到 try 块作用域
                    while (offset < pcm.length && !released && generation == myGen) {
                        int toWrite = Math.min(chunkSize, pcm.length - offset);
                        int w = audioTrack.write(pcm, offset, toWrite);
                        if (w <= 0) break;
                        offset += w;
                        written += w;
                        // 每写一个 chunk 就更新音频时钟估计，让 videoFeedLoop 实时感知
                        audioTotalSamples += w / 2;
                        long headPos = audioTrack.getPlaybackHeadPosition();
                        long bufferedSamples = audioTotalSamples - headPos;
                        long bufferedMs = Math.max(0, bufferedSamples) * 1000 / AUDIO_SAMPLE_RATE;
                        // 当前音频播放位置 ≈ 已写入部分的末尾 PTS - 缓冲时长
                        long writtenEndPtsMs = audioPtsMs + pcmDurationMs * offset / pcm.length;
                        audioPlaybackPtsMs = writtenEndPtsMs - bufferedMs;
                    }
                    long writeCost = System.currentTimeMillis() - tWrite;
                    if (written > 0) {
                        audioWriteCount++;
                        // 标记音频已启动（Bug 1：videoFeedLoop 启动阶段检查此标志）
                        if (!audioStarted) {
                            audioStarted = true;
                            if (LOG) Log.i(TAG, "♫ audioStarted=true at pts=" + audioPtsMs + " writeCount=" + audioWriteCount);
                        }
                    }

                    // 更新跨线程诊断字段
                    diagAudioPtsMs = audioPtsMs;
                    diagAudioGlobalPtsMs = audioGlobalPtsMs;
                    diagAudioWallMs = System.currentTimeMillis();

                    // ★ 降低日志阈值：writeCost > 5ms 或前 50 帧 或每 20 帧打印，观察缓冲堆积
                    if (LOG && (writeCost > 5 || audioWriteCount <= 50 || audioWriteCount % 20 == 0)) {
                        long postHeadPos = audioTrack.getPlaybackHeadPosition();
                        long postPlayedMs = postHeadPos * 1000 / AUDIO_SAMPLE_RATE;
                        long postExpectedMs = audioTotalSamples * 1000 / AUDIO_SAMPLE_RATE;
                        long postBufferedMs = postExpectedMs - postPlayedMs;
                        long wallElapsed = System.currentTimeMillis() - audioStartWallMs;
                        Log.w(TAG, "♫ AUDIO: pts=" + audioPtsMs
                            + " pcmDur=" + pcmDurationMs + "ms"
                            + " preBuf=" + preBufferedMs + "ms→postBuf=" + postBufferedMs + "ms"
                            + " q=" + preQ + "→" + audioQueue.size()
                            + " cost=" + writeCost + "ms"
                            + " wall=" + wallElapsed + "ms"
                            + " #" + audioWriteCount);
                    }
                } catch (IllegalStateException e) {
                    if (LOG) Log.w(TAG, "audioPlayLoop: write failed, track released: " + e.getMessage());
                    break;
                } catch (Exception e) {
                    // 防御性捕获所有异常，避免 native crash
                    if (LOG) Log.w(TAG, "audioPlayLoop: write exception: " + e.getMessage());
                    break;
                }
            }
        }
        audioThread = null;
        if (LOG) Log.i(TAG, "audioPlayLoop: end");
    }

    // ====================== VIDEO DECODE & RENDER ======================

    private void videoFeedLoop() {
        long myGen = generation;  // ★ 记录代际，检测变化时重置本地状态
        if (LOG) Log.i(TAG, "videoFeedLoop: started gen=" + myGen + ", waiting for surface");
        // ★ 使用 surfaceLock.wait() 替代一次性 CountDownLatch，支持 Surface 销毁/重建场景
        synchronized (surfaceLock) {
            while (surface == null && !released && myGen == generation) {
                try { surfaceLock.wait(500); } catch (InterruptedException e) {
                    if (LOG) Log.w(TAG, "videoFeedLoop: interrupted waiting for surface");
                    return;
                }
            }
        }
        if (released) return;
        // 代际变化说明本次对话已被中断，直接退出让位给新播放线程
        if (myGen != generation) {
            if (LOG) Log.i(TAG, "videoFeedLoop: generation changed while waiting for surface, exiting");
            return;
        }
        if (surface == null) {
            if (LOG) Log.w(TAG, "videoFeedLoop: surface not ready, released=" + released + " surface=" + surface);
            return;
        }
        if (LOG) Log.i(TAG, "videoFeedLoop: surface ready, entering main loop, vQ=" + videoQueueSize.get());

        MediaCodec c = null;
        long[] spsPpsLen = new long[2];
        long lastRenderTime = 0;
        int videoFeedCount = 0;
        int videoRenderCount = 0;
        long videoStartWallMs = System.currentTimeMillis();
        // ★ 诊断：循环性能分解
        int loopIter = 0;           // 总循环迭代数
        int loopIdleEmptyQ = 0;     // peek() 返回 null 的次数（队列空）
        int loopIdleCodecFull = 0;  // dequeueInputBuffer 返回 -1 的次数（codec 输入满）
        int loopNoOutput = 0;       // dequeueOutputBuffer 返回 -1 的次数（无解码输出）
        long loopTotalDeqOutMs = 0; // 累计 dequeueOutputBuffer 耗时
        long loopTotalRenderMs = 0; // 累计 releaseOutputBuffer 耗时
        long loopTotalDeqInMs = 0;  // 累计 dequeueInputBuffer 耗时
        long loopTotalQInMs = 0;    // 累计 queueInputBuffer 耗时
        long loopLastDiagWallMs = videoStartWallMs;
        int lastVideoQ = videoQueueSize.get(); // 追踪 vQ 变化以检测降到 0

        while (!released) {
            loopIter++;
            // ★ 检测代际变化：新对话已开始，旧循环必须退出释放 SingleThreadExecutor，
            // 否则排队的 startPlayback() 永远无法执行 → 音频线程永不创建 → 无声
            if (myGen != generation) {
                if (LOG) Log.i(TAG, "videoFeedLoop: generation changed " + myGen + "→" + generation + ", exiting");
                if (c != null) {
                    try { c.stop(); } catch (Exception ignored) {}
                    try { c.release(); } catch (Exception ignored) {}
                    c = null;
                }
                // 不要 release h264Decoder — 它可能已被外部 releaseDecoder() 设为 null
                break;  // ★ 退出循环，释放 executor 让 startPlayback() 可以执行
            }

            // ★ 防御：如果 codec 被外部释放（h264Decoder==null）但本地 c 仍引用旧实例，重置
            if (c != null && h264Decoder == null) {
                if (LOG) Log.w(TAG, "videoFeedLoop: codec released externally, resetting");
                // 不要手动 release c — 外部已释放
                c = null;
                continue;
            }
            // ① Codec not ready: wait for keyframe with SPS/PPS
            if (c == null) {
                byte[] pkt;
                try {
                    pkt = videoQueue.poll(100, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) { return; }
                if (pkt == null) continue;
                videoQueueSize.decrementAndGet();  // ★ 维护 O(1) 计数器

                if (pkt.length < VIDEO_HDR + 4) {
                    if (LOG) Log.w(TAG, "videoFeedLoop: packet too small: " + pkt.length);
                    continue;
                }
                boolean keyFrame = (pkt[7] & 0xFF) == 1;
                if (!keyFrame) {
                    if (LOG) Log.d(TAG, "videoFeedLoop: waiting for keyframe, got non-keyframe");
                    continue;
                }
                if (LOG) Log.i(TAG, "videoFeedLoop: got keyframe, pktLen=" + pkt.length);

                byte[] avData = Arrays.copyOfRange(pkt, VIDEO_HDR, pkt.length);
                if (LOG) Log.i(TAG, "videoFeedLoop: avDataLen=" + avData.length);
                byte[] sps = extractNal(avData, 7);
                byte[] pps = extractNal(avData, 8);
                if (sps == null || pps == null) {
                    if (LOG) Log.w(TAG, "No SPS/PPS in keyframe, skip");
                    continue;
                }
                if (LOG) Log.i(TAG, "videoFeedLoop: SPS len=" + sps.length + " PPS len=" + pps.length);

                c = createCodec(sps, pps, surface);
                if (c == null) {
                    if (LOG) Log.e(TAG, "Failed to create codec");
                    continue;
                }
                h264Decoder = c;
                spsPpsLen[0] = sps.length;
                spsPpsLen[1] = pps.length;
                if (LOG) Log.i(TAG, "Codec created: " + c.getCodecInfo().getName() + " SPS=" + sps.length + " PPS=" + pps.length);

                // Feed the first keyframe（PTS 已由后端全局化）
                long firstPtsMs = ((long) (pkt[3] & 0xFF) << 24) | ((long) (pkt[4] & 0xFF) << 16)
                        | ((long) (pkt[5] & 0xFF) << 8) | (pkt[6] & 0xFF);
                long tFirstFeed = System.currentTimeMillis();
                int inIdx = c.dequeueInputBuffer(50_000);
                long firstDequeueCost = System.currentTimeMillis() - tFirstFeed;
                if (inIdx >= 0) {
                    ByteBuffer inBuf = c.getInputBuffer(inIdx);
                    if (inBuf != null) {
                        inBuf.clear();
                        inBuf.put(avData);
                        c.queueInputBuffer(inIdx, 0, avData.length, firstPtsMs * 1000,
                                MediaCodec.BUFFER_FLAG_KEY_FRAME);
                    }
                    if (LOG) Log.i(TAG, "videoFeedLoop: first keyframe fed, dequeueIn=" + firstDequeueCost + "ms size=" + avData.length);
                } else {
                    if (LOG) Log.w(TAG, "videoFeedLoop: first keyframe dequeueInputBuffer failed, cost=" + firstDequeueCost + "ms idx=" + inIdx);
                }
                lastRenderTime = System.currentTimeMillis();
                continue;
            }

            // ② Render output first (with 25fps pacing)
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            long tDequeueOutStart = System.currentTimeMillis();
            int outIdx = c.dequeueOutputBuffer(outInfo, 1_000); // 1ms timeout
            long dequeueOutCost = System.currentTimeMillis() - tDequeueOutStart;
            if (LOG) loopTotalDeqOutMs += dequeueOutCost;
            if (outIdx >= 0) {
                if ((outInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    c.releaseOutputBuffer(outIdx, false);
                } else {
                    long now = System.currentTimeMillis();
                    long elapsed = now - lastRenderTime;
                    if (elapsed < FRAME_INTERVAL_MS) {
                        try { Thread.sleep(FRAME_INTERVAL_MS - elapsed); } catch (InterruptedException e) { return; }
                    }
                    long tRenderStart = System.currentTimeMillis();
                    c.releaseOutputBuffer(outIdx, true);
                    long renderCost = System.currentTimeMillis() - tRenderStart;
                    if (LOG) loopTotalRenderMs += renderCost;
                    lastRenderTime = System.currentTimeMillis();
                    videoRenderCount++;
                    diagVideoRenderPtsMs = outInfo.presentationTimeUs / 1000;  // us→ms
                    diagVideoRenderWallMs = System.currentTimeMillis();

                    if (LOG && (dequeueOutCost > 10 || renderCost > 10 || videoRenderCount % 25 == 0)) {
                        Log.w(TAG, "▼ VIDEO-RENDER: pts=" + (outInfo.presentationTimeUs / 1000)
                            + "ms deqOut=" + dequeueOutCost + "ms render=" + renderCost
                            + "ms outSize=" + outInfo.size + " flags=" + outInfo.flags
                            + " #" + videoRenderCount);
                    }

                    // ★ 每隔约 2 秒（50 帧 @25fps）输出一次音画同步综合报告
                    if (LOG && videoRenderCount % 50 == 0) {
                        long aPts = diagAudioPtsMs;
                        long aGlobal = diagAudioGlobalPtsMs;
                        long vFeedPts = diagVideoFeedPtsMs;
                        long vFeedGlobal = diagVideoFeedGlobalPtsMs;
                        long vRenderPts = diagVideoRenderPtsMs;
                        long wallNow = System.currentTimeMillis();
                        long audioAge = wallNow - diagAudioWallMs;
                        long videoFeedAge = wallNow - diagVideoFeedWallMs;
                        long videoRenderAge = wallNow - diagVideoRenderWallMs;
                        // ★ 诊断：循环性能分解
                        long diagElapsed = wallNow - loopLastDiagWallMs;
                        Log.w(TAG, "╔══ SYNC-REPORT @" + videoRenderCount + " renders, " + videoFeedCount + " feeds, iter=" + loopIter + " ══╗");
                        Log.w(TAG, "║ audio:  pts=" + aPts + " globalPts=" + aGlobal
                            + " (age=" + audioAge + "ms) aQ=" + audioQueue.size());
                        Log.w(TAG, "║ video:  feedPts=" + vFeedPts + " feedGlobal=" + vFeedGlobal
                            + " (age=" + videoFeedAge + "ms)");
                        Log.w(TAG, "║ render: pts=" + vRenderPts
                            + " (age=" + videoRenderAge + "ms) vQ=" + videoQueueSize.get()
                            + " enqBacklog=" + (enqSubmitted.get() - enqCompleted.get()));
                        Log.w(TAG, "║ LOOP-DIAG " + diagElapsed + "ms:"
                            + " idleEmptyQ=" + loopIdleEmptyQ + " idleCodecFull=" + loopIdleCodecFull
                            + " noOutput=" + loopNoOutput);
                        Log.w(TAG, "║ LOOP-COST: deqOut=" + loopTotalDeqOutMs + "ms render=" + loopTotalRenderMs
                            + "ms deqIn=" + loopTotalDeqInMs + "ms qIn=" + loopTotalQInMs + "ms");
                        Log.w(TAG, "║ Δ(audioGlobal-videoFeedGlobal)=" + (aGlobal - vFeedGlobal) + "ms"
                            + " Δ(audioGlobal-videoRender)=" + (aGlobal - vRenderPts) + "ms");
                        Log.w(TAG, "╚══════════════════════════════════╝");
                        // 重置诊断计数器
                        loopLastDiagWallMs = wallNow;
                        loopIdleEmptyQ = 0;
                        loopIdleCodecFull = 0;
                        loopNoOutput = 0;
                        loopTotalDeqOutMs = 0;
                        loopTotalRenderMs = 0;
                        loopTotalDeqInMs = 0;
                        loopTotalQInMs = 0;
                    }
                }
            } else {
                if (LOG) loopNoOutput++;
                if (LOG && dequeueOutCost > 50) {
                    Log.w(TAG, "videoDecode: dequeueOut TIMEOUT " + dequeueOutCost + "ms outIdx=" + outIdx);
                }
            }

            // ③ Feed next input frame（尽快喂给解码器，不做 25fps 节流）
            // ★ 诊断：每轮检测 vQ 变化
            int curVQ = videoQueueSize.get();
            if (LOG && lastVideoQ > 0 && curVQ == 0) {
                Log.w(TAG, "⚠ VQ-DRAINED: vQ dropped to 0! renderCount=" + videoRenderCount
                        + " feedCount=" + videoFeedCount + " loopIter=" + loopIter
                        + " wallElapsed=" + (System.currentTimeMillis() - videoStartWallMs) + "ms");
            }
            lastVideoQ = curVQ;
            // 使用 peek 而非 poll：先检查是否有帧可用，等 codec 确认可接受后再从队列移除，
            // 避免 dequeueInputBuffer 超时时帧被丢弃。
            byte[] pkt = videoQueue.peek(); // non-blocking peek
            if (pkt == null) {
                if (LOG) loopIdleEmptyQ++;
                try { Thread.sleep(10); } catch (InterruptedException e) { return; }
                continue;
            }
            if (LOG) Log.d(TAG, "videoFeedLoop: feed frame, vQ=" + videoQueueSize.get());

            if (pkt.length < VIDEO_HDR + 4) {
                videoQueue.poll(); // malformed, discard
                videoQueueSize.decrementAndGet();  // ★ 维护 O(1) 计数器
                continue;
            }
            // [typeFlag:1B][sentenceId:2B][ptsMs:4B][keyFrame:1B][H.264:N]
            long ptsMs = ((long) (pkt[3] & 0xFF) << 24) | ((long) (pkt[4] & 0xFF) << 16)
                    | ((long) (pkt[5] & 0xFF) << 8) | (pkt[6] & 0xFF);
            boolean keyFrame = (pkt[7] & 0xFF) == 1;
            byte[] avData = Arrays.copyOfRange(pkt, VIDEO_HDR, pkt.length);

            // Check if SPS/PPS changed
            if (keyFrame) {
                byte[] sps = extractNal(avData, 7);
                byte[] pps = extractNal(avData, 8);
                if (sps != null && pps != null) {
                    if (sps.length != spsPpsLen[0] || pps.length != spsPpsLen[1]) {
                        if (LOG) Log.w(TAG, "SPS/PPS changed! Recreate codec");
                        releaseDecoder();
                        c = createCodec(sps, pps, surface);
                        if (c == null) {
                            if (LOG) Log.e(TAG, "Failed to recreate codec");
                            continue;
                        }
                        h264Decoder = c;
                        spsPpsLen[0] = sps.length;
                        spsPpsLen[1] = pps.length;
                    }
                }
            }

            // PTS 已由后端全局化，直接使用
            long globalPtsUs = ptsMs * 1000;
            long videoGlobalPtsMs = ptsMs;  // 同步诊断用
            int flags = keyFrame ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
            long tDequeueInStart = System.currentTimeMillis();
            int inIdx = c.dequeueInputBuffer(1_000);
            long dequeueInCost = System.currentTimeMillis() - tDequeueInStart;
            if (LOG) loopTotalDeqInMs += dequeueInCost;
            if (inIdx >= 0) {
                // Codec 可接受输入 — 从队列移除该帧并喂入
                videoQueue.poll();
                videoQueueSize.decrementAndGet();  // ★ 维护 O(1) 计数器
                ByteBuffer inBuf = c.getInputBuffer(inIdx);
                if (inBuf != null) {
                    inBuf.clear();
                    inBuf.put(avData);
                    long tQueueStart = System.currentTimeMillis();
                    c.queueInputBuffer(inIdx, 0, avData.length, globalPtsUs, flags);
                    long queueCost = System.currentTimeMillis() - tQueueStart;
                    if (LOG) loopTotalQInMs += queueCost;
                    videoFeedCount++;
                    diagVideoFeedPtsMs = ptsMs;
                    diagVideoFeedGlobalPtsMs = videoGlobalPtsMs;
                    diagVideoFeedWallMs = System.currentTimeMillis();

                    if (LOG && (dequeueInCost > 10 || queueCost > 5 || videoFeedCount % 25 == 0)) {
                        Log.w(TAG, "▶ VIDEO-FEED: pts=" + ptsMs + " globalPts=" + videoGlobalPtsMs
                            + " deqIn=" + dequeueInCost + "ms qIn=" + queueCost
                            + "ms size=" + avData.length + " key=" + keyFrame
                            + " vQ=" + videoQueueSize.get() + " #" + videoFeedCount);
                    }
                }
            } else {
                // Codec 输入缓冲区满，帧保留在队列中，下次循环重试
                if (LOG) loopIdleCodecFull++;
                if (LOG && dequeueInCost > 10) {
                    Log.w(TAG, "videoFeed: codec input full, frame stays in queue, dequeueInCost=" + dequeueInCost + "ms");
                }
            }
        }

        if (LOG) Log.i(TAG, "videoFeedLoop: end");
    }

    // ====================== DECODER HELPERS ======================

    private void releaseDecoder() {
        MediaCodec c = h264Decoder;
        h264Decoder = null;
        if (c != null) {
            try { c.stop(); } catch (Exception ignored) {}
            try { c.release(); } catch (Exception ignored) {}
        }
    }

    private MediaCodec createCodec(byte[] sps, byte[] pps, Surface surface) {
        if (surface == null) return null;
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, VIDEO_WIDTH, VIDEO_HEIGHT);
        format.setByteBuffer("csd-0", ByteBuffer.wrap(sps));
        format.setByteBuffer("csd-1", ByteBuffer.wrap(pps));
        // KEY_PRIORITY removed: realtime priority causes some HW decoders
        // to skip deblocking, amplifying macroblock artifacts in low-bitrate streams.
        format.setInteger(MediaFormat.KEY_OPERATING_RATE, 25);

        // Find hardware decoder explicitly
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
        String hwName = null;
        for (MediaCodecInfo info : codecList.getCodecInfos()) {
            if (info.isEncoder()) continue;
            String[] types = info.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_AVC)) {
                    // Prefer hardware decoders (OMX. prefix, not OMX.google.)
                    if (!info.getName().startsWith("OMX.google.")) {
                        hwName = info.getName();
                        break;
                    }
                }
            }
            if (hwName != null) break;
        }

        // Fallback to any decoder
        if (hwName == null) {
            hwName = codecList.findDecoderForFormat(format);
        }

        if (hwName == null) {
            if (LOG) Log.e(TAG, "No decoder for " + format);
            return null;
        }
        try {
            MediaCodec codec = MediaCodec.createByCodecName(hwName);
            codec.configure(format, surface, null, 0);
            codec.start();
            if (LOG) Log.i(TAG, "Decoder created: " + hwName);
            return codec;
        } catch (Exception e) {
            if (LOG) Log.e(TAG, "createCodec failed", e);
            return null;
        }
    }

    private byte[] extractNal(byte[] data, int targetNalType) {
        for (int i = 0; i < data.length - 4; i++) {
            if (data[i] == 0 && data[i + 1] == 0) {
                int nalStart = -1;
                int headerLen = 0;
                if (data[i + 2] == 1) {
                    nalStart = i;
                    headerLen = 3;
                } else if (i < data.length - 5 && data[i + 2] == 0 && data[i + 3] == 1) {
                    nalStart = i;
                    headerLen = 4;
                }
                if (nalStart >= 0) {
                    int nalType = data[nalStart + headerLen] & 0x1F;
                    int nextStart = findNextStartCode(data, nalStart + headerLen);
                    int nalEnd = (nextStart >= 0) ? nextStart : data.length;
                    if (nalType == targetNalType) {
                        return Arrays.copyOfRange(data, nalStart, nalEnd);
                    }
                }
            }
        }
        return null;
    }

    private int findNextStartCode(byte[] data, int start) {
        for (int i = start; i < data.length - 3; i++) {
            if (data[i] == 0 && data[i + 1] == 0
                    && (data[i + 2] == 1 || (i < data.length - 4 && data[i + 2] == 0 && data[i + 3] == 1))) {
                return i;
            }
        }
        return -1;
    }
}
