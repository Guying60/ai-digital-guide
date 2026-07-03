package com.guying.websocket.session;

import com.alibaba.nls.client.protocol.asr.SpeechTranscriber;
import com.guying.utils.AudioFrameBuffer;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 单个 WebSocket 会话的所有运行时状态。
 * 替代原 AiChatHandler 中 9 个并行的 ConcurrentHashMap。
 */
@Getter
public class ChatSessionContext {

    private final WebSocketSession userSession;
    private final String sid;
    private final Long userId;
    private final Long attractionId;
    private final String conversationId;
    private final ReentrantLock sendLock = new ReentrantLock();
    private final AudioFrameBuffer audioBuffer = new AudioFrameBuffer();
    private final PtsTracker ptsTracker = new PtsTracker();

    /** 本轮对话的句子总数（responseDone 时由 AiChatService 写入；-1 表示进行中/未知）。 */
    private final AtomicInteger roundSentenceTotal = new AtomicInteger(-1);
    /** 本轮已收到 chunk_end 的句子计数（CosyVoiceConnector 递增），用于判定"最后一句"补静音收口。 */
    private final AtomicInteger roundChunkEndCount = new AtomicInteger(0);


    @Setter private volatile WebSocketSession museTalkSession;
    @Setter private volatile WebSocketSession cosyVoiceSession;
    @Setter private volatile SpeechTranscriber transcriber;
    @Setter private volatile ExecutorService ttsExecutor;
    /** 按 PTS 时钟节流的视频出站发送器，afterConnectionEstablished 创建。 */
    @Setter private volatile OutboundPacer outboundPacer;

    private volatile long lastActiveTime;

    public ChatSessionContext(WebSocketSession session) {
        this.userSession = session;
        this.sid = session.getId();
        this.userId = (Long) session.getAttributes().get("userId");
        this.attractionId = (Long) session.getAttributes().get("attractionId");
        this.conversationId = UUID.randomUUID().toString();
        this.lastActiveTime = System.currentTimeMillis();
    }

    public String conversationId() {
        return conversationId;
    }

    public void touchActive() {
        this.lastActiveTime = System.currentTimeMillis();
    }

    /** 新一轮对话开始 / 打断时复位句末补静音的轮次计数。 */
    public void resetRound() {
        roundSentenceTotal.set(-1);
        roundChunkEndCount.set(0);
    }

    // ★ 删除了 recordSentenceAudioOrigin 和 getSentenceAudioOrigin 两个方法
}