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
import java.util.function.Consumer;

public class AVSyncPlayer {

    private static final String TAG = "AVSyncPlayer";
    private static final boolean LOG = true; // TODO: set false for release

    private static final int AUDIO_SAMPLE_RATE = 16000;
    private static final int AUDIO_CHANNEL = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    // MuseTalk server: 25fps → 40ms per frame
    private static final long FRAME_INTERVAL_MS = 40;
    private static final int MAX_QUEUE_SIZE = 500;

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

    private final ConcurrentLinkedQueue<byte[]> videoQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<byte[]> audioQueue = new ConcurrentLinkedQueue<>();
    private int videoQueueSize = 0;

    private volatile boolean playing = false;
    private volatile boolean released = false;
    private volatile boolean interrupted = false;

    // Global PTS offset: accumulated duration of all completed sentences (ms)
    private volatile long ptsOffsetMs = 0;
    private volatile long lastSentenceEndPtsMs = 0;

    private final ExecutorService decoderExecutor = Executors.newSingleThreadExecutor();

    public AVSyncPlayer(TextureView textureView) {
        this.textureView = textureView;
        int bufSize = AudioTrack.getMinBufferSize(AUDIO_SAMPLE_RATE, AUDIO_CHANNEL, AUDIO_FORMAT);
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
                bufSize, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
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
        return playing;
    }

    /**
     * Audio data: [sentenceId:2B][ptsMs:4B][PCM:N]
     * ChatActivity already stripped the type byte.
     */
    public void onAudioData(byte[] frameData) {
        if (released || frameData == null || frameData.length <= AUDIO_HDR) return;
        audioQueue.offer(frameData);
    }

    /**
     * Video data: [sentenceId:2B][ptsMs:4B][keyFrame:1B][H.264 AU:N]
     * ChatActivity already stripped the type byte.
     */
    public void onVideoData(byte[] frameData) {
        if (released || frameData == null || frameData.length <= VIDEO_HDR) return;
        if (videoQueueSize >= MAX_QUEUE_SIZE) {
            videoQueue.poll();
            videoQueueSize--;
        }
        videoQueue.offer(frameData);
        videoQueueSize++;
    }

    public void onSentenceDone(int sentenceId) {
        // Update PTS offset for the next sentence
        ptsOffsetMs += lastSentenceEndPtsMs + FRAME_INTERVAL_MS;
        lastSentenceEndPtsMs = 0;
        if (LOG) Log.i(TAG, "onSentenceDone: sid=" + sentenceId + " ptsOffset=" + ptsOffsetMs);

        // Auto-start playback when first sentence is done
        if (!playing && !released) {
            new Thread(this::startPlayback, "av-playback").start();
        }
    }

    public void interrupt() {
        interrupted = true;
        videoQueue.clear();
        audioQueue.clear();
        videoQueueSize = 0;
        if (LOG) Log.i(TAG, "interrupt");
    }

    public void release() {
        if (released) return;
        released = true;
        playing = false;
        decoderExecutor.shutdownNow();
        releaseDecoder();
        audioTrack.flush();
        audioTrack.release();
    }

    // ====================== PLAYBACK CONTROL ======================

    /**
     * Start playback. Called when enough data has been buffered.
     * Runs on calling thread — starts decode thread, then enters main audio loop.
     */
    public void startPlayback() {
        if (playing) return;
        playing = true;
        audioTrack.play();
        decoderExecutor.submit(this::videoFeedLoop);
        // Main thread runs audio playback
        audioPlayLoop();
    }

    // ====================== AUDIO PLAYBACK (main thread) ======================

    private void audioPlayLoop() {
        while (playing && !released) {
            if (interrupted) {
                drainAudioQueue();
                interrupted = false;
            }
            byte[] pkt = audioQueue.poll();
            if (pkt == null) {
                if (videoQueue.isEmpty() && audioQueue.isEmpty()) {
                    // No more data — check if we should stop
                    try { Thread.sleep(10); } catch (InterruptedException e) { break; }
                    if (videoQueue.isEmpty() && audioQueue.isEmpty()) {
                        playing = false;
                        break;
                    }
                }
                try { Thread.sleep(2); } catch (InterruptedException e) { break; }
                continue;
            }
            if (pkt.length > AUDIO_HDR) {
                byte[] pcm = Arrays.copyOfRange(pkt, AUDIO_HDR, pkt.length);
                audioTrack.write(pcm, 0, pcm.length);
            }
        }
        if (LOG) Log.i(TAG, "audioPlayLoop: end");
    }

    private void drainAudioQueue() {
        while (!audioQueue.isEmpty()) {
            audioQueue.poll();
        }
    }

    // ====================== VIDEO DECODE & RENDER (decoder thread) ======================

    private void videoFeedLoop() {
        if (LOG) Log.i(TAG, "videoFeedLoop: waiting for surface");
        try {
            surfaceReady.await();
        } catch (InterruptedException e) {
            return;
        }
        if (released || surface == null) return;

        MediaCodec c = null;
        long[] spsPpsLen = new long[2];
        long lastRenderTime = 0;

        while (playing && !released) {
            // ① Codec not ready: wait for keyframe with SPS/PPS
            if (c == null) {
                byte[] pkt = videoQueue.poll();
                if (pkt == null) {
                    try { Thread.sleep(5); } catch (InterruptedException e) { return; }
                    continue;
                }
                videoQueueSize = Math.max(0, videoQueueSize - 1);

                if (pkt.length < VIDEO_HDR + 4) continue;
                boolean keyFrame = (pkt[6] & 0xFF) == 1;
                if (!keyFrame) continue;

                byte[] avData = Arrays.copyOfRange(pkt, VIDEO_HDR, pkt.length);
                byte[] sps = extractNal(avData, 7);
                byte[] pps = extractNal(avData, 8);
                if (sps == null || pps == null) {
                    if (LOG) Log.w(TAG, "No SPS/PPS in keyframe, skip");
                    continue;
                }

                c = createCodec(sps, pps, surface);
                if (c == null) {
                    if (LOG) Log.e(TAG, "Failed to create codec");
                    continue;
                }
                h264Decoder = c;
                spsPpsLen[0] = sps.length;
                spsPpsLen[1] = pps.length;
                if (LOG) Log.i(TAG, "Codec created, SPS=" + sps.length + " PPS=" + pps.length);

                // Feed the first keyframe
                int inIdx = c.dequeueInputBuffer(50_000);
                if (inIdx >= 0) {
                    ByteBuffer inBuf = c.getInputBuffer(inIdx);
                    if (inBuf != null) {
                        inBuf.clear();
                        inBuf.put(avData);
                        c.queueInputBuffer(inIdx, 0, avData.length, 0,
                                MediaCodec.BUFFER_FLAG_KEY_FRAME);
                    }
                }
                lastRenderTime = System.currentTimeMillis();
                continue;
            }

            // ② Render output first (with 25fps pacing)
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            int outIdx = c.dequeueOutputBuffer(outInfo, 5_000); // 5ms timeout (microseconds)
            if (outIdx >= 0) {
                if ((outInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    c.releaseOutputBuffer(outIdx, false);
                } else {
                    long now = System.currentTimeMillis();
                    long elapsed = now - lastRenderTime;
                    if (elapsed < FRAME_INTERVAL_MS) {
                        try { Thread.sleep(FRAME_INTERVAL_MS - elapsed); } catch (InterruptedException e) { return; }
                    }
                    c.releaseOutputBuffer(outIdx, true);
                    lastRenderTime = System.currentTimeMillis();
                }
            }

            // ③ Feed next input frame
            byte[] pkt = videoQueue.poll();
            if (pkt == null) continue;
            videoQueueSize = Math.max(0, videoQueueSize - 1);

            if (pkt.length < VIDEO_HDR + 4) continue;
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
            int inIdx = c.dequeueInputBuffer(50_000);
            if (inIdx >= 0) {
                ByteBuffer inBuf = c.getInputBuffer(inIdx);
                if (inBuf != null) {
                    inBuf.clear();
                    inBuf.put(avData);
                    c.queueInputBuffer(inIdx, 0, avData.length, globalPtsUs, flags);
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
