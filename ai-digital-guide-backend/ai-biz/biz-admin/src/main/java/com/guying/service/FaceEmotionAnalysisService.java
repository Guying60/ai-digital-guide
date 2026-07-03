package com.guying.service;

import com.guying.pojo.vo.FaceEmotionTrendVO;

/**
 * 面部表情趋势分析服务（管理端）。参照 AnalysisService 的设计。
 */
public interface FaceEmotionAnalysisService {

    /**
     * 获取某景点近 N 天的面部表情趋势。
     *
     * @param attractionId 景点 ID
     * @param days         天数（如 7 / 30）
     */
    FaceEmotionTrendVO getExpressionTrend(Long attractionId, Integer days);
}
