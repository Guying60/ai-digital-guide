package com.guying.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Component
@Slf4j
public class WsIdleDetector {
    @Autowired
    private AiChatHandler aiChatHandler;

    @Scheduled(fixedDelay = 80_000)
    public void checkIdleConnections() {
        long now = System.currentTimeMillis();
        aiChatHandler.getLastActiveTimeMap().forEach((sid, lastTime) -> {
            if (now - lastTime > 1_800_000) {
                log.warn("WS心跳超时，关闭连接 sid={}", sid);
                WebSocketSession session = aiChatHandler.getSessionMap().get(sid);
                if (session != null && session.isOpen()) {
                    try {
                        session.close(CloseStatus.GOING_AWAY);
                    } catch (IOException e) {
                        aiChatHandler.cleanup(sid);
                    }
                } else {
                    aiChatHandler.cleanup(sid);
                }
            }
        });
    }
}
