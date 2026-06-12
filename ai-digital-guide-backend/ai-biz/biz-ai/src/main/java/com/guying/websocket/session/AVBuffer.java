package com.guying.websocket.session;

import com.guying.websocket.protocol.WsMessageSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.BinaryMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 音视频帧缓冲 + 匀速配对发送。
 * <p>
 * 策略：后端缓冲音频帧，等待 MuseTalk 视频帧就绪后，
 * 先匀速发送所有音频帧，再以 25fps 匀速发送视频帧。
 * <p>
 * 约束：
 * - 禁止以「视频帧迟迟未到」为由丢弃音频帧或触发超时
 * - 音视频配对以 sentence_id 为 key
 * - MuseTalk 推理耗时 3~4s 是正常现象
 */
@Slf4j
public class AVBuffer {

    private static final long VIDEO_FRAME_INTERVAL_MS = 40; // 25fps
    private static final long AUDIO_FRAME_INTERVAL_MS = 5;  // 音频帧间隔，防止 WebSocket 拥塞

    /** 每个 sentence 缓冲的音频帧（Android payload，含 type byte） */
    private final ConcurrentHashMap<Integer, List<byte[]>> pendingAudio = new ConcurrentHashMap<>();

    /**
     * 单线程发送器：保证同一 session 内所有帧按提交顺序、匀速发送。
     * 先提交的音频任务先执行，后提交的视频帧排队等待，自然实现「先音频后视频」。
     */
    private final ExecutorService senderExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "av-paced-sender");
        t.setDaemon(true);
        return t;
    });

    /**
     * 缓冲音频帧。CosyVoiceConnector 收到音频数据时调用。
     *
     * @param sentenceId     句子 ID
     * @param androidPayload 完整的 Android payload（含 type byte 0x01）
     */
    public void bufferAudio(int sentenceId, byte[] androidPayload) {
        pendingAudio.computeIfAbsent(sentenceId, k -> new ArrayList<>()).add(androidPayload);
        log.debug("[AVBuffer] 缓冲音频帧 sid={} size={}B", sentenceId, androidPayload.length);
    }

    /**
     * 提交配对发送任务。MuseTalkConnector 收到视频帧时调用。
     * <p>
     * 内部先 drain 出该 sentence 的所有缓冲音频帧并匀速发送，
     * 然后将当前视频帧以 25fps 匀速发送。
     * 所有发送在独立线程中异步执行，不阻塞调用线程。
     * <p>
     * 每个视频帧都调用此方法：首次调用会 drain 音频（pendingAudio.remove），
     * 后续调用 drain 返回空列表，仅发送视频帧。
     *
     * @param sentenceId     句子 ID
     * @param videoPayload   完整的 Android 视频 payload（含 type byte 0x03）
     * @param sender         WebSocket 消息发送器
     * @param ctx            会话上下文
     */
    public void submitPairedSend(int sentenceId, byte[] videoPayload,
                                  WsMessageSender sender, ChatSessionContext ctx) {
        // 取出并移除该 sentence 的音频帧（仅第一个视频帧触发 drain，后续返回空）
        List<byte[]> audioFrames = pendingAudio.remove(sentenceId);
        if (audioFrames == null) {
            audioFrames = List.of();
        }

        final List<byte[]> audioToSend = audioFrames;

        senderExecutor.submit(() -> {
            try {
                // 1. 先匀速发送所有音频帧
                for (byte[] audioFrame : audioToSend) {
                    if (!ctx.getUserSession().isOpen()) return;
                    sender.send(ctx, new BinaryMessage(audioFrame));
                    if (audioToSend.size() > 1) {
                        Thread.sleep(AUDIO_FRAME_INTERVAL_MS);
                    }
                }
                if (!audioToSend.isEmpty()) {
                    log.info("[AVBuffer] 音频发送完毕 sid={} frames={}", sentenceId, audioToSend.size());
                }

                // 2. 匀速发送当前视频帧（40ms 间隔实现 25fps）
                Thread.sleep(VIDEO_FRAME_INTERVAL_MS);
                if (!ctx.getUserSession().isOpen()) return;
                sender.sendVideoFrame(ctx, new BinaryMessage(videoPayload));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.debug("[AVBuffer] 发送线程被中断 sid={}", sentenceId);
            } catch (Exception e) {
                log.error("[AVBuffer] 发送异常 sid={}", sentenceId, e);
            }
        });
    }

    /**
     * 提交 done 消息到发送队列尾部。保证 done 在所有音视频帧发送完毕后才到达 Android。
     */
    public void submitDone(int sentenceId, WsMessageSender sender, ChatSessionContext ctx) {
        senderExecutor.submit(() -> {
            try {
                if (!ctx.getUserSession().isOpen()) return;
                sender.sendJson(ctx, "done", null);
                log.info("[AVBuffer] done 已发送 sid={}", sentenceId);
            } catch (Exception e) {
                log.error("[AVBuffer] done 发送异常 sid={}", sentenceId, e);
            }
        });
    }

    /**
     * 清除指定 sentence 的缓冲音频帧。interrupt 时调用。
     */
    public void clear(int sentenceId) {
        pendingAudio.remove(sentenceId);
        log.debug("[AVBuffer] 清除缓冲 sid={}", sentenceId);
    }

    /**
     * 清除所有缓冲。interrupt 时调用。
     */
    public void clearAll() {
        pendingAudio.clear();
        log.info("[AVBuffer] 清除所有缓冲");
    }
}
