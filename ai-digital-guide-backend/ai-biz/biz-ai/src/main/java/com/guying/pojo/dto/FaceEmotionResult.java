package com.guying.pojo.dto;

import lombok.Data;

/**
 * 视觉大模型面部表情分类结果，供 BeanOutputConverter 解析大模型返回的 JSON。
 * expression 必须为 ExpressionEnum.desc 之一：喜悦/惊讶/中性/困惑/厌恶/愤怒/悲伤。
 */
@Data
public class FaceEmotionResult {
    /** 表情描述：喜悦/惊讶/中性/困惑/厌恶/愤怒/悲伤 */
    private String expression;
    /** 置信度 0~1 */
    private Double confidence;
    /** 简短理由（可选），落库到 detail 字段 */
    private String reason;
}
