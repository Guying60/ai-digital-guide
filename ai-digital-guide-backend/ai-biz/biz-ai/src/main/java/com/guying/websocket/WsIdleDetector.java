package com.guying.websocket;

import com.guying.websocket.session.ChatSessionContext;
import com.guying.websocket.session.ChatSessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

/**
 * 周期检查 WebSocket 空闲连接，超过 30 分钟无活动则关闭。
 */
@Component
@Slf4j
public class WsIdleDetector {

    private static final long IDLE_THRESHOLD_MS = 1_800_000L;

    @Autowired
    private ChatSessionRegistry registry;

    @Autowired
    private AiChatHandler aiChatHandler;

    @Scheduled(fixedDelay = 80_000)
    public void checkIdleConnections() {
        long now = System.currentTimeMillis();
        registry.forEach((sid, ctx) -> {
            if (now - ctx.getLastActiveTime() <= IDLE_THRESHOLD_MS) return;
            log.warn("WS心跳超时，关闭连接 sid={}", sid);
            WebSocketSession session = ctx.getUserSession();
            if (session != null && session.isOpen()) {
                try {
                    session.close(CloseStatus.GOING_AWAY);
                } catch (IOException e) {
                    aiChatHandler.cleanup(sid);
                }
            } else {
                aiChatHandler.cleanup(sid);
            }
        });
    }
}
