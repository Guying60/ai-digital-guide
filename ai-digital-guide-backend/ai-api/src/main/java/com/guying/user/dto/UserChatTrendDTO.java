package com.guying.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserChatTrendDTO {
    private Integer totalCount;
    private List<DailyItem> dailyList;

    @Data
    public static class DailyItem {
        private String date;   // 原始日期格式，例如 "2026-04-01"
        private Integer count; // 原始数量
    }
}
