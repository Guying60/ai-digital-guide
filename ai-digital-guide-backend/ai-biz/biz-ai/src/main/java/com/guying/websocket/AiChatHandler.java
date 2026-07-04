package com.guying.websocket;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.guying.common.constants.MqConstants;
import com.guying.common.constants.RedisConstants;
import com.guying.attractions.service.DigitalHumanInternalService;
import com.guying.exception.ServiceException;
import com.guying.message.UserTourHistoryMessage;
import com.guying.attractions.service.ReviewInternalService;
import com.guying.pojo.vo.RoutePlanVO;
import com.guying.ratelimit.RateLimiterUtil;
import com.guying.service.FaceEmotionService;
import com.guying.service.RouteRecommendationService;
import com.guying.user.service.UserInternalService;
import com.guying.websocket.chat.AiChatService;
import com.guying.websocket.musetalk.MuseTalkConnector;
import com.guying.websocket.nls.NlsTranscriberManager;
import com.guying.websocket.protocol.WsMessageSender;
import com.guying.websocket.session.ChatSessionContext;
import com.guying.websocket.session.ChatSessionRegistry;
import com.guying.websocket.session.OutboundPacer;
import com.guying.websocket.tts.CosyVoiceConnector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.guying.common.constants.RedisConstants.USER_CONVERSATION_KEY;

/**
 * AI 讲解员 WebSocket 入口：仅做事件分发与会话生命周期管理，
 * 实际工作下沉到 NlsTranscriberManager / AiChatService /
 * CosyVoiceConnector / MuseTalkConnector。
 */
@Component
@Slf4j
public class AiChatHandler extends AbstractWebSocketHandler {

    @Autowired
    private ChatSessionRegistry registry;

    @Autowired
    private WsMessageSender sender;

    @Autowired
    private AiChatService aiChatService;

    @Autowired
    private NlsTranscriberManager nlsTranscriberManager;

    @Autowired
    private CosyVoiceConnector cosyVoiceConnector;

    @Autowired
    private MuseTalkConnector museTalkConnector;

    @Autowired
    private UserInternalService userService;

    @Autowired
    private ReviewInternalService reviewInternalService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RouteRecommendationService routeRecommendationService;

    @Autowired
    private FaceEmotionService faceEmotionService;

    @Autowired
    private DigitalHumanInternalService digitalHumanInternalService;

    @Autowired
    private RateLimiterUtil rateLimiterUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 连接时间不足该阈值且一句话都没问的会话，不落游览历史/待评价。 */
    private static final long MIN_VALID_DURATION_MS = 30_000L;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long attractionId = (Long) session.getAttributes().get("attractionId");
        Long digitalHumanId = digitalHumanInternalService.getDigitalHumanIdByAttractionId(attractionId);
        if (digitalHumanId == null) {
            log.warn("WebSocket 连接拒绝：景点 {} 未配置数字人", attractionId);
            throw new ServiceException("该景点尚未配置数字人");
        }
        ChatSessionContext ctx = registry.register(session, digitalHumanId);
        log.info("用户连接成功，sid: {}，userId: {}，attractionId: {}，digitalHumanId: {}",
                ctx.getSid(), ctx.getUserId(), ctx.getAttractionId(), ctx.getDigitalHumanId());
        log.info("客户端连接成功，sid: {}，当前在线人数：{}", ctx.getSid(), registry.size());

        try {
            // executor 必须最先初始化，保证后续 emitSentence 总能提交 TTS 任务
            ctx.setTtsExecutor(Executors.newSingleThreadExecutor());
            // 视频出站按 PTS 时钟节流，消除 bufferbloat 导致的中后期队列抽干
            ctx.setOutboundPacer(new OutboundPacer(ctx.getSid(), msg -> sender.send(ctx, msg)));
            cacheConversationAndUserInfo(ctx);
            // 游览历史不再在连接建立时落库：改为断开时按"是否提问过 / 连接是否≥30s"判定，
            // 避免零提问且秒断的连接产生历史/待评价脏数据。
            museTalkConnector.connect(ctx);
            cosyVoiceConnector.connect(ctx);
        } catch (Exception e) {
            log.error("会话初始化失败 sid={}", ctx.getSid(), e);
            throw new ServiceException("会话初始化失败");
        }
        log.info("所有初始化完成 sid={}", ctx.getSid());
        sender.sendJson(ctx, "allDone", null);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        ChatSessionContext ctx = registry.get(session.getId());
        if (ctx == null) return;
        ctx.touchActive();

        ObjectNode node = (ObjectNode) objectMapper.readTree(message.getPayload());
        String type = node.get("type").asText();
        switch (type) {
            case "micOff" -> closeMicAndTts(ctx);
            case "micOn" -> nlsTranscriberManager.recreate(ctx);
            case "text" -> {
                String wordText = node.get("text").asText();
                log.info("用户输入文本：{}", wordText);
                aiChatService.invoke(ctx, wordText);
            }
            case "emotionFrame" -> handleEmotionFrame(ctx, node);
            case "ping" -> sender.sendJson(ctx, "pong", null);
            case "interrupt" -> handleInterrupt(ctx);
            case "routeGenerate" -> routeRecommendationService.generateAndPush(ctx);
            case "routeArrive" -> {
                RoutePlanVO updated = routeRecommendationService.markArrived(ctx, node.get("stopIndex").asInt());
                if (updated != null) {
                    sender.sendJson(ctx, "routeUpdate", updated);
                } else {
                    sender.sendJson(ctx, "routeError", "当前没有可更新的路线");
                }
            }
            case "routeClose" -> {
                routeRecommendationService.clearPlan(ctx.getUserId(), ctx.getAttractionId());
                sender.sendJson(ctx, "routeClosed", (String) null);
            }
            default -> log.warn("未知的客户端消息类型: {}", type);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        ChatSessionContext ctx = registry.get(session.getId());
        if (ctx == null) return;
        nlsTranscriberManager.feed(ctx, message.getPayload().array());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("连接关闭 sid={}, 状态={}", session.getId(), status);
        cleanup(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WebSocket 发生错误，异常断开连接, sid={}", session.getId(), exception);
        cleanup(session.getId());
        ChatSessionContext ctx = registry.get(session.getId());
        if (ctx != null) {
            sender.sendJson(ctx, "connectionError", "连接发生错误");
        }
    }

    public void cleanup(String sid) {
        ChatSessionContext ctx = registry.remove(sid);
        if (ctx == null) {
            log.info("连接关闭 sid={}, 剩余在线={}", sid, registry.size());
            return;
        }
        // 仅"提问过 或 连接≥30s"的有效会话才落游览历史 + 待评价；
        // 零提问且秒断的连接直接丢弃，不产生脏数据。
        boolean shouldPersist = ctx.getQuestionCount() > 0
                || (System.currentTimeMillis() - ctx.getConnectTime()) >= MIN_VALID_DURATION_MS;
        if (shouldPersist) {
            publishUserTourHistory(ctx);
            reviewInternalService.createPendingReview(ctx.getUserId(), ctx.getAttractionId(), ctx.conversationId());
        } else {
            log.info("会话判定为无效（零提问且连接<{}ms），不落数据库 sid={}, questionCount={}",
                    MIN_VALID_DURATION_MS, sid, ctx.getQuestionCount());
        }
        // 路线生命周期跟随连接，断开即清理
        routeRecommendationService.clearPlan(ctx.getUserId(), ctx.getAttractionId());
        OutboundPacer pacer = ctx.getOutboundPacer();
        if (pacer != null) {
            pacer.shutdown();
        }
        closeQuietly(ctx.getMuseTalkSession(), "MuseTalk", sid);
        closeQuietly(ctx.getCosyVoiceSession(), "CosyVoice", sid);
        closeMicAndTts(ctx);
        sender.removeSession(sid);
        log.info("连接关闭 sid={}, 剩余在线={}", sid, registry.size());
    }

    /** micOff 与 cleanup 共用：停掉 NLS*/
    private void closeMicAndTts(ChatSessionContext ctx) {
        nlsTranscriberManager.close(ctx);
    }

    /**
     * 处理前置摄像头低频采集的面部表情帧：
     *  - 令牌桶限流（每 userId 每 5 秒最多 1 次视觉调用），超限静默丢弃，控制大模型成本；
     *  - 异步交给 FaceEmotionService 做视觉分类 + 落库，不阻塞 WS 线程；
     *  - 原始图像不入库。
     */
    private void handleEmotionFrame(ChatSessionContext ctx, ObjectNode node) {
        String base64 = node.has("photo") ? node.get("photo").asText() : null;
        if (base64 == null || base64.isEmpty()) {
            return;
        }
        if (!rateLimiterUtil.tryAcquire("ai:face-emotion:" + ctx.getUserId(), 1, 5)) {
            log.debug("面部表情帧被限流丢弃, userId={}", ctx.getUserId());
            return;
        }
        faceEmotionService.analyze(base64, ctx.getUserId(), ctx.getAttractionId(), ctx.conversationId());
    }

    /**
     * 处理用户主动打断数字人：
     *  1) 通知 MuseTalk / CosyVoice 停止当前生成并清空待推理任务队列；
     *  2) 立即 shutdownNow 当前 TTS 串行执行器，再新建一个，让后续对话继续。
     */
    private void handleInterrupt(ChatSessionContext ctx) {
        log.info("用户主动打断 sid={}", ctx.getSid());
        museTalkConnector.interrupt(ctx);
        cosyVoiceConnector.interrupt(ctx);
        ctx.getPtsTracker().onInterrupt();
        ctx.resetRound();  // 复位句末补静音计数，避免被打断的旧轮残留触发误补
        OutboundPacer pacer = ctx.getOutboundPacer();
        if (pacer != null) {
            pacer.reset();
        }
        ExecutorService old = ctx.getTtsExecutor();
        if (old != null && !old.isShutdown()) {
            old.shutdownNow();
        }
        ctx.setTtsExecutor(Executors.newSingleThreadExecutor());
    }

    private void closeQuietly(WebSocketSession session, String tag, String sid) {
        if (session != null && session.isOpen()) {
            try {
                session.close();
            } catch (IOException e) {
                log.warn("关闭 {} session 失败 sid={}", tag, sid, e);
            }
        }
    }

    private void cacheConversationAndUserInfo(ChatSessionContext ctx) {
        Long userId = ctx.getUserId();
        stringRedisTemplate.opsForValue().set(
                USER_CONVERSATION_KEY + userId,
                ctx.conversationId(),
                RedisConstants.CONVERSATION_EXPIRE_TIME,
                TimeUnit.HOURS);
        // 每次连接都重新构建缓存，确保偏好字段完整（UserInternalServiceImpl 内部负责写缓存）
        userService.getUserInfo(userId);
    }

    private void publishUserTourHistory(ChatSessionContext ctx) {
        UserTourHistoryMessage msg = new UserTourHistoryMessage();
        msg.setUserId(ctx.getUserId());
        msg.setAttractionId(ctx.getAttractionId());
        msg.setConversationId(ctx.conversationId());
        rabbitTemplate.convertAndSend(
                MqConstants.USER_TOUR_HISTORY_DIRECT,
                MqConstants.USER_TOUR_HISTORY_ROUTING_KEY,
                msg);
    }
}
