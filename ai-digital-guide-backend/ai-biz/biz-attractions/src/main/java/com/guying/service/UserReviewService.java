package com.guying.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.guying.common.result.ScrollResult;
import com.guying.pojo.dto.UserReviewPageQueryDTO;
import com.guying.pojo.dto.UserReviewSubmitDTO;
import com.guying.pojo.entity.UserReview;
import com.guying.pojo.vo.UserReviewVO;
import com.guying.user.dto.UserSatisfactionTrendDTO;

import java.util.List;

public interface UserReviewService extends IService<UserReview> {

    /**
     * 游标分页查询当前用户的评价列表
     */
    ScrollResult<UserReviewVO> getMyReviews(UserReviewPageQueryDTO dto, Long userId);

    /**
     * 提交评价（待评价 → 已评价）
     */
    void submitReview(UserReviewSubmitDTO dto, Long userId);

    /**
     * 删除评价（逻辑删除）
     */
    void deleteReview(Long reviewId, Long userId);

    /**
     * 创建待评价记录（对话结束时自动调用，若已存在则跳过）
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
