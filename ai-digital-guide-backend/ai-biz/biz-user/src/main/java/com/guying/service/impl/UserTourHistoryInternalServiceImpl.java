package com.guying.service.impl;

import com.guying.mapper.UserTourHistoryMapper;
import com.guying.user.dto.UserChatTrendDTO;
import com.guying.user.service.UserTourHistoryInternalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class UserTourHistoryInternalServiceImpl implements UserTourHistoryInternalService {
    @Autowired
    private UserTourHistoryMapper userTourHistoryMapper;

    @Override
    public UserChatTrendDTO getUserChatTrend(Long attractionId, Integer days) {
        LocalDate today = LocalDate.now();
        LocalDateTime startTime = today.minusDays(days).atStartOfDay();
        LocalDateTime endTime = today.minusDays(1).atTime(LocalTime.MAX);

        Long total = userTourHistoryMapper.selectCountByAttractionAndDateRange(attractionId, startTime, endTime);
        List<UserChatTrendDTO.DailyItem> dailyList = userTourHistoryMapper.selectChatTrend(attractionId, startTime, endTime, days == 1);

        UserChatTrendDTO dto = new UserChatTrendDTO();
        dto.setTotalCount(total != null ? total.intValue() : 0);
        dto.setDailyList(dailyList != null ? dailyList : List.of());
        return dto;
    }
}
