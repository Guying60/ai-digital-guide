package com.guying.websocket.protocol;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.guying.websocket.session.ChatSessionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 向用户 WebSocket 写消息的统一入口。
 * 音频、视频、控制指令全部走 session 锁直接发送，不做队列缓冲。
 */
@Component
@Slf4j
public class WsMessageSender {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 视频帧直接透传给 Android，不做排队。 */
    public void sendVideoFrame(ChatSessionContext ctx, BinaryMessage message) {
        if (ctx == null) return;
        send(ctx.getUserSession(), ctx.getSendLock(), message);
    }

    /** done 消息直接发给 Android。 */
    public void enqueueDone(ChatSessionContext ctx, int sentenceId) {
        sendJson(ctx, "done", null);
    }

    /**
     * 本轮数字人音画均已产出完毕：经 OutboundPacer 排在末帧之后发给前端，
     * 触发客户端进入 Draining（EOS 排空末帧 → 待机闭嘴）。
     */
    public void enqueueSpeakingDone(ChatSessionContext ctx) {
        if (ctx == null) return;
        ObjectNode resp = objectMapper.createObjectNode();
        resp.put("type", "speakingDone");
        TextMessage msg = new TextMessage(resp.toString());
        var pacer = ctx.getOutboundPacer();
        if (pacer != null) {
            pacer.enqueueControl(msg);
            log.info("[speakingDone] enqueued via pacer sid={}", ctx.getSid());
        } else {
            send(ctx, msg);
            log.info("[speakingDone] sent direct sid={}", ctx.getSid());
        }
    }

    // ---- 通用消息发送 ----

    public void sendJson(ChatSessionContext ctx, String type, String text) {
        if (ctx == null) return;
        sendJson(ctx.getUserSession(), ctx.getSendLock(), type, text);
    }

    public void sendJson(WebSocketSession session, ReentrantLock lock, String type, String text) {
        if (session == null || !session.isOpen()) return;
        ObjectNode resp = objectMapper.createObjectNode();
        resp.put("type", type);
        if (text != null) {
            resp.put("text", text);
        }
        send(session, lock, new TextMessage(resp.toString()));
    }

    /**
     * 发送结构化消息：{"type": type, "data": <payload 序列化后的 JSON>}。
     * 供路线时间轴等需要下发嵌套对象的场景使用，前端可直接读 data，无需二次解析。
     */
    public void sendJson(ChatSessionContext ctx, String type, Object payload) {
        if (ctx == null) return;
        WebSocketSession session = ctx.getUserSession();
        if (session == null || !session.isOpen()) return;
        ObjectNode resp = objectMapper.createObjectNode();
        resp.put("type", type);
        if (payload != null) {
            resp.set("data", objectMapper.valueToTree(payload));
        }
        send(session, ctx.getSendLock(), new TextMessage(resp.toString()));
    }

    public void send(ChatSessionContext ctx, WebSocketMessage<?> message) {
        if (ctx == null) return;
        send(ctx.getUserSession(), ctx.getSendLock(), message);
    }

    public void send(WebSocketSession session, ReentrantLock lock, WebSocketMessage<?> message) {
        if (lock == null || session == null || !session.isOpen()) return;
        lock.lock();
        try {
            if (session.isOpen()) {
                session.sendMessage(message);
            }
        } catch (IOException e) {
            log.error("WebSocket发送失败 sessionId={}", session.getId(), e);
        } finally {
            lock.unlock();
        }
    }

    /** 清理 session 数据 */
    public void removeSession(String sessionId) {
        // 保留空方法，供外部调用兼容
    }

    /** 错误路径下尽力直发，不抛异常，不走锁 */
    public void sendError(WebSocketSession session, String msg) {
        if (session == null) return;
        ObjectNode error = objectMapper.createObjectNode();
        error.put("type", "error");
        error.put("text", msg);
        try {
            session.sendMessage(new TextMessage(error.toString()));
        } catch (IOException ignored) {
        }
    }

    public void sendError(ChatSessionContext ctx, String msg) {
        if (ctx == null) return;
        sendError(ctx.getUserSession(), msg);
    }
}
