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

    // ★ 句末补静音收口：每轮最后一句末尾追加一小段静音，让 MuseTalk 把嘴收回闭合，
    //   同时给客户端补同样时长的静音音频帧（推进音频播放头，避免闭嘴尾帧被慢放）。
    private static final int FRAME_MS = 40;                                   // 25fps → 40ms/帧
    private static final int SILENCE_TAIL_MS = 240;                           // 收口静音时长
    private static final int SILENCE_FRAME_BYTES = 16000 * 2 * FRAME_MS / 1000; // 1280B @16kHz/16bit/mono
    private static final int SILENCE_FRAMES = SILENCE_TAIL_MS / FRAME_MS;     // 6 帧

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
        if (ctx.getDigitalHumanId() != null) {
            req.put("digital_human_id", ctx.getDigitalHumanId().toString());
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
                    .put("digital_human_id", String.valueOf(ctx.getDigitalHumanId()))
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
        // 当前句子最后一帧的本地 PTS（ms），供句末补静音帧续接 PTS 使用
        private volatile int lastLocalPtsMs = 0;
        // ★ 句级诊断：追踪每句音频的总字节数、帧数、耗时
        private int audioCurSentenceId = -1;
        private long audioSentenceBytes = 0;
        private int audioSentenceFrameCount = 0;
        private long audioSentenceStartWallMs = 0;
        private long audioSentenceLastSendMs = 0;

        CosyVoiceHandler(ChatSessionContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession cosySession) throws Exception {
            log.info("[CosyVoice WS] 已连接 sid={} digitalHumanId={}", ctx.getSid(), ctx.getDigitalHumanId());
            String initJson = objectMapper.createObjectNode()
                    .put("type", "init")
                    .put("digital_human_id", String.valueOf(ctx.getDigitalHumanId()))
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
                    // ★ 句级诊断：在 chunk_end 时打印最终统计
                    long chunkWallMs = audioSentenceStartWallMs > 0 ?
                            System.currentTimeMillis() - audioSentenceStartWallMs : 0;
                    log.info("[CosyVoice] chunk_end 消息sentenceId={}, 音频帧sentenceId={} audioFrames={} audioBytes={} audioWall={}ms",
                            node.has("sentence_id") ? node.get("sentence_id").asInt() : "null",
                            currentSentenceId, audioSentenceFrameCount, audioSentenceBytes, chunkWallMs);
                    // 判定是否为本轮最后一句：若是，则句末补静音收口（让数字人嘴闭合）
                    int doneCount = ctx.getRoundChunkEndCount().incrementAndGet();
                    int total = ctx.getRoundSentenceTotal().get();
                    boolean isLast = total > 0 && doneCount >= total;
                    forwardSentenceToMuseTalk(currentSentenceId, isLast);
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

            // ★ 句级诊断：检测句子切换，打印上一句的音频统计
            long audioNow = System.currentTimeMillis();
            if (sentenceId != audioCurSentenceId) {
                if (audioCurSentenceId >= 0 && audioSentenceFrameCount > 0) {
                    log.info("[CosyVoice] ⇧ AUDIO-SENTENCE-DONE sid={}: frames={} bytes={} wall={}ms",
                            audioCurSentenceId, audioSentenceFrameCount, audioSentenceBytes,
                            audioSentenceLastSendMs - audioSentenceStartWallMs);
                }
                audioCurSentenceId = sentenceId;
                audioSentenceBytes = 0;
                audioSentenceFrameCount = 0;
                audioSentenceStartWallMs = audioNow;
                audioSentenceLastSendMs = 0;
            }
            audioSentenceBytes += payload.length;
            audioSentenceFrameCount++;

            // 解析本地 PTS，转换为全局 PTS
            int localPtsMs = ((payload[3] & 0xFF) << 24) | ((payload[4] & 0xFF) << 16)
                    | ((payload[5] & 0xFF) << 8) | (payload[6] & 0xFF);
            lastLocalPtsMs = localPtsMs;  // 记录本句最后一帧 PTS，供句末补静音续接
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
            audioSentenceLastSendMs = System.currentTimeMillis();  // ★ 句级诊断

            // 2) 剥离 header，只缓冲裸 PCM 供 MuseTalk 炼丹使用
            if (payload.length > AUDIO_HEADER_LEN) {
                synchronized (pcmBuffer) {
                    pcmBuffer.write(payload, AUDIO_HEADER_LEN, payload.length - AUDIO_HEADER_LEN);
                }
            }
        }

        private void forwardSentenceToMuseTalk(int sentenceId, boolean isLast) {
            byte[] sentencePcm;
            synchronized (pcmBuffer) {
                sentencePcm = pcmBuffer.toByteArray();
                pcmBuffer.reset();
            }
            ChatSessionContext live = registry.get(ctx.getSid());
            WebSocketSession museTalkSession = live != null ? live.getMuseTalkSession() : null;
            if (museTalkSession == null || !museTalkSession.isOpen()) return;

            // 本轮最后一句：句末补静音收口。
            // 1) 给客户端补 SILENCE_FRAMES 帧静音音频（续接本句 PTS），推进音频播放头；
            // 2) 给 MuseTalk 的 PCM 末尾追加同样时长的静音，使其生成闭嘴尾帧（无需改 Python）。
            if (isLast && sentencePcm.length > 0) {
                for (int k = 1; k <= SILENCE_FRAMES; k++) {
                    int globalPts = ctx.getPtsTracker().toGlobal(lastLocalPtsMs + FRAME_MS * k);
                    byte[] frame = new byte[AUDIO_HEADER_LEN + SILENCE_FRAME_BYTES]; // 头部+PCM 默认全 0（静音）
                    frame[0] = 0x01;
                    frame[1] = (byte) (sentenceId >> 8);
                    frame[2] = (byte) sentenceId;
                    frame[3] = (byte) (globalPts >> 24);
                    frame[4] = (byte) (globalPts >> 16);
                    frame[5] = (byte) (globalPts >> 8);
                    frame[6] = (byte) globalPts;
                    sender.send(ctx, new BinaryMessage(frame));
                }
                byte[] padded = new byte[sentencePcm.length + SILENCE_FRAMES * SILENCE_FRAME_BYTES];
                System.arraycopy(sentencePcm, 0, padded, 0, sentencePcm.length);
                // 其余字节默认 0 = 静音
                sentencePcm = padded;
                log.info("[CosyVoice] 句末补静音收口 sid={} +{}ms 静音", sentenceId, SILENCE_TAIL_MS);
            }

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