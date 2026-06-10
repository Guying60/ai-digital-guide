package com.example.digitaltourguide.view.user;

import android.graphics.Matrix;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.function.LongSupplier;

/**
 * H.264 硬解码器：MediaCodec(async 模式) 直渲到 TextureView 的 Surface。
 *
 * WebSocket 读线程只负责 {@link #feed(AccessUnit)} 把 access unit 推入有界队列，
 * 绝不阻塞；所有 MediaCodec 调用都在独立的 "H264Decoder" HandlerThread 上串行执行。
 *
 * 协议：每句话首帧为带 in-band SPS/PPS 的 IDR。
 * - 句切换走 {@link #flushForNewSentence()}：flush 解码器 + 重置时钟 + 等待 IDR。
 *   服务端已流水线化（25fps rate-limited sending），句间隔≈40ms，冻结≈80ms 肉眼不可感知。
 *   首句需守卫：hasDecodedFirstFrame 为 false 时跳过 flush，避免清掉首帧。
 * - 真正中断（用户主动打断）走 {@link #flushForNewSentence()}：同上。
 * 节奏：首帧 pin 锚点，后续帧按 PTS 用 releaseOutputBuffer(index, ns) 定时渲染。
 */
public class H264VideoDecoder {

    private static final String TAG = "H264Decoder";
    private static final String MIME = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final int DEFAULT_W = 480;
    private static final int DEFAULT_H = 854;

    /** 队列上限 ≈ 3s@25fps，满则按 GOP 丢弃到下个关键帧，避免断参考链。 */
    private static final int MAX_PENDING_AUS = 75;
    /** 启播优先级延迟：首帧渲染稍微滞后，给后续帧留缓冲。 */
    private static final long START_LATENCY_NS = 100_000_000L; // 100ms（服务端已整流 + 全局 PTS）
    /** 严重落后阈值：超过则只丢显示不丢解码。 */
    private static final long LATE_DROP_NS = 300_000_000L; // 300ms（原80ms，容忍 burst 到达）
    /** 网络卡顿检测阈值：自上次渲染超过此值则重置渲染时钟，避免级联丢帧。 */
    private static final long STALL_GAP_NS = 300_000_000L; // 300ms
    /** 匀速喂帧间隔：25fps = 40ms/帧。 */
    private static final long FRAME_INTERVAL_MS = 40;
    /** 积压加速阈值：队列深度超过此值时缩短喂帧间隔，加速消耗积压帧。 */
    private static final int BURST_DEPTH_THRESHOLD = 10;
    /** 积压加速喂帧间隔。 */
    private static final long BURST_FRAME_INTERVAL_MS = 30;

    /** 解码输入单元：一个 H.264 Annex-B access unit。 */
    public static final class AccessUnit {
        final int sentenceId;
        final long ptsMs;       // 本句内毫秒时间戳
        final boolean keyFrame; // true=IDR(含 in-band SPS/PPS)
        final byte[] data;      // Annex-B 裸流

        public AccessUnit(int sentenceId, long ptsMs, boolean keyFrame, byte[] data) {
            this.sentenceId = sentenceId;
            this.ptsMs = ptsMs;
            this.keyFrame = keyFrame;
            this.data = data;
        }
    }

    private MediaCodec codec;
    private HandlerThread codecThread;
    private Handler codecHandler;
    private Surface surface;

    private final TextureView textureView;

    /** 音频时钟：返回已播放毫秒数（AudioTrack.getPlaybackHeadPosition / 16）。 */
    private volatile LongSupplier audioClockMs;

    // 仅在 codecThread 访问
    private final ArrayDeque<Integer> availableInputs = new ArrayDeque<>();
    // 跨线程：WS 线程 feed / codecThread drain
    private final ArrayDeque<AccessUnit> pendingAUs = new ArrayDeque<>();
    private volatile boolean waitingForKeyframe = true;
    private volatile boolean released = false;
    /** 已成功解码并渲染过至少一帧（跨线程读：主线程判断首句是否需要 flush）。 */
    volatile boolean hasDecodedFirstFrame = false;

    // 节奏锚点（仅 codecThread 访问）
    private long baseRenderNs = -1;
    private long basePtsUs = -1;
    /** 上一帧实际释放时刻（用于卡顿检测），仅 codecThread 访问 */
    private long lastActualRenderNs = -1;
    /**
     * 音频时钟基准偏移：视频比音频晚到时，记录视频启动瞬间的音频播放位置。
     * 同步公式：diffMs = videoPtsMs - (audioMs - audioClockBaselineMs)
     * 这样视频第一帧 PTS≈0 时，audioMs - baseline ≈ 0，不会被判为"过期"而丢弃。
     */
    private volatile long audioClockBaselineMs = 0;

    // ==== 临时诊断计测（定位卡顿用，验证后移除）====
    private static final boolean DIAG = true;
    private volatile long lastFeedNs = -1;   // 上一帧到达时刻（WS 线程）
    private long lastRenderNs = -1;
    /** 匀速喂帧定时 Runnable。 */
    private Runnable feedRunnable;          // 上一帧 release 时刻（codecThread）
    private int diagRenderCnt = 0;
    private int diagDropCnt = 0;

    // 显示尺寸（onOutputFormatChanged 后更新），用于 center-crop 变换
    private volatile int videoWidth = DEFAULT_W;
    private volatile int videoHeight = DEFAULT_H;

    public H264VideoDecoder(TextureView textureView) {
        this.textureView = textureView;
    }

    /** 设置音频时钟（ChatActivity 传入 getAudioPtsMs）。 */
    public void setAudioClock(LongSupplier clock) {
        this.audioClockMs = clock;
    }

    /**
     * 重置音频时钟基准：视频比音频晚到时调用。
     * 将当前音频播放位置记录为 baseline，后续同步公式变为：
     *   diffMs = videoPtsMs - (audioMs - baseline)
     * 这样视频 PTS≈0 的首帧不会因 audioMs 已跑了 4 秒而被判为"过期丢弃"。
     */
    public void resetAudioClockBaseline() {
        LongSupplier clock = audioClockMs;
        if (clock != null) {
            audioClockBaselineMs = clock.getAsLong();
        } else {
            audioClockBaselineMs = 0;
        }
        Log.d(TAG, "audioClockBaseline reset to " + audioClockBaselineMs + "ms");
    }

    /** TextureView Surface 就绪后调用：建线程、配置并启动 MediaCodec。 */
    public synchronized void start(@NonNull Surface outputSurface) {
        if (codec != null) return; // 已启动
        released = false;
        surface = outputSurface;
        codecThread = new HandlerThread(TAG);
        codecThread.start();
        codecHandler = new Handler(codecThread.getLooper());
        try {
            MediaFormat fmt = MediaFormat.createVideoFormat(MIME, DEFAULT_W, DEFAULT_H);
            // 不设 csd-0/csd-1：SPS/PPS 随每个 IDR 内联(in-band)送达
            codec = MediaCodec.createDecoderByType(MIME);
            codec.setCallback(callback, codecHandler);
            codec.configure(fmt, surface, null, 0);
            codec.start();
            waitingForKeyframe = true;
            Log.d(TAG, "MediaCodec 已启动");
        } catch (Exception e) {
            Log.e(TAG, "MediaCodec 初始化失败，硬解不可用", e);
            releaseCodecOnly();
        }
    }

    /** 是否成功启用硬解。 */
    public boolean isReady() {
        return codec != null && !released;
    }

    /** WS 读线程调用：非阻塞推入 access unit。 */
    public void feed(AccessUnit au) {
        if (released || codec == null) return;
        if (DIAG) {
            long now = System.nanoTime();
            long gapMs = lastFeedNs < 0 ? -1 : (now - lastFeedNs) / 1_000_000L;
            lastFeedNs = now;
            int depth;
            synchronized (pendingAUs) { depth = pendingAUs.size(); }
            Log.d(TAG, "FEED sid=" + au.sentenceId + " pts=" + au.ptsMs
                    + " key=" + au.keyFrame + " arrGapMs=" + gapMs + " queueDepth=" + depth);
        }
        synchronized (pendingAUs) {
            if (pendingAUs.size() >= MAX_PENDING_AUS) {
                dropOneGop();
            }
            pendingAUs.addLast(au);
        }
        kickFeed();
    }

    /** 队满时从队首丢弃整段不完整 GOP（直到下个关键帧），保护参考链。须持有 pendingAUs 锁。 */
    private void dropOneGop() {
        pendingAUs.pollFirst();
        AccessUnit head;
        while ((head = pendingAUs.peekFirst()) != null && !head.keyFrame) {
            pendingAUs.pollFirst();
        }
        // 丢弃后下个喂入的应为关键帧；标记等待，避免把残缺帧喂给解码器
        waitingForKeyframe = true;
    }

    /** 句切换/中断：flush 解码器并等待下个 IDR，重置节奏锚点。用于句切换（需守卫首句）和用户主动打断。 */
    public void flushForNewSentence() {
        synchronized (pendingAUs) {
            pendingAUs.clear();
        }
        Handler h = codecHandler;
        if (h == null || codec == null) return;
        h.post(() -> {
            if (codec == null || released) return;
            cancelFeed();
            try {
                codec.flush();
                codec.start(); // async 模式 flush 后必须 start，否则回调停发
            } catch (Exception e) {
                Log.e(TAG, "flush/start 失败", e);
            }
            availableInputs.clear();
            waitingForKeyframe = true;
            baseRenderNs = -1;
            basePtsUs = -1;
            lastActualRenderNs = -1;
            audioClockBaselineMs = 0;
        });
    }

    /**
     * 正常句切换：清空解码器内部缓冲 + 标记等待 IDR，但不重置渲染时钟。
     * <p>
     * <b>当前未使用</b>：因为不重置 baseRenderNs/basePtsUs，新句子首帧 PTS≈0 会导致
     * sinceBaseUs 为巨大负数，所有帧被 LATE_DROP_NS 判定过期而丢弃。
     * 实际使用 {@link #flushForNewSentence()}（flush + 重置时钟），配合服务端流水线化，
     * 句间隔≈40ms + 解码延迟≈40ms = ~80ms，肉眼不可感知。
     * <p>
     * 首句需额外守卫：hasDecodedFirstFrame 为 false 时跳过 flush，避免清掉首帧。
     */
    public void sentenceTransitionFlush() {
        synchronized (pendingAUs) {
            pendingAUs.clear();
        }
        Handler h = codecHandler;
        if (h == null || codec == null) return;
        h.post(() -> {
            if (codec == null || released) return;
            cancelFeed();
            try {
                codec.flush();
                codec.start();
            } catch (Exception e) {
                Log.e(TAG, "sentenceTransitionFlush flush/start 失败", e);
            }
            availableInputs.clear();
            waitingForKeyframe = true;
            // 不重置 baseRenderNs/basePtsUs → 渲染时钟跨句连续
        });
    }

    /**
     * 仅在 codecThread 执行：每次只喂一帧给解码器，然后定时 40ms 后喂下一帧。
     * <p>
     * 不依赖 onInputBufferAvailable 回调驱动（回调会一次性触发所有空闲 input buffer，
     * 造成 burst 喂入）。改用固定 40ms 定时器匀速喂帧，形成 jitter buffer：
     * 前端缓存 burst 帧，按 25fps 匀速喂给解码器，配合
     * releaseOutputBuffer(index, renderAtNs) 的 PTS 排程，实现丝滑播放。
     */
    private void drainInputs() {
        if (codec == null || released) return;
        if (availableInputs.isEmpty()) {
            // input buffer 全占满，等 onInputBufferAvailable 恢复
            return;
        }

        AccessUnit au;
        synchronized (pendingAUs) {
            au = pendingAUs.peekFirst();
            if (au == null) return; // 队列空，定时器自然停止
            if (waitingForKeyframe && !au.keyFrame) {
                pendingAUs.pollFirst(); // 丢弃 IDR 之前的残缺帧，防花屏
                kickFeed(); // 立即尝试下一帧
                return;
            }
            pendingAUs.pollFirst();
        }
        waitingForKeyframe = false;
        int index = availableInputs.pollFirst();
        try {
            ByteBuffer in = codec.getInputBuffer(index);
            if (in == null) return;
            in.clear();
            in.put(au.data);
            int flags = au.keyFrame ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
            codec.queueInputBuffer(index, 0, au.data.length, au.ptsMs * 1000L, flags);
        } catch (Exception e) {
            Log.e(TAG, "queueInputBuffer 失败", e);
            return;
        }
        // 自适应匀速：队列深时加速消耗积压帧，队列浅时保持 25fps
        int depth;
        synchronized (pendingAUs) { depth = pendingAUs.size(); }
        long interval = depth > BURST_DEPTH_THRESHOLD ? BURST_FRAME_INTERVAL_MS : FRAME_INTERVAL_MS;
        ensureFeedScheduled(interval);
    }

    /** 确保定时喂帧器在跑：延迟指定毫秒后触发 drainInputs。 */
    private void ensureFeedScheduled(long intervalMs) {
        Handler h = codecHandler;
        if (h == null || released) return;
        if (feedRunnable == null) feedRunnable = this::drainInputs;
        h.removeCallbacks(feedRunnable);
        h.postDelayed(feedRunnable, intervalMs);
    }

    /** 立即触发一次 drainInputs（feed 初次唤醒、input buffer 恢复时用）。 */
    private void kickFeed() {
        Handler h = codecHandler;
        if (h == null || released) return;
        if (feedRunnable == null) feedRunnable = this::drainInputs;
        h.removeCallbacks(feedRunnable);
        h.post(feedRunnable);
    }

    /** 取消待执行的喂帧定时器（flush/release 时调用）。 */
    private void cancelFeed() {
        Handler h = codecHandler;
        if (h == null || feedRunnable == null) return;
        h.removeCallbacks(feedRunnable);
    }

    private final MediaCodec.Callback callback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec c, int index) {
            availableInputs.addLast(index);
            // 不直接 drainInputs：避免 burst 时回调链一口气灌满 input buffer。
            // 仅在有 pending 帧且定时器未跑时唤醒。
            synchronized (pendingAUs) {
                if (!pendingAUs.isEmpty()) kickFeed();
            }
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec c, int index,
                                            @NonNull MediaCodec.BufferInfo info) {
            if (codec == null || released) {
                try { c.releaseOutputBuffer(index, false); } catch (Exception ignore) {}
                return;
            }
            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                // SPS/PPS 配置回显，不显示
                try { c.releaseOutputBuffer(index, false); } catch (Exception ignore) {}
                return;
            }
            long ptsUs = info.presentationTimeUs;
            long nowNs = System.nanoTime();
            long videoPtsMs = ptsUs / 1000;

            // ★ 音视频同步：优先用音频时钟锚定渲染时刻。
            //   AudioTrack.write(WRITE_BLOCKING) 天然按采样率匀速消费，是唯一可信的实时时钟。
            //   视频 PTS 与音频 PTS 同源（服务端统一打戳），差值即为"视频应提前/延后多少渲染"。
            //   audioClockBaselineMs 处理视频比音频晚到的场景：减去 baseline 使 PTS 对齐。
            LongSupplier clock = audioClockMs;
            long renderAtNs;
            if (clock != null) {
                long audioMs = clock.getAsLong();
                if (audioMs > 0) {
                    long effectiveAudioMs = audioMs - audioClockBaselineMs;
                    long diffMs = videoPtsMs - effectiveAudioMs;
                    renderAtNs = nowNs + diffMs * 1_000_000L;
                    if (DIAG && diagRenderCnt % 25 == 0) {
                        Log.d(TAG, "AUDIO_SYNC vPts=" + videoPtsMs
                                + " aPts=" + audioMs + " baseline=" + audioClockBaselineMs
                                + " effAudio=" + effectiveAudioMs + " diff=" + diffMs + "ms");
                    }
                } else {
                    // 音频尚未启动，回落到 PTS 自维护时钟
                    renderAtNs = fallbackRenderNs(ptsUs, nowNs);
                }
            } else {
                // 无音频时钟，PTS 自维护时钟
                renderAtNs = fallbackRenderNs(ptsUs, nowNs);
            }

            long targetNs = renderAtNs;
            try {
                if (targetNs < nowNs - LATE_DROP_NS) {
                    // 严重落后（在 STALL_GAP_NS 重置后不应再触发）：只丢显示，不断解码链
                    if (DIAG) {
                        diagDropCnt++;
                        Log.d(TAG, "RENDER DROP pts=" + (ptsUs / 1000)
                                + " lateMs=" + ((nowNs - targetNs) / 1_000_000L)
                                + " (drop#" + diagDropCnt + ")");
                    }
                    c.releaseOutputBuffer(index, false);
                } else {
                    if (DIAG) {
                        long leadMs = (renderAtNs - nowNs) / 1_000_000L;
                        long sinceLastMs = lastRenderNs < 0 ? -1 : (nowNs - lastRenderNs) / 1_000_000L;
                        lastRenderNs = nowNs;
                        diagRenderCnt++;
                        Log.d(TAG, "RENDER pts=" + (ptsUs / 1000)
                                + " leadMs=" + leadMs
                                + " sinceLastRenderMs=" + sinceLastMs
                                + " (render#" + diagRenderCnt + ")");
                    }
                    hasDecodedFirstFrame = true;
                    lastActualRenderNs = renderAtNs;
                    c.releaseOutputBuffer(index, renderAtNs);
                }
            } catch (Exception e) {
                Log.e(TAG, "releaseOutputBuffer 失败", e);
            }
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec c, @NonNull MediaFormat format) {
            int w = format.containsKey(MediaFormat.KEY_WIDTH) ? format.getInteger(MediaFormat.KEY_WIDTH) : DEFAULT_W;
            int h = format.containsKey(MediaFormat.KEY_HEIGHT) ? format.getInteger(MediaFormat.KEY_HEIGHT) : DEFAULT_H;
            // 优先用 crop 矩形得到真实显示尺寸
            if (format.containsKey("crop-left") && format.containsKey("crop-right")
                    && format.containsKey("crop-top") && format.containsKey("crop-bottom")) {
                int cl = format.getInteger("crop-left");
                int cr = format.getInteger("crop-right");
                int ct = format.getInteger("crop-top");
                int cb = format.getInteger("crop-bottom");
                w = cr - cl + 1;
                h = cb - ct + 1;
            }
            videoWidth = w;
            videoHeight = h;
            Log.d(TAG, "输出格式: " + w + "x" + h);
            updateTransformOnUi();
        }

        @Override
        public void onError(@NonNull MediaCodec c, @NonNull MediaCodec.CodecException e) {
            if (e.isTransient()) {
                Log.w(TAG, "解码器瞬时错误，忽略", e);
                return;
            }
            Log.e(TAG, "解码器错误 recoverable=" + e.isRecoverable(), e);
            if (e.isRecoverable()) {
                try {
                    c.stop();
                    c.start();
                    availableInputs.clear();
                    waitingForKeyframe = true;
                    baseRenderNs = -1;
                    basePtsUs = -1;
                    lastActualRenderNs = -1;
                } catch (Exception ex) {
                    Log.e(TAG, "recoverable 恢复失败", ex);
                    releaseCodecOnly();
                }
            } else {
                releaseCodecOnly();
            }
        }
    };

    /** 在 UI 线程按 center-crop 重算 TextureView 变换矩阵。 */
    public void updateTransformOnUi() {
        if (textureView == null) return;
        textureView.post(this::applyCenterCropTransform);
    }

    private void applyCenterCropTransform() {
        int vW = textureView.getWidth();
        int vH = textureView.getHeight();
        if (vW == 0 || vH == 0) return;
        float dW = videoWidth;
        float dH = videoHeight;
        if (dW <= 0 || dH <= 0) return;
        // center-crop：填满视图，超出部分裁掉
        float scale = Math.max(vW / dW, vH / dH);
        float sx = (dW * scale) / vW;
        float sy = (dH * scale) / vH;
        Matrix m = new Matrix();
        m.setScale(sx, sy, vW / 2f, vH / 2f);
        textureView.setTransform(m);
    }

    /**
     * PTS 自维护时钟（无音频或音频未启动时的回退方案）。
     * 首帧 pin 锚点，后续帧按 PTS 差值定时渲染。
     */
    private long fallbackRenderNs(long ptsUs, long nowNs) {
        if (baseRenderNs < 0) {
            baseRenderNs = nowNs + START_LATENCY_NS;
            basePtsUs = ptsUs;
        }
        if (lastActualRenderNs > 0) {
            long gapSinceRender = nowNs - lastActualRenderNs;
            if (gapSinceRender > STALL_GAP_NS) {
                baseRenderNs = nowNs + START_LATENCY_NS;
                basePtsUs = ptsUs;
            }
        }
        return baseRenderNs + (ptsUs - basePtsUs) * 1000L;
    }

    /** 仅释放 codec（错误路径用，不动线程/Surface）。 */
    private void releaseCodecOnly() {
        if (codec != null) {
            try { codec.stop(); } catch (Exception ignore) {}
            try { codec.release(); } catch (Exception ignore) {}
            codec = null;
        }
        released = true;
    }

    /** 完整释放：codec → HandlerThread → Surface。顺序重要（回调跑在该线程）。 */
    public synchronized void release() {
        released = true;
        cancelFeed();
        if (codec != null) {
            try { codec.flush(); } catch (Exception ignore) {}
            try { codec.stop(); } catch (Exception ignore) {}
            try { codec.release(); } catch (Exception ignore) {}
            codec = null;
        }
        if (codecThread != null) {
            codecThread.quitSafely();
            try { codecThread.join(200); } catch (InterruptedException ignore) {}
            codecThread = null;
            codecHandler = null;
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }
        synchronized (pendingAUs) {
            pendingAUs.clear();
        }
        availableInputs.clear();
    }
}
