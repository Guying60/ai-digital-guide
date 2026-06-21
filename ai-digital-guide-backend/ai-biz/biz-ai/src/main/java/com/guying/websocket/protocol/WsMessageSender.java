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
import org.springframework.web.socket.adapter.standard.StandardWebSocketSession;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 向用户 WebSocket 写消息的统一入口。
 * 音频、视频、控制指令全部走 session 锁直接发送，不做队列缓冲。
 */
@Component
@Slf4j
public class WsMessageSender {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 诊断：每个 session 的上次 send 时间，用于计算间隔 */
    private final ConcurrentHashMap<String, Long> sessionLastSendTime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> sessionSendCount = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> sessionTcpNoDelaySet = new ConcurrentHashMap<>();

    /** 诊断：每 session 的累计发送字节数 & 起始时间（计算实际吞吐） */
    private final ConcurrentHashMap<String, Long> sessionTotalBytes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> sessionStartWallMs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> sessionBytesSinceLastLog = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> sessionLastLogTime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> sessionLogCount = new ConcurrentHashMap<>();

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
                // ★ 诊断：记录发送间隔
                long now = System.currentTimeMillis();
                String sid = session.getId();
                Long lastTime = sessionLastSendTime.get(sid);
                long interval = lastTime == null ? 0 : now - lastTime;
                sessionLastSendTime.put(sid, now);
                int count = sessionSendCount.merge(sid, 1, Integer::sum);

                // ★ 首次发送时尝试在底层 Socket 上设置 TCP_NODELAY
                if (!sessionTcpNoDelaySet.getOrDefault(sid, false)) {
                    trySetTcpNoDelay(session, sid);
                }

                long beforeSend = System.currentTimeMillis();
                session.sendMessage(message);
                long sendCost = System.currentTimeMillis() - beforeSend;

                // ★ 诊断：统计实际发送字节数 & 吞吐
                int payloadLen = message.getPayloadLength();
                long totalBytes = sessionTotalBytes.merge(sid, (long) payloadLen, Long::sum);
                sessionStartWallMs.putIfAbsent(sid, now);
                long sessionWallMs = now - sessionStartWallMs.get(sid);

                // ★ 诊断：每 100 帧输出一次吞吐报告
                int logCnt = sessionLogCount.merge(sid, 1, Integer::sum);
                long bytesSinceLog = sessionBytesSinceLastLog.merge(sid, (long) payloadLen, Long::sum);
                Long lastLogTimeObj = sessionLastLogTime.putIfAbsent(sid, now);
                long lastLogTime = (lastLogTimeObj != null) ? lastLogTimeObj : now;
                long logInterval = now - lastLogTime;

                if (logCnt % 100 == 0) {
                    long logThroughput = logInterval > 0 ? bytesSinceLog * 1000 / logInterval : 0;
                    log.info("[WS-SENDER] ⇧ THROUGHPUT: totalBytes={} wallElapsed={}ms logBytes={} logElapsed={}ms logThroughput={}B/s frames={}",
                            totalBytes, sessionWallMs, bytesSinceLog, logInterval, logThroughput, logCnt);
                    sessionBytesSinceLastLog.put(sid, 0L);
                    sessionLastLogTime.put(sid, now);
                }

                if (count <= 30 || interval > 50 || sendCost > 10 || count % 50 == 0) {
                    log.info("[WS-SENDER] ⇧ SEND: interval={}ms sendCost={}ms count={} payloadSize={}",
                            interval, sendCost, count,
                            payloadLen);
                }
            }
        } catch (IOException e) {
            log.error("WebSocket发送失败 sessionId={}", session.getId(), e);
        } finally {
            lock.unlock();
        }
    }

    /** 尝试在底层 SocketChannel 上设置 TCP_NODELAY */
    private void trySetTcpNoDelay(WebSocketSession session, String sid) {
        try {
            if (session instanceof StandardWebSocketSession standardSession) {
                var nativeSession = standardSession.getNativeSession();
                // Tomcat WebSocket: org.apache.tomcat.websocket.WsSession
                if (nativeSession.getClass().getName().contains("tomcat")) {
                    // 尝试通过反射访问底层 SocketChannel
                    Field socketField = findField(nativeSession.getClass(), "socketChannel");
                    if (socketField == null) {
                        socketField = findField(nativeSession.getClass(), "socketWrapper");
                    }
                    if (socketField != null) {
                        socketField.setAccessible(true);
                        Object socketObj = socketField.get(nativeSession);
                        SocketChannel sc = null;
                        if (socketObj instanceof SocketChannel) {
                            sc = (SocketChannel) socketObj;
                        } else if (socketObj != null) {
                            // 从 SocketWrapper 获取 SocketChannel
                            Field scField = findField(socketObj.getClass(), "socketChannel");
                            if (scField == null) scField = findField(socketObj.getClass(), "sc");
                            if (scField != null) {
                                scField.setAccessible(true);
                                Object scObj = scField.get(socketObj);
                                if (scObj instanceof SocketChannel) {
                                    sc = (SocketChannel) scObj;
                                }
                            }
                        }
                        if (sc != null && sc.isConnected()) {
                            sc.socket().setTcpNoDelay(true);
                            sessionTcpNoDelaySet.put(sid, true);
                            log.info("[WS-SENDER] ✓ TCP_NODELAY set on socket sessionId={}", sid);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[WS-SENDER] Could not set TCP_NODELAY: {}", e.getMessage());
        }
    }

    /** 递归查找类及其父类的字段 */
    private Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /** 清理 session 诊断数据 */
    public void removeSession(String sessionId) {
        sessionLastSendTime.remove(sessionId);
        sessionSendCount.remove(sessionId);
        sessionTcpNoDelaySet.remove(sessionId);
        sessionTotalBytes.remove(sessionId);
        sessionStartWallMs.remove(sessionId);
        sessionBytesSinceLastLog.remove(sessionId);
        sessionLastLogTime.remove(sessionId);
        sessionLogCount.remove(sessionId);
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
