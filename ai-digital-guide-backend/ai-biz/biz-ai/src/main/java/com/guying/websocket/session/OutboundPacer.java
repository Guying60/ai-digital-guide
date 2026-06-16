package com.guying.websocket.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.WebSocketMessage;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 按全局 PTS 时钟节流的视频出站发送器（每会话一个）。
 *
 * <p>背景：后端原先在 MuseTalk reader 线程里逐帧直接 {@code sender.send}，没有任何发送节拍。
 * 整句视频帧被瞬间灌进下游 TCP/网络缓冲（bufferbloat），缓冲只能按链路速率慢慢排到客户端，
 * 于是逐帧到达时延单调累积，前端按 40ms/帧实时消费的视频队列在中后期被抽干（画面冻结）。
 *
 * <p>本类把视频帧放进一个<b>有界</b>队列，由一条专用线程按帧的全局 PTS 在墙钟上择时释放：
 * <ul>
 *   <li>起播 {@link #PREBUFFER_MS} 内的 PTS 不等待、立即释放，保证前端快速攒够起播帧；</li>
 *   <li>之后按 {@link #PACE_RATE}（略快于实时）匀速释放，使帧 just-in-time 到达，
 *       既不再撑爆下游缓冲，又始终领先 1.0× 的消费速度，队列不饿死；</li>
 *   <li>有界队列在满时阻塞入队线程，从而反压 MuseTalk（其产帧本有富余）。</li>
 * </ul>
 *
 * <p>仅节流视频。音频体量小（不构成 bufferbloat）且与同句视频 PTS 区间重叠但产生更早，
 * 混入同一 FIFO 会破坏节拍，故音频与控制消息仍走直发，不经本类。
 */
@Slf4j
public class OutboundPacer {

    /** 释放速率相对实时的倍数：>1 保证供给始终领先消费，又远低于原先的瞬时爆发。 */
    private static final double PACE_RATE = 1.25;
    /** 起播预灌窗口：此 PTS 跨度内的帧不做节流，立即释放，避免拖慢首帧起播。 */
    private static final long PREBUFFER_MS = 1500;
    /** 有界队列容量（帧）：约 256*40ms≈10s，作为反压触发点与内存上限。 */
    private static final int QUEUE_CAPACITY = 256;

    private record Item(int globalPtsMs, WebSocketMessage<?> msg, int epoch) {}

    private final String sid;
    private final Consumer<WebSocketMessage<?>> sink;
    private final BlockingQueue<Item> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicInteger epoch = new AtomicInteger(0);
    private final Thread worker;

    private volatile boolean running = true;
    private volatile boolean reanchor = false;

    // ↓ 仅 worker 线程读写：节拍时钟
    private boolean clockAnchored = false;
    private long clockStartWallMs = 0;
    private long clockStartPtsMs = 0;

    // ↓ 仅入队线程（MuseTalk reader）读写：用于给控制消息排到末帧之后
    private int lastVideoGlobalPts = 0;

    public OutboundPacer(String sid, Consumer<WebSocketMessage<?>> sink) {
        this.sid = sid;
        this.sink = sink;
        this.worker = new Thread(this::run, "outbound-pacer-" + sid);
        this.worker.setDaemon(true);
        this.worker.start();
    }

    /** 入队一帧视频，按其全局 PTS 节流释放。队列满时阻塞，从而反压上游。 */
    public void enqueueVideo(int globalPtsMs, WebSocketMessage<?> msg) {
        lastVideoGlobalPts = globalPtsMs;
        put(new Item(globalPtsMs, msg, epoch.get()));
    }

    /** 入队一条与视频流同序的控制消息（如句末 done），排在本句末帧之后释放。 */
    public void enqueueControl(WebSocketMessage<?> msg) {
        put(new Item(lastVideoGlobalPts, msg, epoch.get()));
    }

    private void put(Item it) {
        try {
            queue.put(it);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 打断/新一轮：作废在途帧、清空队列、重置时钟，并唤醒 worker 立即重新起播。 */
    public void reset() {
        epoch.incrementAndGet();
        queue.clear();
        reanchor = true;
        worker.interrupt();
    }

    /** 会话关闭：停止线程。 */
    public void shutdown() {
        running = false;
        worker.interrupt();
    }

    private void run() {
        while (running) {
            Item it;
            try {
                it = queue.take();
            } catch (InterruptedException e) {
                continue; // reset 或 shutdown：回到 while 判定
            }
            int cur = epoch.get();
            if (it.epoch() != cur) continue; // 旧 epoch（reset 前入队），丢弃

            if (reanchor) {
                reanchor = false;
                clockAnchored = false;
            }
            if (!clockAnchored) {
                clockStartWallMs = System.currentTimeMillis();
                clockStartPtsMs = it.globalPtsMs();
                clockAnchored = true;
            }

            long offset = (long) (Math.max(0, it.globalPtsMs() - clockStartPtsMs - PREBUFFER_MS) / PACE_RATE);
            long sleep = (clockStartWallMs + offset) - System.currentTimeMillis();
            if (sleep > 0) {
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException e) {
                    continue; // reset/shutdown：丢弃当前帧，回到 while 判定
                }
            }
            if (it.epoch() != epoch.get()) continue; // 等待期间发生了 reset

            try {
                sink.accept(it.msg());
            } catch (Exception e) {
                log.warn("[OutboundPacer] 发送失败 sid={}", sid, e);
            }
        }
        log.info("[OutboundPacer] worker 退出 sid={}", sid);
    }
}
