package com.guying.websocket.session;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * WebSocket 会话注册表，集中管理 ChatSessionContext。
 */
@Component
public class ChatSessionRegistry {

    private final Map<String, ChatSessionContext> contexts = new ConcurrentHashMap<>();

    public ChatSessionContext register(WebSocketSession session) {
        ChatSessionContext ctx = new ChatSessionContext(session);
        contexts.put(ctx.getSid(), ctx);
        return ctx;
    }

    public ChatSessionContext get(String sid) {
        return contexts.get(sid);
    }

    public ChatSessionContext remove(String sid) {
        return contexts.remove(sid);
    }

    public int size() {
        return contexts.size();
    }

    public void forEach(BiConsumer<String, ChatSessionContext> consumer) {
        contexts.forEach(consumer);
    }
}
