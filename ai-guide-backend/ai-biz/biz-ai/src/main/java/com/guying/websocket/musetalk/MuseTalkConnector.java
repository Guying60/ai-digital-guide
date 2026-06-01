package com.guying.websocket.musetalk;

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

/**
 * 数字人（Python MuseTalk）连接器：
 *  - 建立 WS 连接并发送 init 协议；
 *  - 文本：ready/done 透传给 Android（done 含 sentence_id）；ping 回 pong；
 *  - 二进制：Python 发来 [sentence_id:2B][pts_ms:4B][JPEG...]，
 *    Java 在最前面加 0x02 后转给 Android，
 *    安卓最终收到：[0x02][sentence_id:2B][pts_ms:4B][JPEG...]。
 */
@Component
@Slf4j
public class MuseTalkConnector {

    @Value("${spring.museTalk.ws-url}")
    private String pythonWsUrl;

    @Autowired
    private WsMessageSender sender;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void connect(ChatSessionContext ctx) {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxBinaryMessageBufferSize(1024 * 1024);
        container.setDefaultMaxTextMessageBufferSize(1024 * 1024);

        StandardWebSocketClient client = new StandardWebSocketClient(container);
        try {
            WebSocketSession museTalkSession = client
                    .execute(new MuseTalkHandler(ctx), pythonWsUrl)
                    .get();
            ctx.setMuseTalkSession(museTalkSession);
        } catch (Exception e) {
            log.error("连接 Python 炼丹炉失败！", e);
        }
    }

    /**
     * 通知 MuseTalk 端：停止当前用户的视频帧生成、并清空待推理任务队列。
     * 任何异常均被吞掉（仅 log.warn），避免影响 CosyVoice 那一路的打断。
     */
    public void interrupt(ChatSessionContext ctx) {
        WebSocketSession museTalkSession = ctx.getMuseTalkSession();
        if (museTalkSession == null || !museTalkSession.isOpen()) {
            log.warn("MuseTalk session 不可用，跳过 interrupt sid={}", ctx.getSid());
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
            log.info("建立连接{}", ctx.getAttractionId());
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
                    log.debug("[Python WS] ← ping，已回 pong");
                }
                case "ready" -> {
                    log.info("[Python WS] 准备好了，通知 Android");
                    sender.sendJson(ctx, "ready", null);
                }
                case "done" -> {
                    // sentence_id 透传给 Android，用于通知安卓哪一句视频已全部推完
                    int sentenceId = node.has("sentence_id") ? node.get("sentence_id").asInt() : 0;
                    log.info("[Python WS] 这句话生成完毕 sentence_id={}，通知 Android", sentenceId);
                    String doneJson = objectMapper.createObjectNode()
                            .put("type", "done")
                            .put("sentence_id", sentenceId)
                            .toString();
                    if (ctx.getUserSession().isOpen()) {
                        ctx.getUserSession().sendMessage(new TextMessage(doneJson));
                    }
                }
                case "error" -> {
                    String errMsg = node.get("message").asText();
                    log.error("[Python WS] 报错：{}", errMsg);
                }
                default -> log.warn("[Python WS] 收到未知类型消息: {}", type);
            }
        }

        /**
         * Python 发来的二进制帧格式（ws_routes.py）：
         *   [sentence_id: 2B big-endian][pts_ms: 4B big-endian][JPEG bytes...]
         *
         * 在最前面加 0x02 类型头后透传给 Android，
         * 安卓最终收到：[0x02][sentence_id:2B][pts_ms:4B][JPEG...]
         */
        @Override
        protected void handleBinaryMessage(WebSocketSession museTalkSession, BinaryMessage message) {
            if (!ctx.getUserSession().isOpen()) return;
            byte[] rawVideoBytes = message.getPayload().array();
            byte[] androidPayload = new byte[rawVideoBytes.length + 1];
            androidPayload[0] = 0x02;
            System.arraycopy(rawVideoBytes, 0, androidPayload, 1, rawVideoBytes.length);
            sender.send(ctx, new BinaryMessage(androidPayload));
        }
    }
}