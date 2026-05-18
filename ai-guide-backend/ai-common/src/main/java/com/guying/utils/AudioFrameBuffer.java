package com.guying.utils;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class AudioFrameBuffer {
    // 最大缓冲帧数，假设一帧20ms，50帧就是1秒
    private static final int MAX_FRAMES = 50; 
    
    private final ConcurrentLinkedQueue<byte[]> queue = new ConcurrentLinkedQueue<>();
    // 用 AtomicInteger 替代 queue.size()，实现 O(1) 获取大小
    private final AtomicInteger frameCount = new AtomicInteger(0);

    /**
     * 写入音频帧
     */
    public void offer(byte[] frame) {
        queue.offer(frame);
        // 先增加计数，如果超标了，就踢掉最老的一帧（滑动窗口）
        if (frameCount.incrementAndGet() > MAX_FRAMES) {
            queue.poll();
            frameCount.decrementAndGet();
        }
    }

    /**
     * NLS 连上后，一次性倒出所有缓存
     */
    public void drainTo(Consumer<byte[]> consumer) {
        byte[] frame;
        // 只要队列里有数据，就一直 poll
        while ((frame = queue.poll()) != null) {
            frameCount.decrementAndGet();
            consumer.accept(frame);
        }
    }
    
    public void clear() {
        queue.clear();
        frameCount.set(0);
    }
}