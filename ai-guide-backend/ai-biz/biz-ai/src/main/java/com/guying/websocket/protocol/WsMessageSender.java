package com.guying.websocket.protocol;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.guying.websocket.session.ChatSessionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 统一封装向用户 WebSocket 写消息的逻辑：
 *  - sendJson：发送 {type, text} 协议帧（走 session 锁，避免并发写入）
 *  - send    ：发送任意 WebSocketMessage（同样走锁）
 *  - sendError：异常路径下的错误帧（不走锁，尽力发送）
 */
@Component
@Slf4j
public class WsMessageSender {

    private final ObjectMapper objectMapper = new ObjectMapper();

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
