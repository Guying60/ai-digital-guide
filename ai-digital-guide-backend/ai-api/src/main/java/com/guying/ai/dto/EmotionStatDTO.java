package com.guying.ai.dto;

import lombok.Data;

@Data
public class EmotionStatDTO {
    private Integer total;
    private Integer positiveCount;
    private Integer preTotal;
    private Integer prePositiveCount;
}