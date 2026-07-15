package com.guying.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.guying.common.result.ScrollResult;
import com.guying.converter.UserReviewConverter;
import com.guying.exception.ServiceException;
import com.guying.mapper.AttractionsMapper;
import com.guying.mapper.UserReviewMapper;
import com.guying.pojo.dto.UserReviewPageQueryDTO;
import com.guying.pojo.dto.UserReviewSubmitDTO;
import com.guying.pojo.entity.Attraction;
import com.guying.pojo.entity.UserReview;
import com.guying.pojo.vo.UserReviewVO;
import com.guying.service.UserReviewService;
import com.guying.attractions.dto.FeedbackItemDTO;
import com.guying.user.dto.UserSatisfactionTrendDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserReviewServiceImpl extends ServiceImpl<UserReviewMapper, UserReview> implements UserReviewService {

    @Autowired
    private UserReviewConverter userReviewConverter;

    @Autowired
    private AttractionsMapper attractionsMapper;

    @Override
    public ScrollResult<UserReviewVO> getMyReviews(UserReviewPageQueryDTO dto, Long userId) {
        LambdaQueryWrapper<UserReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserReview::getUserId, userId)
                .eq(UserReview::getIsDeleted, 0)
                .eq(dto.getStatus() != null, UserReview::getStatus, dto.getStatus())
                .lt(StringUtils.hasText(dto.getLastId()), UserReview::getId, dto.getLastId())
                .orderByDesc(UserReview::getId)
                .last("limit " + (dto.getPageSize() + 1));

        List<UserReview> reviewList = list(wrapper);
        boolean hasMore = reviewList.size() > dto.getPageSize();
        if (hasMore) {
            reviewList.removeLast();
        }
        Long nextLastId = reviewList.isEmpty() ? null : reviewList.getLast().getId();

        // 转换为 VO
        List<UserReviewVO> voList = userReviewConverter.toVOList(reviewList);

        // 批量补充景点名称和封面
        List<Long> attractionIds = voList.stream()
                .map(UserReviewVO::getAttractionId)
                .distinct()
                .toList();

        if (!attractionIds.isEmpty()) {
            List<Attraction> attractions = attractionsMapper.selectBatchIds(attractionIds);
            Map<Long, Attraction> attractionMap = attractions.stream()
                    .collect(Collectors.toMap(Attraction::getId, a -> a));

            voList.forEach(vo -> {
                Attraction attraction = attractionMap.get(vo.getAttractionId());
                if (attraction != null) {
                    vo.setAttractionName(attraction.getAttractionName());
                    vo.setCoverUrl(attraction.getCoverUrl());
                }
            });
        }

        return new ScrollResult<>(voList, nextLastId, hasMore);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void submitReview(UserReviewSubmitDTO dto, Long userId) {
        UserReview review = getById(dto.getReviewId());
        if (review == null || !review.getUserId().equals(userId)) {
            throw new ServiceException("评价记录不存在");
        }
        if (review.getStatus() == 1) {
            throw new ServiceException("该评价已提交，请勿重复提交");
        }

        review.setRating(dto.getRating());
        review.setContent(dto.getContent());
        review.setTags(dto.getTags());
        review.setStatus(1);
        updateById(review);

        log.info("用户提交评价成功, userId={}, reviewId={}", userId, dto.getReviewId());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteReview(Long reviewId, Long userId) {
        UserReview review = getById(reviewId);
        if (review == null || !review.getUserId().equals(userId)) {
            throw new ServiceException("评价记录不存在");
        }

        review.setIsDeleted(1);
        updateById(review);

        log.info("用户删除评价成功, userId={}, reviewId={}", userId, reviewId);
    }

    @Override
    public void createPendingReview(Long userId, Long attractionId, String conversationId) {
        // 按会话去重：每次游览（conversationId）对应一条待评价，与旅游历史一一对应
        LambdaQueryWrapper<UserReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserReview::getUserId, userId)
                .eq(UserReview::getConversationId, conversationId)
                .eq(UserReview::getIsDeleted, 0);
        Long count = count(wrapper);
        if (count > 0) {
            log.debug("待评价记录已存在, userId={}, conversationId={}", userId, conversationId);
            return;
        }

        UserReview review = new UserReview();
        review.setUserId(userId);
        review.setAttractionId(attractionId);
        review.setConversationId(conversationId);
        review.setStatus(0);
        review.setIsDeleted(0);
        save(review);

        log.info("自动创建待评价记录, userId={}, attractionId={}, conversationId={}", userId, attractionId, conversationId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void submitByConversationId(String conversationId, Long userId, Integer score, String feedbackText) {
        // 按 conversationId 查找该次游览的待评价记录
        LambdaQueryWrapper<UserReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserReview::getConversationId, conversationId)
                .eq(UserReview::getUserId, userId)
                .eq(UserReview::getIsDeleted, 0)
                .eq(UserReview::getStatus, 0);
        UserReview review = getOne(wrapper);
        if (review == null) {
            log.warn("未找到待评价记录, userId={}, conversationId={}", userId, conversationId);
            return;
        }

        review.setRating(BigDecimal.valueOf(score));
        review.setContent(feedbackText);
        review.setStatus(1);
        updateById(review);

        log.info("通过对话ID提交评价成功, userId={}, conversationId={}", userId, conversationId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deletePendingReviewByConversationId(String conversationId, Long userId) {
        LambdaQueryWrapper<UserReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserReview::getConversationId, conversationId)
                .eq(UserReview::getUserId, userId)
                .eq(UserReview::getIsDeleted, 0)
                .eq(UserReview::getStatus, 0);
        List<UserReview> pendingReviews = list(wrapper);
        if (pendingReviews.isEmpty()) {
            return;
        }
        pendingReviews.forEach(r -> r.setIsDeleted(1));
        updateBatchById(pendingReviews);
        log.info("按会话ID清理待评价记录, userId={}, conversationId={}, count={}", userId, conversationId, pendingReviews.size());
    }

    @Override
    public UserSatisfactionTrendDTO getSatisfactionTrend(Long attractionId, Integer days) {
        List<UserSatisfactionTrendDTO.SatisfactionItem> itemList =
                baseMapper.getSatisfactionTrend(attractionId, days);
        Double totalAvgScore = baseMapper.selectTotalAvgScore(attractionId, days);

        UserSatisfactionTrendDTO dto = new UserSatisfactionTrendDTO();
        dto.setItemList(itemList != null ? itemList : List.of());
        dto.setTotalAvgScore(totalAvgScore);
        return dto;
    }

    @Override
    public List<FeedbackItemDTO> getFeedbackText(Long attractionId, Integer days) {
        LocalDate today = LocalDate.now();
        LocalDateTime startTime = today.minusDays(days).atStartOfDay();
        LocalDateTime endTime = today.minusDays(1).atTime(LocalTime.MAX);

        List<FeedbackItemDTO> result = baseMapper.selectFeedbackText(attractionId, startTime, endTime);
        return result != null ? result : List.of();
    }
}
