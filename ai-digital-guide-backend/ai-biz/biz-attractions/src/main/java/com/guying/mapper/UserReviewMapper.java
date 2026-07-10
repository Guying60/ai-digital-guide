package com.guying.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guying.pojo.entity.UserReview;
import com.guying.attractions.dto.AttractionReviewAggregateDTO;
import com.guying.attractions.dto.FeedbackItemDTO;
import com.guying.user.dto.UserSatisfactionTrendDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserReviewMapper extends BaseMapper<UserReview> {

    List<UserSatisfactionTrendDTO.SatisfactionItem> getSatisfactionTrend(@Param("attractionId") Long attractionId, @Param("days") Integer days);

    Double selectTotalAvgScore(@Param("attractionId") Long attractionId, @Param("days") Integer days);

    List<FeedbackItemDTO> selectFeedbackText(@Param("attractionId") Long attractionId,
                                             @Param("startTime") LocalDateTime startTime,
                                             @Param("endTime") LocalDateTime endTime);

    /**
     * 批量查询多个景点的全量（无时间窗口）平均分与评论数。
     * 口径：status=1 AND rating IS NOT NULL AND is_deleted=0
     * @param attractionIds 景点ID集合；为空时不应调用（service 层已兜底）
     */
    List<AttractionReviewAggregateDTO> selectBatchAggregates(@Param("attractionIds") List<Long> attractionIds);
}
