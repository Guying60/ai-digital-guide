package com.guying.websocket.session;

import com.alibaba.nls.client.protocol.asr.SpeechTranscriber;
import com.guying.utils.AudioFrameBuffer;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.ExecutorService;
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
    private final ReentrantLock sendLock = new ReentrantLock();
    private final AudioFrameBuffer audioBuffer = new AudioFrameBuffer();

    @Setter private volatile WebSocketSession museTalkSession;
    @Setter private volatile WebSocketSession cosyVoiceSession;
    @Setter private volatile SpeechTranscriber transcriber;
    @Setter private volatile ExecutorService ttsExecutor;

    private volatile String pendingImage;
    private volatile long lastActiveTime;

    public ChatSessionContext(WebSocketSession session) {
        this.userSession = session;
        this.sid = session.getId();
        this.userId = (Long) session.getAttributes().get("userId");
        this.attractionId = (Long) session.getAttributes().get("attractionId");
        this.lastActiveTime = System.currentTimeMillis();
    }

    public String conversationId() {
        return attractionId + ":" + userId;
    }

    public void touchActive() {
        this.lastActiveTime = System.currentTimeMillis();
    }

    public void setPendingImage(String base64) {
        this.pendingImage = base64;
    }

    /** 取出并清空待处理图片，保证只消费一次 */
    public String consumePendingImage() {
        String img = this.pendingImage;
        this.pendingImage = null;
        return img;
    }

    public void clearPendingImage() {
        this.pendingImage = null;
    }
}
