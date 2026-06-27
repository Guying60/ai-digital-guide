package com.guying.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guying.pojo.entity.UserReview;
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
}
