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
    //TODO 复用会话

    private final Map<String, ChatSessionContext> contexts = new ConcurrentHashMap<>();

    public ChatSessionContext register(WebSocketSession session, Long digitalHumanId) {
        ChatSessionContext ctx = new ChatSessionContext(session, digitalHumanId);
        contexts.put(ctx.getSid(), ctx);
        return ctx;
    }

    public ChatSessionContext get(String sid) {
        return contexts.get(sid);
    }

    /**
     * 按会话 ID 查找存活会话（注册表按 sid 存储，此处线性扫描）。
     * @return 存活的 ChatSessionContext；无则返回 null
     */
    public ChatSessionContext getByConversationId(String conversationId) {
        if (conversationId == null) {
            return null;
        }
        for (ChatSessionContext ctx : contexts.values()) {
            if (conversationId.equals(ctx.conversationId())) {
                return ctx;
            }
        }
        return null;
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
