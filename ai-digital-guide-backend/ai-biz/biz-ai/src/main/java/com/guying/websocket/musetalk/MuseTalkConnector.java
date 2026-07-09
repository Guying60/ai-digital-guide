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
 * 数字人（Python MuseTalk）连接器：将 MuseTalk 生成的视频帧即时流式转发给前端。
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
        WebSocketSession museTalkSession = ctx.getMuseTalkSession();
        if (museTalkSession == null || !museTalkSession.isOpen()) {
            return;
        }
        // 重置诊断计数器
        MuseTalkHandler handler = handlers.get(ctx.getSid());
        if (handler != null) {
            handler.pyVideoCount = 0;
            handler.videoMaxPtsMs = 0;
        }
        try {
            String json = objectMapper.createObjectNode()
                    .put("type", "interrupt")
                    .put("digital_human_id", String.valueOf(ctx.getDigitalHumanId()))
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
        // ★ 句级诊断：追踪每句的总字节数、帧数、耗时
        private int pyCurSentenceId = -1;
        private long pySentenceStartWallMs = 0;
        private long pySentenceBytes = 0;
        private int pySentenceFrameCount = 0;
        private long pySentenceFirstSendMs = 0;
        private long pySentenceLastSendMs = 0;
        // ★ AV sync 追踪
        private int pyVideoCount = 0;
        private int videoMaxPtsMs = 0;

        MuseTalkHandler(ChatSessionContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession museTalkSession) throws Exception {
            log.info("建立连接 digitalHumanId={}", ctx.getDigitalHumanId());
            String initJson = objectMapper.createObjectNode()
                    .put("type", "init")
                    .put("digital_human_id", String.valueOf(ctx.getDigitalHumanId()))
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
                    long doneWallMs = System.currentTimeMillis();
                    long sentenceWallMs = pySentenceStartWallMs > 0 ? doneWallMs - pySentenceStartWallMs : 0;
                    long effectiveThroughput = sentenceWallMs > 0 ? pySentenceBytes * 1000 / sentenceWallMs : 0;
                    long sendSpan = pySentenceLastSendMs > 0 ? pySentenceLastSendMs - pySentenceFirstSendMs : 0;
                    log.info("[Python WS] 句子完毕 sentence_id={} frames={} bytes={} pyRecv→doneWall={}ms sendSpan={}ms effectiveThroughput={}B/s",
                            sentenceId, pySentenceFrameCount, pySentenceBytes,
                            sentenceWallMs, sendSpan, effectiveThroughput);

                    // ═══ METRICS: 数字人首帧延迟 ═══
                    long firstFrameDelay = ctx.getE2eFirstVideoTime() > 0 && ctx.getE2eLlmFirstTokenTime() > 0
                            ? ctx.getE2eFirstVideoTime() - ctx.getE2eLlmFirstTokenTime() : -1;
                    log.info("═══ [METRICS] DIGITAL-HUMAN | sentenceId={} | firstFrameDelay={}ms | totalFrames={} | totalBytes={} | wallTime={}ms ═══",
                            sentenceId, firstFrameDelay, pySentenceFrameCount, pySentenceBytes, sentenceWallMs);

                    // ═══ METRICS: 后端音画同步误差 ═══
                    int audioPts = ctx.getLastAudioGlobalPtsMs();
                    int backendDelta = audioPts > 0 ? videoMaxPtsMs - audioPts : 0;
                    log.info("═══ [METRICS] AV-SYNC | sentenceId={} | audioMaxPts={}ms | videoMaxPts={}ms | backendDelta={}ms ═══",
                            sentenceId, audioPts, videoMaxPtsMs, backendDelta);
                    videoMaxPtsMs = 0;

                    // 推进全局 PTS 偏移（该句所有视频帧已发送完毕）
                    ctx.getPtsTracker().advanceOnDone();

                    // 转发 done 消息给前端（前端可用于 UI 状态更新，不再用于 PTS 偏移）
                    // 经 pacer 排在本句末帧之后释放，避免越过仍在排队的视频帧提前到达
                    ObjectNode doneMsg = objectMapper.createObjectNode();
                    doneMsg.put("type", "done");
                    doneMsg.put("sentence_id", sentenceId);
                    com.guying.websocket.session.OutboundPacer pacer = ctx.getOutboundPacer();
                    if (pacer != null) {
                        pacer.enqueueControl(new TextMessage(doneMsg.toString()));
                    } else {
                        sender.send(ctx, new TextMessage(doneMsg.toString()));
                    }
                }
                case "error" -> log.error("[Python WS] 报错：{}", node.get("message").asText());
                default -> log.warn("[Python WS] 收到未知类型消息: {}", type);
            }
        }

        @Override
        protected void handleBinaryMessage(WebSocketSession museTalkSession, BinaryMessage message) {
            if (!ctx.getUserSession().isOpen()) return;
            byte[] raw = message.getPayload().array();
            if (raw.length < 7) return;

            int sentenceId = ((raw[0] & 0xFF) << 8) | (raw[1] & 0xFF);
            int localPtsMs = ((raw[2] & 0xFF) << 24) | ((raw[3] & 0xFF) << 16)
                    | ((raw[4] & 0xFF) << 8) | (raw[5] & 0xFF);

            long pyNow = System.currentTimeMillis();
            pyVideoCount++;

            // 句级诊断：检测句子切换
            if (sentenceId != pyCurSentenceId) {
                pyCurSentenceId = sentenceId;
                pySentenceStartWallMs = pyNow;
                pySentenceBytes = 0;
                pySentenceFrameCount = 0;
                pySentenceFirstSendMs = 0;
                pySentenceLastSendMs = 0;
            }
            pySentenceBytes += raw.length;
            pySentenceFrameCount++;

            int globalPtsMs = ctx.getPtsTracker().toGlobal(localPtsMs);
            raw[2] = (byte) (globalPtsMs >> 24);
            raw[3] = (byte) (globalPtsMs >> 16);
            raw[4] = (byte) (globalPtsMs >> 8);
            raw[5] = (byte) globalPtsMs;

            byte[] androidPayload = new byte[raw.length + 1];
            androidPayload[0] = 0x03;
            System.arraycopy(raw, 0, androidPayload, 1, raw.length);

            com.guying.websocket.session.OutboundPacer pacer = ctx.getOutboundPacer();
            if (pacer != null) {
                pacer.enqueueVideo(globalPtsMs, new BinaryMessage(androidPayload));
            } else {
                sender.send(ctx, new BinaryMessage(androidPayload));
            }

            // T4: 首个视频帧
            if (ctx.getE2eFirstVideoTime() == 0) {
                ctx.markE2eFirstVideo();
            }
            if (globalPtsMs > videoMaxPtsMs) videoMaxPtsMs = globalPtsMs;

            long sendDoneMs = System.currentTimeMillis();
            if (pySentenceFirstSendMs == 0) pySentenceFirstSendMs = sendDoneMs;
            pySentenceLastSendMs = sendDoneMs;
        }

        @Override
        public void afterConnectionClosed(WebSocketSession museTalkSession, CloseStatus status) {
            handlers.remove(ctx.getSid(), this);
        }
    }
}