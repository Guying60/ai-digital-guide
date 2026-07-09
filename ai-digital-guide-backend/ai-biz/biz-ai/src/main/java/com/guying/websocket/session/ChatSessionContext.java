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
    private final Long digitalHumanId;
    private final String conversationId;
    private final ReentrantLock sendLock = new ReentrantLock();
    private final AudioFrameBuffer audioBuffer = new AudioFrameBuffer();
    private final PtsTracker ptsTracker = new PtsTracker();

    // ════ E2E 延迟追踪（ms，System.currentTimeMillis()）════
    private volatile long e2eUserInputTime;
    private volatile long e2eAfterPromptTime;
    private volatile long e2eLlmFirstTokenTime;
    private volatile long e2eFirstAudioTime;
    private volatile long e2eFirstVideoTime;
    public void markE2eUserInput()     { this.e2eUserInputTime = System.currentTimeMillis(); }
    public void markE2eAfterPrompt()   { this.e2eAfterPromptTime = System.currentTimeMillis(); }
    public void markE2eLlmFirstToken() { this.e2eLlmFirstTokenTime = System.currentTimeMillis(); }
    public void markE2eFirstAudio()    { this.e2eFirstAudioTime = System.currentTimeMillis(); }
    public void markE2eFirstVideo()    { this.e2eFirstVideoTime = System.currentTimeMillis(); }
    public long getE2eUserInputTime()     { return e2eUserInputTime; }
    public long getE2eAfterPromptTime()   { return e2eAfterPromptTime; }
    public long getE2eLlmFirstTokenTime() { return e2eLlmFirstTokenTime; }
    public long getE2eFirstAudioTime()    { return e2eFirstAudioTime; }
    public long getE2eFirstVideoTime()    { return e2eFirstVideoTime; }
    private volatile int lastAudioGlobalPtsMs;
    public void setLastAudioGlobalPts(int ptsMs) { this.lastAudioGlobalPtsMs = ptsMs; }
    public int getLastAudioGlobalPtsMs()          { return lastAudioGlobalPtsMs; }

    /** 本轮对话的句子总数（responseDone 时由 AiChatService 写入；-1 表示进行中/未知）。 */
    private final AtomicInteger roundSentenceTotal = new AtomicInteger(-1);
    /** 本轮已收到 chunk_end 的句子计数（CosyVoiceConnector 递增），用于判定"最后一句"补静音收口。 */
    private final AtomicInteger roundChunkEndCount = new AtomicInteger(0);

    /** 会话建立时刻（毫秒），用于断开时判定"连接时间是否 ≥30s"是否落库。final，仅内存比较，不落库。 */
    private final long connectTime;
    /** 本次会话内用户实际提问次数（文本/语音均汇入 AiChatService.invoke 递增）。>0 即视为"有效会话"。 */
    @Getter(lombok.AccessLevel.NONE)
    private final AtomicInteger questionCount = new AtomicInteger(0);


    @Setter private volatile WebSocketSession museTalkSession;
    @Setter private volatile WebSocketSession cosyVoiceSession;
    @Setter private volatile SpeechTranscriber transcriber;
    @Setter private volatile ExecutorService ttsExecutor;
    /** 按 PTS 时钟节流的视频出站发送器，afterConnectionEstablished 创建。 */
    @Setter private volatile OutboundPacer outboundPacer;

    private volatile long lastActiveTime;

    public ChatSessionContext(WebSocketSession session, Long digitalHumanId) {
        this.userSession = session;
        this.sid = session.getId();
        this.userId = (Long) session.getAttributes().get("userId");
        this.attractionId = (Long) session.getAttributes().get("attractionId");
        this.digitalHumanId = digitalHumanId;
        this.conversationId = UUID.randomUUID().toString();
        this.connectTime = System.currentTimeMillis();
        this.lastActiveTime = this.connectTime;
    }

    public String conversationId() {
        return conversationId;
    }

    /** 用户提问一次（文本或语音），递增计数。 */
    public int incrementQuestionCount() {
        return questionCount.incrementAndGet();
    }

    /** 返回当前提问次数（int）。注：questionCount 字段已排除类级 @Getter，避免返回 AtomicInteger 对象。 */
    public int getQuestionCount() {
        return questionCount.get();
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