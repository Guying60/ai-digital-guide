package com.guying.service;

import com.guying.pojo.vo.EmotionOverviewVO;

/**
 * 情感概览服务：合并文本情感与面部表情两路数据，供管理后台统一展示。
 * 不重写聚合逻辑，直接复用已有的 AnalysisService 和 FaceEmotionAnalysisService。
 */
public interface EmotionOverviewService {

    /**
     * 获取某景点近 N 天的情感概览（文本 + 面部 + 关注点合并）。
     *
     * @param attractionId 景点 ID
     * @param days         天数（如 7 / 30）
     */
    EmotionOverviewVO getOverview(Long attractionId, Integer days);
}
