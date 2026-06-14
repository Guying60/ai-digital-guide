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

import android.graphics.Matrix;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class AVSyncPlayer {

    private static final String TAG = "AVSyncPlayer";
    private static final boolean LOG = true; // TODO: set false for release

    private static final int AUDIO_SAMPLE_RATE = 16000;
    private static final int AUDIO_CHANNEL = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    // MuseTalk server: 25fps → 40ms per frame
    private static final long FRAME_INTERVAL_MS = 40;
    private static final int VIDEO_BUFFER_THRESHOLD = 18;  // 视频缓冲 3 秒（25fps）吸收后端批量发送间隙

    // Data format (type byte already stripped by ChatActivity):
    // Audio: [sentenceId:2B][ptsMs:4B][PCM:N]  → header = 6 bytes
    // Video: [sentenceId:2B][ptsMs:4B][keyFrame:1B][H.264:N] → header = 7 bytes
    private static final int AUDIO_HDR = 6;
    private static final int VIDEO_HDR = 7;
    private static final int H264_W = 720;
    private static final int H264_H = 1280;

    private final TextureView textureView;
    private Consumer<String> subtitleCallback;

    private final AudioTrack audioTrack;
    private volatile MediaCodec h264Decoder;
    private volatile Surface surface;
    private final CountDownLatch surfaceReady = new CountDownLatch(1);

    private final LinkedBlockingQueue<byte[]> videoQueue = new LinkedBlockingQueue<>();
    private final ConcurrentLinkedQueue<byte[]> audioQueue = new ConcurrentLinkedQueue<>();

    private volatile boolean released = false;
    private volatile boolean playbackStarted = false;  // 播放线程是否已启动
    private volatile long generation = 0;  // 对话代际计数器，用于安全重启播放线程

    // ---- 帧接收 & 解码耗时统计 ----
    private long lastVideoRecvTime = 0;
    private long lastAudioRecvTime = 0;
    private int videoRecvCount = 0;
    private int audioRecvCount = 0;
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

    private final ExecutorService decoderExecutor = Executors.newSingleThreadExecutor();
    private volatile Thread audioThread;  // 用于安全停止音频线程

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
        this.surface = surface;
        surfaceReady.countDown();
        applyAspectRatio();
        if (LOG) Log.i(TAG, "Surface ready");
    }

    public void onSurfaceDestroyed() {
        this.surface = null;
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

        float videoAspect = (float) H264_W / H264_H; // 720/1280 = 0.5625
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
                + " video=" + H264_W + "x" + H264_H + " scale=" + scaleX + "x" + scaleY);
    }

    public boolean isPlaying() {
        return playbackStarted;
    }

    /**
     * Audio data: [sentenceId:2B][ptsMs:4B][PCM:N]
     * ChatActivity already stripped the type byte.
     */
    public void onAudioData(byte[] frameData) {
        if (released || frameData == null || frameData.length <= AUDIO_HDR) return;
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
                long ptsMs = ((long)(frameData[2]&0xFF)<<24) | ((long)(frameData[3]&0xFF)<<16)
                           | ((long)(frameData[4]&0xFF)<<8) | (frameData[5]&0xFF);
                Log.w(TAG, "onAudioData: interval=" + interval + "ms pts=" + ptsMs
                        + " pcmLen=" + pcmLen + " q=" + audioQueue.size()
                        + " total=" + audioRecvCount + " recvDur=" + recvDurationMs + "ms");
            }
        }
    }

    /**
     * Video data: [sentenceId:2B][ptsMs:4B][keyFrame:1B][H.264 AU:N]
     * ChatActivity already stripped the type byte.
     */
    private static final int VIDEO_QUEUE_MAX = 350;  // 8 秒 burst 上限保护，丢弃最旧帧防内存暴涨

    public void onVideoData(byte[] frameData) {
        if (released || frameData == null || frameData.length <= VIDEO_HDR) return;
        // 队列满时丢弃最旧的帧，防止 burst 导致内存暴涨
        while (videoQueue.size() >= VIDEO_QUEUE_MAX) {
            videoQueue.poll();
        }
        videoQueue.offer(frameData);
        if (LOG) {
            long now = System.currentTimeMillis();
            long interval = lastVideoRecvTime == 0 ? 0 : now - lastVideoRecvTime;
            lastVideoRecvTime = now;
            videoRecvCount++;
            if (interval > 100 || videoRecvCount % 20 == 0) {
                long ptsMs = ((long)(frameData[2]&0xFF)<<24) | ((long)(frameData[3]&0xFF)<<16)
                           | ((long)(frameData[4]&0xFF)<<8) | (frameData[5]&0xFF);
                Log.w(TAG, "onVideoData: interval=" + interval + "ms pts=" + ptsMs + " queueSize=" + videoQueue.size() + " total=" + videoRecvCount);
            }
        }
        // 预缓冲 VIDEO_BUFFER_THRESHOLD 帧后再启动播放，吸收网络抖动和 GPU 推理间隙
        // ★ 在 WebSocket 回调线程上仅标记状态并提交任务，绝不可在此线程上调用 startPlayback()
        // 否则 join/write 会阻塞整个 WebSocket 管道，导致后续帧无法入队
        if (!playbackStarted && !released && videoQueue.size() >= VIDEO_BUFFER_THRESHOLD) {
            playbackStarted = true;  // 提前标记，避免重复提交
            if (LOG) Log.i(TAG, "onVideoData: start playback after buffering " + videoQueue.size() + " frames");
            decoderExecutor.submit(this::startPlayback);
        }
    }

    /**
     * 对话结束时调用，清空队列，释放解码器，准备下一次对话
     */
    public void onConversationEnd() {
        if (LOG) Log.i(TAG, "onConversationEnd: clearing queues");
        generation++;  // ★ 通知运行中的音视频线程立即退出
        audioQueue.clear();
        videoQueue.clear();
        // 停止 AudioTrack，解除 write() 阻塞（pause+flush 是瞬间操作）
        try { audioTrack.pause(); } catch (Exception ignored) {}
        try { audioTrack.flush(); } catch (Exception ignored) {}
        // ★ generation++ 已足够让旧线程退出循环，不再 join 等待阻塞调用线程
        // startPlayback() 会处理残余线程的清理
        releaseDecoder();
        playbackStarted = false;  // 允许下次对话重新启动播放
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
    }

    /**
     * Sentence done notification from backend.
     * PTS 全局化已由后端完成，此处仅记录日志，不再维护偏移量。
     */
    public void onSentenceDone(int sentenceId) {
        if (LOG) Log.i(TAG, "onSentenceDone: sid=" + sentenceId
                + " audioQueueSize=" + audioQueue.size() + " videoQueueSize=" + videoQueue.size()
                + " playbackStarted=" + playbackStarted);
    }

    public void interrupt() {
        if (LOG) Log.i(TAG, "interrupt");
        generation++;  // ★ 通知运行中的音视频线程立即退出
        audioQueue.clear();
        videoQueue.clear();
        // 停止 AudioTrack，解除 write() 阻塞（pause+flush 是瞬间操作）
        try { audioTrack.pause(); } catch (Exception ignored) {}
        try { audioTrack.flush(); } catch (Exception ignored) {}
        // ★ generation++ 已足够让旧线程退出循环，不再 join 等待阻塞调用线程
        releaseDecoder();
        playbackStarted = false;
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
    }

    public void release() {
        if (released) return;
        released = true;
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
        decoderExecutor.shutdownNow();
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

        // 预填充 2 帧静音（80ms），防止启动瞬间 underrun 产生电流声，同时不过度阻塞后续真实音频写入
        for (int i = 0; i < 2; i++) {
            audioTrack.write(silenceFrame, 0, silenceFrame.length);
        }
        audioTrack.play();
        decoderExecutor.submit(this::videoFeedLoop);
        new Thread(this::audioPlayLoop, "av-audio").start();
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
                long audioPtsMs = ((long)(pkt[2]&0xFF)<<24) | ((long)(pkt[3]&0xFF)<<16)
                                | ((long)(pkt[4]&0xFF)<<8) | (pkt[5]&0xFF);
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
                    int written = audioTrack.write(pcm, 0, pcm.length);
                    long writeCost = System.currentTimeMillis() - tWrite;
                    audioWriteCount++;
                    audioTotalSamples += written / 2;  // 16-bit mono = 2 bytes/sample

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
        try {
            surfaceReady.await();
        } catch (InterruptedException e) {
            if (LOG) Log.w(TAG, "videoFeedLoop: interrupted waiting for surface");
            return;
        }
        if (released || surface == null) {
            if (LOG) Log.w(TAG, "videoFeedLoop: surface not ready, released=" + released + " surface=" + surface);
            return;
        }
        if (LOG) Log.i(TAG, "videoFeedLoop: surface ready, entering main loop, queueSize=" + videoQueue.size());

        MediaCodec c = null;
        long[] spsPpsLen = new long[2];
        long lastRenderTime = 0;
        int videoFeedCount = 0;
        int videoRenderCount = 0;
        long videoStartWallMs = System.currentTimeMillis();

        while (!released) {
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

                if (pkt.length < VIDEO_HDR + 4) {
                    if (LOG) Log.w(TAG, "videoFeedLoop: packet too small: " + pkt.length);
                    continue;
                }
                boolean keyFrame = (pkt[6] & 0xFF) == 1;
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
                long firstPtsMs = ((long) (pkt[2] & 0xFF) << 24) | ((long) (pkt[3] & 0xFF) << 16)
                        | ((long) (pkt[4] & 0xFF) << 8) | (pkt[5] & 0xFF);
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
                        Log.w(TAG, "╔══ SYNC-REPORT @" + videoRenderCount + " frames ══╗");
                        Log.w(TAG, "║ audio:  pts=" + aPts + " globalPts=" + aGlobal
                            + " (age=" + audioAge + "ms) aQ=" + audioQueue.size());
                        Log.w(TAG, "║ video:  feedPts=" + vFeedPts + " feedGlobal=" + vFeedGlobal
                            + " (age=" + videoFeedAge + "ms)");
                        Log.w(TAG, "║ render: pts=" + vRenderPts
                            + " (age=" + videoRenderAge + "ms) vQ=" + videoQueue.size());
                        Log.w(TAG, "║ Δ(audioGlobal-videoFeedGlobal)=" + (aGlobal - vFeedGlobal) + "ms"
                            + " Δ(audioGlobal-videoRender)=" + (aGlobal - vRenderPts) + "ms");
                        Log.w(TAG, "╚══════════════════════════════════╝");
                    }
                }
            } else if (LOG && dequeueOutCost > 50) {
                Log.w(TAG, "videoDecode: dequeueOut TIMEOUT " + dequeueOutCost + "ms outIdx=" + outIdx);
            }

            // ③ Feed next input frame（尽快喂给解码器，不做 25fps 节流）
            // 使用 peek 而非 poll：先检查是否有帧可用，等 codec 确认可接受后再从队列移除，
            // 避免 dequeueInputBuffer 超时时帧被丢弃。
            byte[] pkt = videoQueue.peek(); // non-blocking peek
            if (pkt == null) {
                try { Thread.sleep(10); } catch (InterruptedException e) { return; }
                continue;
            }
            if (LOG) Log.d(TAG, "videoFeedLoop: feed frame, queueSize=" + videoQueue.size());

            if (pkt.length < VIDEO_HDR + 4) {
                videoQueue.poll(); // malformed, discard
                continue;
            }
            // [sentenceId:2B][ptsMs:4B][keyFrame:1B][H.264:N]
            long ptsMs = ((long) (pkt[2] & 0xFF) << 24) | ((long) (pkt[3] & 0xFF) << 16)
                    | ((long) (pkt[4] & 0xFF) << 8) | (pkt[5] & 0xFF);
            boolean keyFrame = (pkt[6] & 0xFF) == 1;
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
            if (inIdx >= 0) {
                // Codec 可接受输入 — 从队列移除该帧并喂入
                videoQueue.poll();
                ByteBuffer inBuf = c.getInputBuffer(inIdx);
                if (inBuf != null) {
                    inBuf.clear();
                    inBuf.put(avData);
                    long tQueueStart = System.currentTimeMillis();
                    c.queueInputBuffer(inIdx, 0, avData.length, globalPtsUs, flags);
                    long queueCost = System.currentTimeMillis() - tQueueStart;
                    videoFeedCount++;
                    diagVideoFeedPtsMs = ptsMs;
                    diagVideoFeedGlobalPtsMs = videoGlobalPtsMs;
                    diagVideoFeedWallMs = System.currentTimeMillis();

                    if (LOG && (dequeueInCost > 10 || queueCost > 5 || videoFeedCount % 25 == 0)) {
                        Log.w(TAG, "▶ VIDEO-FEED: pts=" + ptsMs + " globalPts=" + videoGlobalPtsMs
                            + " deqIn=" + dequeueInCost + "ms qIn=" + queueCost
                            + "ms size=" + avData.length + " key=" + keyFrame
                            + " q=" + videoQueue.size() + " #" + videoFeedCount);
                    }
                }
            } else {
                // Codec 输入缓冲区满，帧保留在队列中，下次循环重试
                if (LOG) Log.w(TAG, "videoFeed: codec input full, frame stays in queue, dequeueInCost=" + dequeueInCost + "ms");
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
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, H264_W, H264_H);
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
