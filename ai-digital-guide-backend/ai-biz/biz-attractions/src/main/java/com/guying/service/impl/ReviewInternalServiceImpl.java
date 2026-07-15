package com.guying.service.impl;

import com.guying.attractions.service.ReviewInternalService;
import com.guying.service.UserReviewService;
import com.guying.attractions.dto.FeedbackItemDTO;
import com.guying.user.dto.UserSatisfactionTrendDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class ReviewInternalServiceImpl implements ReviewInternalService {

    @Autowired
    private UserReviewService userReviewService;

    @Override
    public void createPendingReview(Long userId, Long attractionId, String conversationId) {
        try {
            userReviewService.createPendingReview(userId, attractionId, conversationId);
        } catch (Exception e) {
            log.error("创建待评价记录失败, userId={}, attractionId={}", userId, attractionId, e);
        }
    }

    @Override
    public void submitByConversationId(String conversationId, Long userId, Integer score, String feedbackText) {
        try {
            userReviewService.submitByConversationId(conversationId, userId, score, feedbackText);
        } catch (Exception e) {
            log.error("通过对话ID提交评价失败, userId={}, conversationId={}", userId, conversationId, e);
        }
    }

    @Override
    public void deletePendingReviewByConversationId(String conversationId, Long userId) {
        try {
            userReviewService.deletePendingReviewByConversationId(conversationId, userId);
        } catch (Exception e) {
            log.error("按会话ID清理待评价记录失败, userId={}, conversationId={}", userId, conversationId, e);
        }
    }

    @Override
    public UserSatisfactionTrendDTO getSatisfactionTrend(Long attractionId, Integer days) {
        return userReviewService.getSatisfactionTrend(attractionId, days);
    }

    @Override
    public List<FeedbackItemDTO> getFeedbackText(Long attractionId, Integer days) {
        return userReviewService.getFeedbackText(attractionId, days);
    }
}
