package com.guying.websocket.tts;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.guying.websocket.protocol.WsMessageSender;
import com.guying.websocket.session.ChatSessionContext;
import com.guying.websocket.session.ChatSessionRegistry;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CosyVoice TTS 客户端：音频帧即时流式转发给前端，同时累积裸 PCM 供 MuseTalk 使用。
 */
@Component
@Slf4j
public class CosyVoiceConnector {

    @Value("${spring.cosyVoice.ws-url}")
    private String cosyVoiceWsUrl;

    @Autowired
    private WsMessageSender sender;

    @Autowired
    private ChatSessionRegistry registry;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ConcurrentHashMap<String, CosyVoiceHandler> handlers = new ConcurrentHashMap<>();

    private static final int AUDIO_HEADER_LEN = 7;

    public void connect(ChatSessionContext ctx) {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxBinaryMessageBufferSize(5000 * 1024);
        container.setDefaultMaxTextMessageBufferSize(5000 * 1024);

        StandardWebSocketClient client = new StandardWebSocketClient(container);
        try {
            CosyVoiceHandler handler = new CosyVoiceHandler(ctx);
            WebSocketSession cosySession = client.execute(handler, cosyVoiceWsUrl).get();
            ctx.setCosyVoiceSession(cosySession);
            handlers.put(ctx.getSid(), handler);
        } catch (Exception e) {
            log.error("连接 CosyVoice TTS 服务失败！url={}", cosyVoiceWsUrl, e);
        }
    }

    public void synthesize(ChatSessionContext ctx, String text) {
        if (text == null || text.isBlank()) return;

        WebSocketSession cosySession = ctx.getCosyVoiceSession();
        if (cosySession == null || !cosySession.isOpen()) {
            log.warn("CosyVoice session not ready sid={}", ctx.getSid());
            return;
        }
        ObjectNode req = objectMapper.createObjectNode();
        req.put("type", "synthesize");
        req.put("text", text);
        if (ctx.getAttractionId() != null) {
            req.put("attraction_id", ctx.getAttractionId().toString());
        }
        req.put("session_id", ctx.getSid());
        try {
            log.info("已向 CosyVoice 发送 synthesize 请求 sid={}", ctx.getSid());
            cosySession.sendMessage(new TextMessage(req.toString()));
        } catch (IOException e) {
            log.error("发送 synthesize 请求到 CosyVoice 失败 sid={}", ctx.getSid(), e);
        }
    }

    public void interrupt(ChatSessionContext ctx) {
        CosyVoiceHandler handler = handlers.get(ctx.getSid());
        if (handler != null) {
            handler.clearPcmBuffer();
        }
        WebSocketSession cosySession = ctx.getCosyVoiceSession();
        if (cosySession == null || !cosySession.isOpen()) {
            return;
        }
        try {
            String json = objectMapper.createObjectNode()
                    .put("type", "interrupt")
                    .put("attraction_id", String.valueOf(ctx.getAttractionId()))
                    .put("session_id", ctx.getSid())
                    .toString();
            cosySession.sendMessage(new TextMessage(json));
            log.info("已向 CosyVoice 发送 interrupt sid={}", ctx.getSid());
        } catch (Exception e) {
            log.warn("向 CosyVoice 发送 interrupt 失败 sid={}", ctx.getSid(), e);
        }
    }

    private class CosyVoiceHandler extends AbstractWebSocketHandler {

        private final ChatSessionContext ctx;
        private final ByteArrayOutputStream pcmBuffer = new ByteArrayOutputStream();
        // 当前句子的 ID，由二进制音频帧头部提取，供 chunk_end → MuseTalk 使用
        private volatile int currentSentenceId = 0;

        CosyVoiceHandler(ChatSessionContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession cosySession) throws Exception {
            log.info("[CosyVoice WS] 已连接 sid={} attractionId={}", ctx.getSid(), ctx.getAttractionId());
            String initJson = objectMapper.createObjectNode()
                    .put("type", "init")
                    .put("attraction_id", String.valueOf(ctx.getAttractionId()))
                    .put("session_id", ctx.getSid())
                    .toString();
            cosySession.sendMessage(new TextMessage(initJson));
        }

        @Override
        protected void handleTextMessage(WebSocketSession cosySession, TextMessage message) throws Exception {
            ObjectNode node = (ObjectNode) objectMapper.readTree(message.getPayload());
            String type = node.has("type") ? node.get("type").asText() : "";
            switch (type) {
                case "ping" -> {
                    cosySession.sendMessage(new TextMessage(
                            objectMapper.createObjectNode().put("type", "pong").toString()));
                }
                case "chunk_end" -> {
                    // 使用音频帧头部的 sentenceId（currentSentenceId），
                    // 而非 chunk_end 消息自身的 sentenceId，保证与 buffer key 一致
                    log.info("[CosyVoice] chunk_end 消息sentenceId={}, 音频帧sentenceId={}",
                            node.has("sentence_id") ? node.get("sentence_id").asInt() : "null",
                            currentSentenceId);
                    forwardSentenceToMuseTalk(currentSentenceId);
                }
                case "error" -> log.error("[CosyVoice WS] 报错：{}", node.has("message") ? node.get("message").asText() : "unknown");
                default -> log.warn("[CosyVoice WS] 收到未知类型消息: {}", type);
            }
        }

        @Override
        protected void handleBinaryMessage(WebSocketSession cosySession, BinaryMessage message) {
            byte[] payload = message.getPayload().array();
            if (payload.length < AUDIO_HEADER_LEN) return;

            // 解析 sentence_id（来自音频帧头部，payload 格式: [0x01][sentenceId:2B][ptsMs:4B][PCM...]）
            int sentenceId = ((payload[1] & 0xFF) << 8) | (payload[2] & 0xFF);
            currentSentenceId = sentenceId;  // 记录当前句子 ID，供 chunk_end 使用

            // 解析本地 PTS，转换为全局 PTS
            int localPtsMs = ((payload[3] & 0xFF) << 24) | ((payload[4] & 0xFF) << 16)
                    | ((payload[5] & 0xFF) << 8) | (payload[6] & 0xFF);
            int globalPtsMs = ctx.getPtsTracker().toGlobal(localPtsMs);

            // 1) 即时流式发送音频帧给前端（不做批量缓冲）
            // Android 协议格式：[0x01][sentenceId:2B][ptsMs:4B][PCM...]
            byte[] androidFrame = new byte[payload.length];
            androidFrame[0] = 0x01;
            // sentenceId(2B)
            androidFrame[1] = payload[1];
            androidFrame[2] = payload[2];
            // 全局 PTS(4B)
            androidFrame[3] = (byte) (globalPtsMs >> 24);
            androidFrame[4] = (byte) (globalPtsMs >> 16);
            androidFrame[5] = (byte) (globalPtsMs >> 8);
            androidFrame[6] = (byte) globalPtsMs;
            // PCM
            System.arraycopy(payload, AUDIO_HEADER_LEN, androidFrame, 7,
                    payload.length - AUDIO_HEADER_LEN);
            sender.send(ctx, new BinaryMessage(androidFrame));

            // 2) 剥离 header，只缓冲裸 PCM 供 MuseTalk 炼丹使用
            if (payload.length > AUDIO_HEADER_LEN) {
                synchronized (pcmBuffer) {
                    pcmBuffer.write(payload, AUDIO_HEADER_LEN, payload.length - AUDIO_HEADER_LEN);
                }
            }
        }

        private void forwardSentenceToMuseTalk(int sentenceId) {
            byte[] sentencePcm;
            synchronized (pcmBuffer) {
                sentencePcm = pcmBuffer.toByteArray();
                pcmBuffer.reset();
            }
            ChatSessionContext live = registry.get(ctx.getSid());
            WebSocketSession museTalkSession = live != null ? live.getMuseTalkSession() : null;
            if (museTalkSession == null || !museTalkSession.isOpen()) return;

            try {
                if (sentencePcm.length > 0) {
                    museTalkSession.sendMessage(new BinaryMessage(sentencePcm));
                }
                museTalkSession.sendMessage(new TextMessage(
                        objectMapper.createObjectNode()
                                .put("type", "audio_end")
                                .put("sentence_id", sentenceId)
                                .toString()));
            } catch (IOException e) {
                log.error("发送 PCM/audio_end 到 MuseTalk 失败 sid={}", ctx.getSid(), e);
            }
        }

        void clearPcmBuffer() {
            synchronized (pcmBuffer) {
                pcmBuffer.reset();
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession cosySession, CloseStatus status) {
            handlers.remove(ctx.getSid(), this);
        }
    }
}