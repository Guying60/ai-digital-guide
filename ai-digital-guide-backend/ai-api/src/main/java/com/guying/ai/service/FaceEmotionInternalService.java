package com.guying.ai.service;

import com.guying.ai.dto.FaceEmotionTrendDTO;

import java.util.List;

/**
 * 面部表情情感分析的内部服务接口（依赖倒置层）。
 * biz-ai 实现，biz-admin 通过本接口消费聚合数据，避免 biz 模块间循环依赖。
 * 参照 AIExperienceAnalysisInternalService 的设计。
 */
public interface FaceEmotionInternalService {

    /**
     * 按景点+天数聚合面部表情趋势（按日 + 按表情分组计数）。
     *
     * @param attractionId 景点 ID
     * @param days         近 N 天
     * @return 每行：date(MM-dd) / expression(code) / count
     */
    List<FaceEmotionTrendDTO> getExpressionTrend(Long attractionId, Integer days);
}
