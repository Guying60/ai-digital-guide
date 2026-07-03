package com.guying.ai.dto;

import lombok.Data;

/**
 * 面部表情趋势聚合行：某景点某日某表情的计数。
 * date 格式 "MM-dd"，expression 为 ExpressionEnum.code（0喜悦 1惊讶 2中性 3困惑 4厌恶 5愤怒 6悲伤）。
 */
@Data
public class FaceEmotionTrendDTO {
    private String date;
    private Integer expression;
    private Integer count;
}
