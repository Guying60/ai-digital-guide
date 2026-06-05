package com.guying.websocket;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.guying.common.constants.MqConstants;
import com.guying.common.constants.RedisConstants;
import com.guying.exception.ServiceException;
import com.guying.message.UserTourHistoryMessage;
import com.guying.user.service.UserInternalService;
import com.guying.websocket.chat.AiChatService;
import com.guying.websocket.musetalk.MuseTalkConnector;
import com.guying.websocket.nls.NlsTranscriberManager;
import com.guying.websocket.protocol.WsMessageSender;
import com.guying.websocket.session.ChatSessionContext;
import com.guying.websocket.session.ChatSessionRegistry;
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
import static com.guying.common.constants.RedisConstants.USER_INFO_KEY;

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
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        ChatSessionContext ctx = registry.register(session);
        log.info("用户连接成功，sid: {}，userId: {}，attractionId: {}",
                ctx.getSid(), ctx.getUserId(), ctx.getAttractionId());
        log.info("客户端连接成功，sid: {}，当前在线人数：{}", ctx.getSid(), registry.size());

        try {
            cacheConversationAndUserInfo(ctx);
            publishUserTourHistory(ctx);
            museTalkConnector.connect(ctx);
            cosyVoiceConnector.connect(ctx);
            ctx.setTtsExecutor(Executors.newSingleThreadExecutor());
        } catch (Exception e) {
            log.error("会话初始化失败 sid={}", ctx.getSid(), e);
            throw new ServiceException("会话初始化失败");
        }
        sender.startVideoDrain(ctx);
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
            case "photo" -> {
                ctx.setPendingImage(node.get("photo").asText());
            }
            case "camera" -> {
                if ("off".equals(node.get("status").asText())) {
                    ctx.clearPendingImage();
                }
            }
            case "ping" -> sender.sendJson(ctx, "pong", null);
            case "interrupt" -> handleInterrupt(ctx);
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
        sender.stopVideoDrain(sid);
        ChatSessionContext ctx = registry.remove(sid);
        if (ctx == null) {
            log.info("连接关闭 sid={}, 剩余在线={}", sid, registry.size());
            return;
        }
        closeQuietly(ctx.getMuseTalkSession(), "MuseTalk", sid);
        closeQuietly(ctx.getCosyVoiceSession(), "CosyVoice", sid);
        closeMicAndTts(ctx);
        log.info("连接关闭 sid={}, 剩余在线={}", sid, registry.size());
    }

    /** micOff 与 cleanup 共用：停掉 NLS + 关闭该用户的 TTS 串行执行器 */
    private void closeMicAndTts(ChatSessionContext ctx) {
        nlsTranscriberManager.close(ctx);
        ExecutorService ttsExecutor = ctx.getTtsExecutor();
        ctx.setTtsExecutor(null);
        if (ttsExecutor != null && !ttsExecutor.isShutdown()) {
            ttsExecutor.shutdownNow();
        }
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
        if (!stringRedisTemplate.hasKey(USER_INFO_KEY + userId)) {
            Map<String, String> userInfo = userService.getUserInfo(userId);
            stringRedisTemplate.opsForHash().putAll(USER_INFO_KEY + userId, userInfo);
            stringRedisTemplate.expire(USER_INFO_KEY + userId,
                    RedisConstants.USER_INFO_EXPIRE_TIME, TimeUnit.HOURS);
        }
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
