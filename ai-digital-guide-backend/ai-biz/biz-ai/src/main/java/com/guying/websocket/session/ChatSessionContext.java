package com.guying.websocket.session;

import com.alibaba.nls.client.protocol.asr.SpeechTranscriber;
import com.guying.utils.AudioFrameBuffer;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
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

    // ════ E2E 延迟追踪（ms，System.currentTimeMillis()；每轮用户输入时清零下游时间戳）════
    private volatile long e2eUserInputTime;
    private volatile long e2eAfterPromptTime;
    private volatile long e2eLlmFirstTokenTime;
    private volatile long e2eFirstAudioTime;
    private volatile long e2eFirstVideoTime;
    private final AtomicInteger e2eLlmLogged = new AtomicInteger(0);
    private final AtomicInteger e2eCompleteLogged = new AtomicInteger(0);

    /** 新一轮用户输入：记录 T0，并清零本轮下游时间戳与打点闸门，避免多轮串值。 */
    public void markE2eUserInput() {
        this.e2eUserInputTime = System.currentTimeMillis();
        this.e2eAfterPromptTime = 0;
        this.e2eLlmFirstTokenTime = 0;
        this.e2eFirstAudioTime = 0;
        this.e2eFirstVideoTime = 0;
        this.e2eLlmLogged.set(0);
        this.e2eCompleteLogged.set(0);
    }
    public void markE2eAfterPrompt()   { this.e2eAfterPromptTime = System.currentTimeMillis(); }
    public void markE2eLlmFirstToken() { this.e2eLlmFirstTokenTime = System.currentTimeMillis(); }
    public void markE2eFirstAudio()    { this.e2eFirstAudioTime = System.currentTimeMillis(); }
    public void markE2eFirstVideo()    { this.e2eFirstVideoTime = System.currentTimeMillis(); }
    public long getE2eUserInputTime()     { return e2eUserInputTime; }
    public long getE2eAfterPromptTime()   { return e2eAfterPromptTime; }
    public long getE2eLlmFirstTokenTime() { return e2eLlmFirstTokenTime; }
    public long getE2eFirstAudioTime()    { return e2eFirstAudioTime; }
    public long getE2eFirstVideoTime()    { return e2eFirstVideoTime; }
    /** @return true 表示本轮首次获得 LLM 阶段打点权 */
    public boolean tryClaimE2eLlmLog() {
        return e2eLlmLogged.compareAndSet(0, 1);
    }
    /** @return true 表示本轮首次获得完整链路打点权 */
    public boolean tryClaimE2eCompleteLog() {
        return e2eCompleteLogged.compareAndSet(0, 1);
    }
    private volatile int lastAudioGlobalPtsMs;
    public void setLastAudioGlobalPts(int ptsMs) { this.lastAudioGlobalPtsMs = ptsMs; }
    public int getLastAudioGlobalPtsMs()          { return lastAudioGlobalPtsMs; }

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

    /**
     * 本轮数字人说话收尾：LLM 已结束且待出镜句数为 0 时，经 Pacer 下发 {@code speakingDone}。
     * pendingSpeakSentences 在 emitSentence 时 +1，MuseTalk 句级 done 时 -1。
     */
    private final AtomicInteger pendingSpeakSentences = new AtomicInteger(0);
    private volatile boolean llmRoundComplete = false;
    private final AtomicBoolean speakingDoneEnqueued = new AtomicBoolean(false);

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

    /** 新一轮用户输入 / 打断：重置说话轮次计数。 */
    public void resetSpeakRound() {
        pendingSpeakSentences.set(0);
        llmRoundComplete = false;
        speakingDoneEnqueued.set(false);
    }

    /** 调度一句 TTS+数字人视频时调用。 */
    public void beginSpeakSentence() {
        pendingSpeakSentences.incrementAndGet();
    }

    /**
     * MuseTalk 单句视频 done 后调用。
     * @return true 表示应立即入队 {@code speakingDone}
     */
    public boolean onSpeakVideoDoneAndMaybeReady() {
        pendingSpeakSentences.decrementAndGet();
        return tryClaimSpeakingDone();
    }

    /**
     * LLM 流结束（已发 responseDone）后调用。
     * @return true 表示应立即入队 {@code speakingDone}
     */
    public boolean markLlmCompleteAndMaybeReady() {
        llmRoundComplete = true;
        return tryClaimSpeakingDone();
    }

    private boolean tryClaimSpeakingDone() {
        if (!llmRoundComplete) {
            return false;
        }
        if (pendingSpeakSentences.get() > 0) {
            return false;
        }
        return speakingDoneEnqueued.compareAndSet(false, true);
    }
}