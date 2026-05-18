package com.guying.pojo.vo;

import lombok.Data;

import java.util.List;

@Data
public class EmotionTrendVO {
    private List<String> dates;

    private List<Integer> positiveCount;
    private List<Integer> neutralCount;
    private List<Integer> negativeCount;

    private List<Double> positiveRate;
    private List<Double> neutralRate;
    private List<Double> negativeRate;

    // 环形图用：整体占比
    private Double totalPositiveRate;
    private Double totalNeutralRate;
    private Double totalNegativeRate;
}
