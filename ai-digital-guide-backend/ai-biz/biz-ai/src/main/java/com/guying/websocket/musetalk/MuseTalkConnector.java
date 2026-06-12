package com.guying.websocket.musetalk;

import org.springframework.web.socket.CloseStatus;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.guying.websocket.protocol.WsMessageSender;
import com.guying.websocket.session.ChatSessionContext;
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

import java.util.concurrent.ConcurrentHashMap;

/**
 * 数字人（Python MuseTalk）连接器：音视频配对匀速发送模式。
 * <p>
 * 视频帧到达后，通过 AVBuffer 先发送该 sentence 缓冲的音频帧，
 * 再以 25fps 匀速发送视频帧，实现音画同步。
 */
@Component
@Slf4j
public class MuseTalkConnector {

    @Value("${spring.museTalk.ws-url}")
    private String pythonWsUrl;

    @Autowired
    private WsMessageSender sender;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, MuseTalkHandler> handlers = new ConcurrentHashMap<>();

    public void connect(ChatSessionContext ctx) {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxBinaryMessageBufferSize(5000 * 1024);
        container.setDefaultMaxTextMessageBufferSize(5000 * 1024);

        StandardWebSocketClient client = new StandardWebSocketClient(container);
        try {
            MuseTalkHandler handler = new MuseTalkHandler(ctx);
            WebSocketSession museTalkSession = client.execute(handler, pythonWsUrl).get();
            ctx.setMuseTalkSession(museTalkSession);
            handlers.put(ctx.getSid(), handler);
        } catch (Exception e) {
            log.error("连接 Python 炼丹炉失败！", e);
        }
    }

    public void interrupt(ChatSessionContext ctx) {
        ctx.getAvBuffer().clearAll();
        WebSocketSession museTalkSession = ctx.getMuseTalkSession();
        if (museTalkSession == null || !museTalkSession.isOpen()) {
            return;
        }
        try {
            String json = objectMapper.createObjectNode()
                    .put("type", "interrupt")
                    .put("attraction_id", String.valueOf(ctx.getAttractionId()))
                    .put("session_id", ctx.getSid())
                    .toString();
            museTalkSession.sendMessage(new TextMessage(json));
            log.info("已向 MuseTalk 发送 interrupt sid={}", ctx.getSid());
        } catch (Exception e) {
            log.warn("向 MuseTalk 发送 interrupt 失败 sid={}", ctx.getSid(), e);
        }
    }

    private class MuseTalkHandler extends AbstractWebSocketHandler {

        private final ChatSessionContext ctx;

        MuseTalkHandler(ChatSessionContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession museTalkSession) throws Exception {
            log.info("建立连接 attractionId={}", ctx.getAttractionId());
            String initJson = objectMapper.createObjectNode()
                    .put("type", "init")
                    .put("attraction_id", String.valueOf(ctx.getAttractionId()))
                    .toString();
            museTalkSession.sendMessage(new TextMessage(initJson));
        }

        @Override
        protected void handleTextMessage(WebSocketSession museTalkSession, TextMessage message) throws Exception {
            ObjectNode node = (ObjectNode) objectMapper.readTree(message.getPayload());
            String type = node.get("type").asText();
            switch (type) {
                case "ping" -> {
                    museTalkSession.sendMessage(new TextMessage(
                            objectMapper.createObjectNode().put("type", "pong").toString()));
                }
                case "ready" -> sender.sendJson(ctx, "ready", null);
                case "done" -> {
                    int sentenceId = node.has("sentence_id") ? node.get("sentence_id").asInt() : 0;
                    // ★ 通过 AVBuffer 发送 done，保证在所有音视频帧发送完毕后才到达 Android
                    log.info("[Python WS] 句子完毕 sentence_id={}，提交到 AVBuffer 发送队列", sentenceId);
                    ctx.getAvBuffer().submitDone(sentenceId, sender, ctx);
                }
                case "error" -> log.error("[Python WS] 报错：{}", node.get("message").asText());
                default -> log.warn("[Python WS] 收到未知类型消息: {}", type);
            }
        }

        @Override
        protected void handleBinaryMessage(WebSocketSession museTalkSession, BinaryMessage message) {
            if (!ctx.getUserSession().isOpen()) return;
            byte[] raw = message.getPayload().array();
            // 内层头现为 7 字节：[sentence_id:2B][pts_ms:4B][is_keyframe:1B]
            if (raw.length < 7) return;

            // 解析 sentence_id
            int sentenceId = ((raw[0] & 0xFF) << 8) | (raw[1] & 0xFF);

            // 组装 Android payload：[0x03][sentence_id:2B][pts_ms:4B][is_keyframe:1B][H.264 AU...]
            byte[] androidPayload = new byte[raw.length + 1];
            androidPayload[0] = 0x03;
            System.arraycopy(raw, 0, androidPayload, 1, raw.length);

            // ★ 通过 AVBuffer 配对发送：先匀速发音频，再以 25fps 匀速发视频
            ctx.getAvBuffer().submitPairedSend(sentenceId, androidPayload, sender, ctx);
        }

        @Override
        public void afterConnectionClosed(WebSocketSession museTalkSession, CloseStatus status) {
            handlers.remove(ctx.getSid(), this);
        }
    }
}