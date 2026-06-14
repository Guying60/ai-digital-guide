package com.guying.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guying.pojo.entity.UserTourHistory;
import com.guying.user.dto.UserChatTrendDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserTourHistoryMapper extends BaseMapper<UserTourHistory> {

    Long selectCountByAttractionAndDateRange(@Param("attractionId") Long attractionId,
                                              @Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime);

    List<UserChatTrendDTO.DailyItem> selectChatTrend(@Param("attractionId") Long attractionId,
                                                      @Param("startTime") LocalDateTime startTime,
                                                      @Param("endTime") LocalDateTime endTime,
                                                      @Param("groupByHour") boolean groupByHour);
}
