package com.guying.converter;

import com.guying.pojo.vo.ChatTrendVO;
import com.guying.pojo.vo.HotFaqChartVO;
import com.guying.user.dto.UserChatTrendDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface StatConverter {
    @Mapping(source = "totalCount", target = "summary.totalChats")
    @Mapping(source = "dailyList", target = "trendList")
    ChatTrendVO convertToChatTrendVO(UserChatTrendDTO userChatTrendDTO);

    @Mapping(source = "date", target = "time")
    ChatTrendVO.Trend itemToTrend(UserChatTrendDTO.DailyItem item);

}
