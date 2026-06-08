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

/**
 * H.264 硬解码器：MediaCodec(async 模式) 直渲到 TextureView 的 Surface。
 *
 * 取代旧的 MJPEG 软解(BitmapFactory)+ImageView 渲染路径。WebSocket 读线程只负责
 * {@link #feed(AccessUnit)} 把 access unit 推入有界队列，绝不阻塞；所有 MediaCodec
 * 调用都在独立的 "H264Decoder" HandlerThread 上串行执行。
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

    /** 队列上限 ≈ 2s@25fps，满则按 GOP 丢弃到下个关键帧，避免断参考链。 */
    private static final int MAX_PENDING_AUS = 50;
    /** 启播优先级延迟：首帧渲染稍微滞后，给后续帧留缓冲。 */
    private static final long START_LATENCY_NS = 150_000_000L; // 150ms（原80ms，容忍后端推理抖动）
    /** 严重落后阈值：超过则只丢显示不丢解码。 */
    private static final long LATE_DROP_NS = 300_000_000L; // 300ms（原80ms，容忍 burst 到达）

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
    /** 上一帧实际释放时刻（用于 burst 平滑），仅 codecThread 访问 */
    private long lastActualRenderNs = -1;
    /** 最小渲染间隔：burst 时避免帧"快进"闪过 */
    private static final long MIN_RENDER_INTERVAL_NS = 30_000_000L; // 30ms ≈ 33fps

    // ==== 临时诊断计测（定位卡顿用，验证后移除）====
    private static final boolean DIAG = true;
    private volatile long lastFeedNs = -1;   // 上一帧到达时刻（WS 线程）
    private long lastRenderNs = -1;          // 上一帧 release 时刻（codecThread）
    private int diagRenderCnt = 0;
    private int diagDropCnt = 0;

    // 显示尺寸（onOutputFormatChanged 后更新），用于 center-crop 变换
    private volatile int videoWidth = DEFAULT_W;
    private volatile int videoHeight = DEFAULT_H;

    public H264VideoDecoder(TextureView textureView) {
        this.textureView = textureView;
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

    /** 是否成功启用硬解（供 ChatActivity 决定是否回落 JPEG）。 */
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
        Handler h = codecHandler;
        if (h != null) h.post(this::drainInputs);
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
        MediaCodec c = codec;
        if (c != null) {
            try {
                // flush 清空 codec 内部输入/输出缓冲，但不重置 baseRenderNs/basePtsUs
                c.flush();
            } catch (Exception e) {
                Log.e(TAG, "sentenceTransitionFlush flush codec 失败", e);
            }
        }
        waitingForKeyframe = true;
        // 只 flush 了 codec 缓冲（必须），没有重置 baseRenderNs/basePtsUs → 渲染时钟跨句连续
    }

    /** 仅在 codecThread 执行：把可用 input buffer 喂满待发 AU。 */
    private void drainInputs() {
        if (codec == null || released) return;
        while (!availableInputs.isEmpty()) {
            AccessUnit au;
            synchronized (pendingAUs) {
                au = pendingAUs.peekFirst();
                if (au == null) return; // 暂无数据，保留 input index
                if (waitingForKeyframe && !au.keyFrame) {
                    pendingAUs.pollFirst(); // 丢弃 IDR 之前的残缺帧，防花屏
                    continue;
                }
                pendingAUs.pollFirst();
            }
            waitingForKeyframe = false;
            int index = availableInputs.pollFirst();
            try {
                ByteBuffer in = codec.getInputBuffer(index);
                if (in == null) continue;
                in.clear();
                in.put(au.data);
                int flags = au.keyFrame ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                codec.queueInputBuffer(index, 0, au.data.length, au.ptsMs * 1000L, flags);
            } catch (Exception e) {
                Log.e(TAG, "queueInputBuffer 失败", e);
                return;
            }
        }
    }

    private final MediaCodec.Callback callback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec c, int index) {
            availableInputs.addLast(index);
            drainInputs();
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
            if (baseRenderNs < 0) {
                baseRenderNs = nowNs + START_LATENCY_NS;
                basePtsUs = ptsUs;
            }
            long targetNs = baseRenderNs + (ptsUs - basePtsUs) * 1000L;
            try {
                if (targetNs < nowNs - LATE_DROP_NS) {
                    // 严重落后：只丢显示，不断解码链
                    if (DIAG) {
                        diagDropCnt++;
                        Log.d(TAG, "RENDER DROP pts=" + (ptsUs / 1000)
                                + " lateMs=" + ((nowNs - targetNs) / 1_000_000L)
                                + " (drop#" + diagDropCnt + ")");
                    }
                    c.releaseOutputBuffer(index, false);
                } else {
                    // burst 平滑：如果距上次渲染间隔太短，推迟释放，避免帧"快进"闪过
                    long renderAtNs = targetNs;
                    if (lastActualRenderNs > 0) {
                        long gapSinceLastRender = nowNs - lastActualRenderNs;
                        if (gapSinceLastRender < MIN_RENDER_INTERVAL_NS) {
                            // 上一帧刚渲染不到 30ms，推迟这一帧
                            long boostedTarget = lastActualRenderNs + MIN_RENDER_INTERVAL_NS;
                            if (boostedTarget > renderAtNs) {
                                renderAtNs = boostedTarget;
                            }
                        }
                    }
                    if (DIAG) {
                        long leadMs = (renderAtNs - nowNs) / 1_000_000L;
                        long sinceLastMs = lastRenderNs < 0 ? -1 : (nowNs - lastRenderNs) / 1_000_000L;
                        lastRenderNs = nowNs;
                        diagRenderCnt++;
                        if (renderAtNs != targetNs) {
                            Log.d(TAG, "RENDER pts=" + (ptsUs / 1000)
                                    + " leadMs=" + leadMs
                                    + " sinceLastRenderMs=" + sinceLastMs
                                    + " BURST_SMOOTHED"
                                    + " (render#" + diagRenderCnt + ")");
                        } else {
                            Log.d(TAG, "RENDER pts=" + (ptsUs / 1000)
                                    + " leadMs=" + leadMs
                                    + " sinceLastRenderMs=" + sinceLastMs
                                    + " (render#" + diagRenderCnt + ")");
                        }
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
