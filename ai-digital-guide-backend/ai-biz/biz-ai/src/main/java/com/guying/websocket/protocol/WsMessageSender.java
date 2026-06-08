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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 统一封装向用户 WebSocket 写消息的逻辑：
 *  - sendJson / send：高优先级消息（音频帧、控制指令），走 session 锁确保线程安全；
 *  - sendVideoFrame：高频视频帧走无锁队列 + 独立 virtual thread 消费，
 *                    绝不阻塞 NIO 接收线程，队列满时丢弃最旧帧；
 *  - sendError：异常路径下的错误帧，不走锁，尽力发送。
 */
@Component
@Slf4j
public class WsMessageSender {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ---- 视频帧异步下发（生产者-消费者解耦） ----

    /** 每会话一个视频帧缓冲队列，容量小以保证只保留最新帧、防止 OOM */
    private final ConcurrentHashMap<String, LinkedBlockingQueue<Object>> videoQueues = new ConcurrentHashMap<>();
    private static final int VIDEO_QUEUE_CAPACITY = 200;

    /** 队列哨兵：一句 done，保证在其之前的所有视频帧全部发完后才送达 Android。 */
    private record DoneMarker(int sentenceId, String doneJson) {}

    /**
     * 高频视频帧投递（由 MuseTalk NIO 线程调用）。
     * 非阻塞，绝不让 NIO 线程等待网络 I/O。
     * 队列满时按 GOP 丢弃：从队首丢弃整段不完整 GOP（直到下一个关键帧），
     * 避免丢中间 P 帧导致 H.264 参考链断裂、客户端长时间花屏。
     */
    public void sendVideoFrame(ChatSessionContext ctx, BinaryMessage message) {
        if (ctx == null) return;
        WebSocketSession session = ctx.getUserSession();
        if (session == null || !session.isOpen()) return;

        LinkedBlockingQueue<Object> queue = videoQueues.get(ctx.getSid());
        if (queue == null) return; // 消费线程尚未启动，直接丢弃

        byte[] payload = message.getPayload().array();
        while (!queue.offer(payload)) {
            dropOneGop(queue); // 队满：丢弃队首一段 GOP 腾空间
        }
    }

    /**
     * 从队首丢弃至少一个元素，并继续丢弃后续非关键帧，直到队首为关键帧 / done 标记 / 队空。
     * 保证丢弃后队首仍是一个可独立解码的起点（关键帧），不破坏参考链。
     */
    private void dropOneGop(LinkedBlockingQueue<Object> queue) {
        if (queue.poll() == null) return; // 已空
        Object head;
        while ((head = queue.peek()) != null) {
            if (head instanceof DoneMarker) break;            // 保留 done 标记
            if (head instanceof byte[] f && isKeyFrame(f)) break; // 队首已是关键帧，停止
            queue.poll();
        }
    }

    /** Android payload 布局 [0x03][sentence_id:2B][pts_ms:4B][is_keyframe:1B][AU...]，offset 7 为关键帧标志。 */
    private static boolean isKeyFrame(byte[] androidPayload) {
        return androidPayload.length > 7 && androidPayload[7] == 1;
    }

    /** 将 done 消息注入视频帧队列末尾，保证 done 在其所属句子的所有视频帧之后送达 Android。 */
    public void enqueueDone(ChatSessionContext ctx, int sentenceId) {
        if (ctx == null) return;
        WebSocketSession session = ctx.getUserSession();
        if (session == null || !session.isOpen()) return;

        LinkedBlockingQueue<Object> queue = videoQueues.get(ctx.getSid());
        if (queue == null) return;

        String doneJson = objectMapper.createObjectNode()
                .put("type", "done")
                .put("sentence_id", sentenceId)
                .toString();
        queue.offer(new DoneMarker(sentenceId, doneJson));
    }

    /** 清空视频帧队列中的待发送帧（interrupt 时使用），不删除队列本身。 */
    public void clearVideoQueue(String sid) {
        LinkedBlockingQueue<Object> queue = videoQueues.get(sid);
        if (queue != null) {
            queue.clear();
        }
    }

    /**
     * 为该会话启动视频帧消费 virtual thread。
     * 在会话建立时调用一次，会话关闭时由 {@link #stopVideoDrain} 清理。
     */
    public void startVideoDrain(ChatSessionContext ctx) {
        String sid = ctx.getSid();
        LinkedBlockingQueue<Object> queue = new LinkedBlockingQueue<>(VIDEO_QUEUE_CAPACITY);
        videoQueues.put(sid, queue);

        Thread.startVirtualThread(() -> {
            ReentrantLock lock = ctx.getSendLock();
            try {
                while (ctx.getUserSession().isOpen()) {
                    Object item = queue.poll(200, TimeUnit.MILLISECONDS);
                    if (item == null) continue;

                    if (item instanceof byte[] frame) {
                        lock.lock();
                        try {
                            if (ctx.getUserSession().isOpen()) {
                                ctx.getUserSession().sendMessage(new BinaryMessage(frame));
                            } else {
                                break;
                            }
                        } catch (IOException e) {
                            log.error("视频帧发送失败 sid={}", sid, e);
                        } finally {
                            lock.unlock();
                        }
                    } else if (item instanceof DoneMarker done) {
                        lock.lock();
                        try {
                            if (ctx.getUserSession().isOpen()) {
                                ctx.getUserSession().sendMessage(new TextMessage(done.doneJson()));
                            }
                        } catch (IOException e) {
                            log.error("done 消息发送失败 sid={}", sid, e);
                        } finally {
                            lock.unlock();
                        }
                        // 背压：通知 Python 该句全部帧已送达 Android
                        WebSocketSession muse = ctx.getMuseTalkSession();
                        if (muse != null && muse.isOpen()) {
                            try {
                                String ackJson = objectMapper.createObjectNode()
                                        .put("type", "ack")
                                        .put("sentence_id", done.sentenceId())
                                        .toString();
                                muse.sendMessage(new TextMessage(ackJson));
                            } catch (IOException e) {
                                log.error("ack 发送到 Python 失败 sid={}", sid, e);
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                videoQueues.remove(sid);
            }
        });
    }

    /** 会话关闭时清理视频帧队列，消费线程会在下次 poll 超时后自动退出。 */
    public void stopVideoDrain(String sid) {
        videoQueues.remove(sid);
    }

    // ---- 高优先级消息（音频、控制指令），仍走锁保证线程安全 ----

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
