package com.guying.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserSatisfactionTrendDTO {
    private Double totalAvgScore;
    private List<SatisfactionItem> itemList;

    @Data
    public static class SatisfactionItem {
        private String date;
        private Double avgScore;
        private Integer count;
    }
}
