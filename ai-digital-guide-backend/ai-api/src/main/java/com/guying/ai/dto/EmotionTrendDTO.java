package com.guying.ai.dto;

import lombok.Data;

import java.util.List;

@Data
public class EmotionTrendDTO {
    private String date;    // "04-01"
    private Integer emotion; // 0=正面 1=中性 2=负面
    private Integer count;
}
