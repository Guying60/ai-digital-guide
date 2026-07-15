package com.guying.websocket;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.guying.common.constants.MqConstants;
import com.guying.common.constants.RedisConstants;
import com.guying.common.enums.TourStatusEnum;
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
        Long userId = (Long) session.getAttributes().get("userId");
        Long digitalHumanId = digitalHumanInternalService.getDigitalHumanIdByAttractionId(attractionId);
        // 未配置数字人时不再拒绝连接：降级为纯文本问答会话，仍可用 AI 文本/语音问答，
        // 仅跳过 CosyVoice 语音合成与 MuseTalk 数字人视频（二者均依赖数字人素材）。
        boolean textOnly = digitalHumanId == null;
        if (textOnly) {
            log.warn("景点 {} 未配置数字人，降级为纯文本问答模式 sid={}", attractionId, session.getId());
        }

        // 继续对话：校验携带的 conversationId 归属当前用户，防止串会话/读取他人 LLM 记忆。
        // 校验通过则沿用（LLM 记忆与游览历史衔接），否则移除标记按新会话处理。
        String resumeConversationId = (String) session.getAttributes().get("conversationId");
        Integer priorMessageCount = null;
        if (resumeConversationId != null) {
            priorMessageCount = userService.getTourHistoryMessageCount(userId, resumeConversationId);
            if (priorMessageCount == null) {
                log.warn("继续对话校验失败（会话不存在或不属于该用户），按新会话处理 userId={}, conversationId={}",
                        userId, resumeConversationId);
                session.getAttributes().remove("conversationId");
            } else {
                // 极端场景（崩溃后快速重进）下同会话可能残留旧连接，先关闭避免注册表出现双活
                ChatSessionContext stale = registry.getByConversationId(resumeConversationId);
                if (stale != null) {
                    log.warn("继续对话检测到同会话残留连接，关闭旧连接 staleSid={}, conversationId={}",
                            stale.getSid(), resumeConversationId);
                    closeQuietly(stale.getUserSession(), "StaleUserSession", stale.getSid());
                }
            }
        }

        ChatSessionContext ctx = registry.register(session, digitalHumanId);
        if (priorMessageCount != null) {
            // 以历史消息数折算提问数作为起点，保证零交互判定与断开落库按累计值计算
            ctx.seedQuestionCount(priorMessageCount / 2);
        }
        log.info("用户连接成功，sid: {}，userId: {}，attractionId: {}，digitalHumanId: {}，textOnly: {}",
                ctx.getSid(), ctx.getUserId(), ctx.getAttractionId(), ctx.getDigitalHumanId(), textOnly);
        log.info("═══ [METRICS] SESSIONS | active={} | sid={} | action=CONNECT ═══", registry.size(), ctx.getSid());

        try {
            // executor 必须最先初始化，保证后续 emitSentence 总能提交 TTS 任务
            // （纯文本模式下 AiChatService.emitSentence 会跳过 TTS，executor 仅作占位，断开时一并清理）
            ctx.setTtsExecutor(Executors.newSingleThreadExecutor());
            // 视频出站按 PTS 时钟节流，消除 bufferbloat 导致的中后期队列抽干
            ctx.setOutboundPacer(new OutboundPacer(ctx.getSid(), msg -> sender.send(ctx, msg)));
            cacheConversationAndUserInfo(ctx);
            // 同步创建游览历史记录（新会话插入 IN_PROGRESS；继续对话时 upsert 复用原记录，
            // 不重置 messageCount、状态只升不降），确保主页立即可见；
            // 记录在断开时保留，由用户显式「结束对话」时按零交互清理
            userService.createTourHistory(ctx.getUserId(), ctx.getAttractionId(),
                    ctx.conversationId(), TourStatusEnum.IN_PROGRESS.getCode());
            // 数字人音视频连接仅在配置了数字人时建立，纯文本会话不依赖二者
            if (!textOnly) {
                museTalkConnector.connect(ctx);
                cosyVoiceConnector.connect(ctx);
            }
        } catch (Exception e) {
            log.error("会话初始化失败 sid={}", ctx.getSid(), e);
            throw new ServiceException("会话初始化失败");
        }
        log.info("所有初始化完成 sid={} textOnly={}", ctx.getSid(), textOnly);
        // 下发 textOnly 标识与服务端权威的 conversationId（新会话时前端据此获知会话 ID，
        // 用于对话页内结束、后续继续对话）；已配数字人的会话 textOnly 恒为 false，向后兼容
        sender.sendJson(ctx, "allDone", Map.of(
                "textOnly", textOnly,
                "conversationId", ctx.conversationId()));
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
                ctx.resetSpeakRound();
                ctx.markE2eUserInput();
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
            log.info("═══ [METRICS] SESSIONS | active={} | sid={} | action=DISCONNECT ═══", registry.size(), sid);
            return;
        }
        // 已由 HTTP /end 按零交互删除的会话：记录已被删除，跳过持久化/删除，避免 MQ 重建脏数据
        if (ctx.isTourHistoryDeleted()) {
            log.info("会话游览历史已由 /end 删除，cleanup 跳过持久化 sid={}, conversationId={}",
                    sid, ctx.conversationId());
        } else {
            // 有效会话：MQ 异步更新 messageCount（tourStatus 不改变）
            // 零交互短会话（0 提问 + <30s）：不再删除，保留连接时创建的 IN_PROGRESS 记录。
            // 断开不代表用户结束游览——用户可能只是暂时退出、稍后回到主页「继续对话」，
            // 若此处删除，刷新列表后记录会直接消失。空记录的清理交给用户显式「结束对话」
            // （endTourHistory 按零交互删除），此处仅不落待评价，避免空记录被标为「可评价」。
            boolean shouldPersist = ctx.getQuestionCount() > 0
                    || (System.currentTimeMillis() - ctx.getConnectTime()) >= MIN_VALID_DURATION_MS;
            if (shouldPersist) {
                publishUserTourHistory(ctx);
                reviewInternalService.createPendingReview(ctx.getUserId(), ctx.getAttractionId(), ctx.conversationId());
            } else {
                log.info("零交互短会话（零提问且连接<{}ms），保留 IN_PROGRESS 记录待用户继续对话 sid={}, questionCount={}",
                        MIN_VALID_DURATION_MS, sid, ctx.getQuestionCount());
            }
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
        log.info("═══ [METRICS] SESSIONS | active={} | sid={} | action=DISCONNECT ═══", registry.size(), sid);
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
        ctx.resetSpeakRound();
        museTalkConnector.interrupt(ctx);
        cosyVoiceConnector.interrupt(ctx);
        ctx.getPtsTracker().onInterrupt();
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

    /** 断开时调用：MQ 异步更新 messageCount，不改变 tourStatus（由 Listener upsert 保留原值） */
    private void publishUserTourHistory(ChatSessionContext ctx) {
        UserTourHistoryMessage msg = buildBaseMessage(ctx);
        rabbitTemplate.convertAndSend(
                MqConstants.USER_TOUR_HISTORY_DIRECT,
                MqConstants.USER_TOUR_HISTORY_ROUTING_KEY,
                msg);
    }

    private UserTourHistoryMessage buildBaseMessage(ChatSessionContext ctx) {
        UserTourHistoryMessage msg = new UserTourHistoryMessage();
        msg.setUserId(ctx.getUserId());
        msg.setAttractionId(ctx.getAttractionId());
        msg.setConversationId(ctx.conversationId());
        // 每轮用户提问对应一条 AI 回复；记忆窗口会裁剪历史，不能事后 COUNT
        int questionCount = ctx.getQuestionCount();
        msg.setMessageCount(questionCount > 0 ? questionCount * 2 : 0);
        return msg;
    }
}
