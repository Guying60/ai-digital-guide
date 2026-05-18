package com.guying.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guying.mapper.UserTourHistoryMapper;
import com.guying.pojo.entity.UserTourHistory;
import com.guying.user.dto.UserChatTrendDTO;
import com.guying.user.dto.UserSatisfactionTrendDTO;
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

    @Override
    public UserSatisfactionTrendDTO getUserSatisfactionTrend(Long attractionId, Integer days) {
        List<UserSatisfactionTrendDTO.SatisfactionItem> itemList = userTourHistoryMapper.getSatisfactionTrend(attractionId, days);
        Double totalAvgScore = userTourHistoryMapper.selectTotalAvgScore(attractionId, days);
        UserSatisfactionTrendDTO dto = new UserSatisfactionTrendDTO();
        dto.setItemList(itemList != null ? itemList : List.of());
        dto.setTotalAvgScore(totalAvgScore);
        return dto;
    }

    @Override
    public List<String> getFeedbackText(Long attractionId, Integer days) {
        LocalDate today = LocalDate.now();
        LocalDateTime startTime = today.minusDays(days).atStartOfDay();
        LocalDateTime endTime = today.minusDays(1).atTime(LocalTime.MAX);

        LambdaQueryWrapper<UserTourHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(UserTourHistory::getFeedbackText)
                .eq(UserTourHistory::getAttractionId, attractionId)
                .isNotNull(UserTourHistory::getFeedbackText)
                .ne(UserTourHistory::getFeedbackText, "")
                .between(UserTourHistory::getCreateTime, startTime, endTime)
                .apply("TIMESTAMPDIFF(MINUTE, create_time, update_time) > 5")
                .apply("CHAR_LENGTH(feedback_text) BETWEEN 5 AND 18")
                .orderByDesc(UserTourHistory::getCreateTime);

        return userTourHistoryMapper.selectList(wrapper).stream()
                .map(UserTourHistory::getFeedbackText)
                .toList();
    }
}
