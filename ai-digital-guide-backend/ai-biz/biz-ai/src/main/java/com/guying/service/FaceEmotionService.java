package com.guying.service;

/**
 * 面部表情情感分析服务。
 * 接收 Android 前置摄像头低频采集的面部帧(base64)，调用视觉大模型做表情分类并落库。
 * 不存原始图像。
 */
public interface FaceEmotionService {

    /**
     * 异步分析一帧面部表情并落库。失败仅记录日志，不抛异常、不阻断调用方（WebSocket 线程）。
     *
     * @param base64Image    面部帧 base64（JPEG，无 data: 前缀）
     * @param userId         游客 ID
     * @param attractionId   景点 ID
     * @param conversationId 会话 ID
     */
    void analyze(String base64Image, Long userId, Long attractionId, String conversationId);
}
