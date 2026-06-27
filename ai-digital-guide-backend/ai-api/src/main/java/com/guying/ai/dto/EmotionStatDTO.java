package com.guying.ai.dto;

import lombok.Data;

@Data
public class EmotionStatDTO {
    private Integer total;
    private Integer positiveCount;
    private Integer neutralCount;
    private Integer negativeCount;
    private Integer preTotal;
    private Integer prePositiveCount;
}