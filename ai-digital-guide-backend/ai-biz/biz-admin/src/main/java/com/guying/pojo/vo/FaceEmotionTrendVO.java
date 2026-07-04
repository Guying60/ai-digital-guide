package com.guying.pojo.vo;

import lombok.Data;

import java.util.List;

/**
 * 面部表情趋势 VO：按日 + 7 个表情维度。
 * 维度顺序固定对应 ExpressionEnum：喜悦/惊讶/中性/困惑/厌恶/愤怒/悲伤。
 * 各 count/rate 数组下标与 dates 一一对齐；无数据日期 count 为 0、rate 为 0.0，日期序列不断档。
 */
@Data
public class FaceEmotionTrendVO {
    private List<String> dates;

    private List<Integer> joyCount;
    private List<Integer> surpriseCount;
    private List<Integer> neutralCount;
    private List<Integer> confusionCount;
    private List<Integer> disgustCount;
    private List<Integer> angerCount;
    private List<Integer> sadnessCount;

    private List<Double> joyRate;
    private List<Double> surpriseRate;
    private List<Double> neutralRate;
    private List<Double> confusionRate;
    private List<Double> disgustRate;
    private List<Double> angerRate;
    private List<Double> sadnessRate;

    // 环形图用：所选时间段内各表情整体占比
    private Double totalJoyRate;
    private Double totalSurpriseRate;
    private Double totalNeutralRate;
    private Double totalConfusionRate;
    private Double totalDisgustRate;
    private Double totalAngerRate;
    private Double totalSadnessRate;
}
