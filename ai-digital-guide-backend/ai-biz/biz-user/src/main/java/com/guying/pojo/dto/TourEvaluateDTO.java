package com.guying.pojo.dto;

import lombok.Data;

@Data
public class TourEvaluateDTO {
    private String conversationId;

    // 游客的评分 (比如 1-5)
    private Integer score;
    
    private String feedbackText;
}