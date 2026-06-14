package com.guying.attractions.service;

import com.guying.user.dto.UserSatisfactionTrendDTO;

import java.util.List;

/**
 * 评价内部服务接口（供 biz-ai、biz-user 等外部模块调用）
 */
public interface ReviewInternalService {

    /**
     * 创建待评价记录（对话结束时自动调用，若已存在则跳过）
     *
     * @param userId         用户ID
     * @param attractionId   景点ID
     * @param conversationId 对话ID
     */
    void createPendingReview(Long userId, Long attractionId, String conversationId);

    /**
     * 通过对话ID提交评价（兼容旧版游览评价接口）
     */
    void submitByConversationId(String conversationId, Long userId, Integer score, String feedbackText);

    /**
     * 获取景点满意度趋势（按天）
     */
    UserSatisfactionTrendDTO getSatisfactionTrend(Long attractionId, Integer days);

    /**
     * 获取景点反馈文本列表（用于AI分析）
     */
    List<String> getFeedbackText(Long attractionId, Integer days);
}
