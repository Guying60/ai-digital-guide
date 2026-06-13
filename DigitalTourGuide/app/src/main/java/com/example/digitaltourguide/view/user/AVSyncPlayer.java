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
    private static final int VIDEO_BUFFER_THRESHOLD = 25;  // 视频缓冲 1 秒（25fps）后开始播放

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

    // Global PTS offset: accumulated duration of all completed sentences (ms)
    private volatile long ptsOffsetMs = 0;
    private volatile long lastSentenceEndPtsMs = 0;

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
            if (interval > 100 || audioRecvCount % 50 == 0) {
                Log.w(TAG, "onAudioData: interval=" + interval + "ms queueSize=" + audioQueue.size() + " total=" + audioRecvCount);
            }
        }
    }

    /**
     * Video data: [sentenceId:2B][ptsMs:4B][keyFrame:1B][H.264 AU:N]
     * ChatActivity already stripped the type byte.
     */
    private static final int VIDEO_QUEUE_MAX = 80;  // burst 上限保护，丢弃最旧帧防内存暴涨

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
                Log.w(TAG, "onVideoData: interval=" + interval + "ms queueSize=" + videoQueue.size() + " total=" + videoRecvCount);
            }
        }
        // 预缓冲 VIDEO_BUFFER_THRESHOLD 帧后再启动播放，吸收网络抖动和 GPU 推理间隙
        if (!playbackStarted && !released && videoQueue.size() >= VIDEO_BUFFER_THRESHOLD) {
            if (LOG) Log.i(TAG, "onVideoData: start playback after buffering " + videoQueue.size() + " frames");
            startPlayback();
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
        // 停止 AudioTrack，解除 write() 阻塞
        try { audioTrack.pause(); } catch (Exception ignored) {}
        try { audioTrack.flush(); } catch (Exception ignored) {}
        // 等待音频线程退出（flush 后 write() 应快速返回，最多等 2s）
        Thread t = audioThread;
        if (t != null) {
            t.interrupt();
            for (int i = 0; i < 10 && t.isAlive(); i++) {
                try { t.join(200); } catch (InterruptedException ignored) {}
            }
            if (t.isAlive()) {
                Log.w(TAG, "onConversationEnd: audio thread still alive, detaching");
                audioThread = null;
            }
        }
        releaseDecoder();
        ptsOffsetMs = 0;
        lastSentenceEndPtsMs = 0;
        playbackStarted = false;  // 允许下次对话重新启动播放
        videoRecvCount = 0;
        audioRecvCount = 0;
        lastVideoRecvTime = 0;
        lastAudioRecvTime = 0;
    }

    /**
     * Update PTS offset when a sentence is done.
     * 播放启动由 onVideoData 负责（收到第一帧视频时触发），
     * 此处仅更新 PTS 偏移，不触发播放。
     */
    public void onSentenceDone(int sentenceId) {
        ptsOffsetMs += lastSentenceEndPtsMs + FRAME_INTERVAL_MS;
        lastSentenceEndPtsMs = 0;
        if (LOG) Log.i(TAG, "onSentenceDone: sid=" + sentenceId + " ptsOffset=" + ptsOffsetMs
                + " audioQueueSize=" + audioQueue.size() + " videoQueueSize=" + videoQueue.size()
                + " playbackStarted=" + playbackStarted);
    }

    public void interrupt() {
        if (LOG) Log.i(TAG, "interrupt");
        generation++;  // ★ 通知运行中的音视频线程立即退出
        audioQueue.clear();
        videoQueue.clear();
        // 停止 AudioTrack，解除 write() 阻塞
        try { audioTrack.pause(); } catch (Exception ignored) {}
        try { audioTrack.flush(); } catch (Exception ignored) {}
        // 等待音频线程退出，避免后续操作时 write() 仍在 native 层
        Thread t = audioThread;
        if (t != null) {
            t.interrupt();
            for (int i = 0; i < 10 && t.isAlive(); i++) {
                try { t.join(200); } catch (Exception ignored) {}
            }
            if (t.isAlive()) {
                Log.w(TAG, "interrupt: audio thread still alive, detaching");
                audioThread = null;
            }
        }
        releaseDecoder();
        ptsOffsetMs = 0;
        lastSentenceEndPtsMs = 0;
        playbackStarted = false;
        videoRecvCount = 0;
        audioRecvCount = 0;
        lastVideoRecvTime = 0;
        lastAudioRecvTime = 0;
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
        if (playbackStarted) return;
        playbackStarted = true;

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
        while (!released && generation == myGen) {  // ★ gen 变化立即退出
            byte[] pkt = audioQueue.poll();
            if (pkt == null) {
                // 队列空时写 1 帧静音 + sleep 40ms，保持 AudioTrack 缓冲区连续但不填满，
                // 避免后续真实音频 write() 因缓冲区满而长时间阻塞
                if (released) break;
                try {
                    audioTrack.write(silenceFrame, 0, silenceFrame.length);
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
                long tWrite = System.currentTimeMillis();
                try {
                    int written = audioTrack.write(pcm, 0, pcm.length);
                    long writeCost = System.currentTimeMillis() - tWrite;
                    audioWriteCount++;
                    if (LOG && (writeCost > 20 || audioWriteCount % 100 == 0)) {
                        Log.w(TAG, "audioWrite: cost=" + writeCost + "ms pcmLen=" + pcm.length + " written=" + written + " total=" + audioWriteCount);
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

        while (!released) {
            // ★ 检测代际变化：新的对话已开始，释放旧 codec 并重置等待新关键帧
            if (myGen != generation) {
                if (LOG) Log.i(TAG, "videoFeedLoop: generation changed " + myGen + "→" + generation + ", resetting codec");
                myGen = generation;
                if (c != null) {
                    try { c.stop(); } catch (Exception ignored) {}
                    try { c.release(); } catch (Exception ignored) {}
                    c = null;
                }
                // 不要 release h264Decoder — 它可能已被外部 releaseDecoder() 设为 null
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

                // Feed the first keyframe
                long tFirstFeed = System.currentTimeMillis();
                int inIdx = c.dequeueInputBuffer(50_000);
                long firstDequeueCost = System.currentTimeMillis() - tFirstFeed;
                if (inIdx >= 0) {
                    ByteBuffer inBuf = c.getInputBuffer(inIdx);
                    if (inBuf != null) {
                        inBuf.clear();
                        inBuf.put(avData);
                        c.queueInputBuffer(inIdx, 0, avData.length, 0,
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
                    if (LOG && (dequeueOutCost > 10 || renderCost > 10)) {
                        Log.w(TAG, "videoDecode: dequeueOut=" + dequeueOutCost + "ms render=" + renderCost + "ms outSize=" + outInfo.size);
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

            // Track max PTS per sentence
            if (ptsMs > lastSentenceEndPtsMs) {
                lastSentenceEndPtsMs = ptsMs;
            }

            // Apply global PTS offset
            long globalPtsUs = (ptsMs + ptsOffsetMs) * 1000;
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
                    if (LOG && (dequeueInCost > 10 || queueCost > 5)) {
                        Log.w(TAG, "videoFeed: dequeueIn=" + dequeueInCost + "ms queueIn=" + queueCost
                                + "ms size=" + avData.length + " key=" + keyFrame + " qSize=" + videoQueue.size());
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
        format.setInteger(MediaFormat.KEY_PRIORITY, 0); // realtime priority

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
