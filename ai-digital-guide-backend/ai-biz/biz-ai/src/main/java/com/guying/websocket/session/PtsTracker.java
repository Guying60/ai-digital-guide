package com.guying.websocket.session;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 全局 PTS 追踪器。
 * 由 MuseTalkConnector（视频）和 CosyVoiceConnector（音频）共享，
 * 将每句话从 0 开始的本地 PTS 转换为跨句连续递增的全局 PTS。
 */
public class PtsTracker {

    private long globalPtsOffsetMs = 0;
    private int lastSentenceMaxPtsMs = 0;
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * 将本地 PTS 转换为全局 PTS，并跟踪当前句最大值。
     * 线程安全，可被视频/音频帧的接收线程并发调用。
     */
    public int toGlobal(int localPtsMs) {
        lock.lock();
        try {
            lastSentenceMaxPtsMs = Math.max(lastSentenceMaxPtsMs, localPtsMs);
            return (int) (localPtsMs + globalPtsOffsetMs);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 句子结束时调用，推进全局偏移量。
     * 必须在该句所有帧发送完毕后调用（通常在 MuseTalk 的 done 消息时）。
     */
    public void advanceOnDone() {
        lock.lock();
        try {
            globalPtsOffsetMs += lastSentenceMaxPtsMs + 40; // +1 帧间隔(40ms)
            lastSentenceMaxPtsMs = 0;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 句子被中断时调用，丢弃当前句的 PTS 累计，但保留全局偏移。
     * 下一句将从上一个完成句的末尾继续。
     */
    public void onInterrupt() {
        lock.lock();
        try {
            lastSentenceMaxPtsMs = 0;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 重置状态（新一轮对话开始时调用）。
     */
    public void reset() {
        lock.lock();
        try {
            globalPtsOffsetMs = 0;
            lastSentenceMaxPtsMs = 0;
        } finally {
            lock.unlock();
        }
    }
}
