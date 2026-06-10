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
