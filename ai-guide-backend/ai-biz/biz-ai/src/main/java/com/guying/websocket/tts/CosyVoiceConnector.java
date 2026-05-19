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
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * CosyVoice TTS 客户端：
 *  - connect：与 Python TTS 建立 WS 连接，发 init 握手；
 *  - synthesize：把单句文本投递给 TTS，由其异步合成；
 *  - 内部 handler：把 PCM 加 0x01 头转给 Android，同时缓冲整句 PCM，
 *    收到 chunk_end 后把整句 PCM + audio_end 一并转给 MuseTalk。
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

    public void connect(ChatSessionContext ctx) {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxBinaryMessageBufferSize(1024 * 1024);
        container.setDefaultMaxTextMessageBufferSize(1024 * 1024);

        StandardWebSocketClient client = new StandardWebSocketClient(container);
        try {
            WebSocketSession cosySession = client
                    .execute(new CosyVoiceHandler(ctx), cosyVoiceWsUrl)
                    .get();
            ctx.setCosyVoiceSession(cosySession);
        } catch (Exception e) {
            log.error("连接 CosyVoice TTS 服务失败！url={}", cosyVoiceWsUrl, e);
        }
    }

    /**
     * 把一句话扔给 CosyVoice，不等回包；TTS 内部按队列顺序合成，
     * PCM 流经 CosyVoiceHandler 异步落到 Android / MuseTalk。
     */
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
            cosySession.sendMessage(new TextMessage(req.toString()));
        } catch (IOException e) {
            log.error("发送 synthesize 请求到 CosyVoice 失败 sid={}", ctx.getSid(), e);
        }
    }

    private class CosyVoiceHandler extends AbstractWebSocketHandler {

        private final ChatSessionContext ctx;
        private final ByteArrayOutputStream pcmBuffer = new ByteArrayOutputStream();

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
                    log.debug("[CosyVoice WS] ← ping，已回 pong");
                }
                case "chunk_end" -> forwardSentenceToMuseTalk();
                case "error" -> {
                    String errMsg = node.has("message") ? node.get("message").asText() : "unknown";
                    log.error("[CosyVoice WS] 报错：{}", errMsg);
                }
                default -> log.warn("[CosyVoice WS] 收到未知类型消息: {}", type);
            }
        }

        @Override
        protected void handleBinaryMessage(WebSocketSession cosySession, BinaryMessage message) {
            byte[] rawPcm = message.getPayload().array();
            synchronized (pcmBuffer) {
                pcmBuffer.write(rawPcm, 0, rawPcm.length);
            }
            // 实时加 0x01 头转给 Android
            if (ctx.getUserSession().isOpen()) {
                byte[] androidPayload = new byte[rawPcm.length + 1];
                androidPayload[0] = 0x01;
                System.arraycopy(rawPcm, 0, androidPayload, 1, rawPcm.length);
                sender.send(ctx, new BinaryMessage(androidPayload));
            }
        }

        private void forwardSentenceToMuseTalk() {
            byte[] sentencePcm;
            synchronized (pcmBuffer) {
                sentencePcm = pcmBuffer.toByteArray();
                pcmBuffer.reset();
            }
            ChatSessionContext live = registry.get(ctx.getSid());
            WebSocketSession pythonSession = live != null ? live.getPythonSession() : null;
            if (pythonSession == null || !pythonSession.isOpen()) return;

            try {
                if (sentencePcm.length > 0) {
                    pythonSession.sendMessage(new BinaryMessage(sentencePcm));
                }
                pythonSession.sendMessage(new TextMessage(
                        objectMapper.createObjectNode().put("type", "audio_end").toString()));
            } catch (IOException e) {
                log.error("发送 PCM/audio_end 到 MuseTalk 失败 sid={}", ctx.getSid(), e);
            }
        }
    }
}
