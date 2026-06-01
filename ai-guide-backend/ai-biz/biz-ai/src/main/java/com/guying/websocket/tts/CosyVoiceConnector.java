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
 * CosyVoice TTS 客户端：
 *  - connect：与 Python TTS 建立 WS 连接，发 init 握手；
 *  - synthesize：把单句文本投递给 TTS，由其异步合成；
 *  - 内部 handler：
 *      * 二进制帧直接透传给 Android（Python 已包含完整 header）；
 *      * 同时剥离 7 字节 header 后缓冲裸 PCM，
 *        收到 chunk_end 后把整句裸 PCM + 带 sentence_id 的 audio_end 转给 MuseTalk。
 *
 * Python 发来的二进制帧格式（tts_routes.py）：
 *   [0x01][sentence_id: 2B big-endian][pts_ms: 4B big-endian][raw PCM...]
 *   共 7 字节 header，Java 直接透传给 Android，无需再加头。
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

    /** sid → handler 注册表，用于 interrupt 时清空对应会话的 PCM 缓冲。 */
    private final ConcurrentHashMap<String, CosyVoiceHandler> handlers = new ConcurrentHashMap<>();

    /** Python 音频帧 header 长度：0x01(1) + sentence_id(2) + pts_ms(4) = 7 字节 */
    private static final int AUDIO_HEADER_LEN = 7;

    public void connect(ChatSessionContext ctx) {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxBinaryMessageBufferSize(1024 * 1024);
        container.setDefaultMaxTextMessageBufferSize(1024 * 1024);

        StandardWebSocketClient client = new StandardWebSocketClient(container);
        try {
            CosyVoiceHandler handler = new CosyVoiceHandler(ctx);
            WebSocketSession cosySession = client
                    .execute(handler, cosyVoiceWsUrl)
                    .get();
            ctx.setCosyVoiceSession(cosySession);
            handlers.put(ctx.getSid(), handler);
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

    /**
     * 通知 CosyVoice 端：停止当前用户的语音合成、并清空待推理任务队列；
     * 同时清空本地 PCM 缓冲，避免打断后残留 PCM 在下一次 chunk_end 时被错误转发到 MuseTalk。
     */
    public void interrupt(ChatSessionContext ctx) {
        // 1) 清空本地 handler 中尚未转发的 PCM 缓冲
        CosyVoiceHandler handler = handlers.get(ctx.getSid());
        if (handler != null) {
            handler.clearPcmBuffer();
        }
        // 2) 通知下游 CosyVoice 停止生成 + 清队列
        WebSocketSession cosySession = ctx.getCosyVoiceSession();
        if (cosySession == null || !cosySession.isOpen()) {
            log.warn("CosyVoice session 不可用，跳过 interrupt sid={}", ctx.getSid());
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
                case "chunk_end" -> {
                    // sentence_id 由 Python tts_routes.py 生成，透传给 MuseTalk 的 audio_end
                    int sentenceId = node.has("sentence_id") ? node.get("sentence_id").asInt() : 0;
                    forwardSentenceToMuseTalk(sentenceId);
                }
                case "error" -> {
                    String errMsg = node.has("message") ? node.get("message").asText() : "unknown";
                    log.error("[CosyVoice WS] 报错：{}", errMsg);
                }
                default -> log.warn("[CosyVoice WS] 收到未知类型消息: {}", type);
            }
        }

        /**
         * Python 发来的二进制帧已包含完整 header：
         *   [0x01][sentence_id: 2B][pts_ms: 4B][raw PCM...]
         *
         * 1. 直接透传给 Android（不再手动加 0x01 头）；
         * 2. 剥离前 7 字节 header，只把裸 PCM 写入 pcmBuffer，
         *    供 chunk_end 时整句拼完后发给 MuseTalk。
         */
        @Override
        protected void handleBinaryMessage(WebSocketSession cosySession, BinaryMessage message) {
            byte[] payload = message.getPayload().array();

            // 1) 透传给 Android（Python 已包含 0x01 + sentence_id + pts_ms header）
            if (ctx.getUserSession().isOpen()) {
                sender.send(ctx, new BinaryMessage(payload));
            }

            // 2) 剥离 header，只缓冲裸 PCM 供 MuseTalk 使用
            if (payload.length > AUDIO_HEADER_LEN) {
                synchronized (pcmBuffer) {
                    pcmBuffer.write(payload, AUDIO_HEADER_LEN, payload.length - AUDIO_HEADER_LEN);
                }
            }
        }

        /**
         * 整句 PCM 攒完后转给 MuseTalk，并附上 sentence_id，
         * 使 MuseTalk 能为对应视频帧打上相同的 sentence_id 供安卓对齐。
         */
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
                log.debug("[CosyVoice→MuseTalk] 转发 sentence_id={} PCM {} bytes", sentenceId, sentencePcm.length);
            } catch (IOException e) {
                log.error("发送 PCM/audio_end 到 MuseTalk 失败 sid={}", ctx.getSid(), e);
            }
        }

        /** 打断时清空本地未转发的 PCM 缓冲。 */
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