package com.guying.service.impl;

import com.guying.pojo.vo.EmotionFocusCardVO;
import com.guying.pojo.vo.EmotionOverviewVO;
import com.guying.pojo.vo.EmotionTrendVO;
import com.guying.pojo.vo.FaceEmotionTrendVO;
import com.guying.service.AnalysisService;
import com.guying.service.EmotionOverviewService;
import com.guying.service.FaceEmotionAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 情感概览服务实现：直接复用已有的 AnalysisService（文本情感 + 关注点）
 * 和 FaceEmotionAnalysisService（面部表情），做字段级搬运组装。
 * 不重写聚合 SQL、不重算 rate，保证三端数据口径一致。
 */
@Service
public class EmotionOverviewServiceImpl implements EmotionOverviewService {

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private FaceEmotionAnalysisService faceEmotionAnalysisService;

    @Override
    public EmotionOverviewVO getOverview(Long attractionId, Integer days) {
        EmotionTrendVO textTrend = analysisService.getEmotionTrend(attractionId, days);
        FaceEmotionTrendVO faceTrend = faceEmotionAnalysisService.getExpressionTrend(attractionId, days);
        EmotionFocusCardVO card = analysisService.getEmotionFocusCard(attractionId, days);

        EmotionOverviewVO vo = new EmotionOverviewVO();

        // dates：两路用同一 days 生成相同日期序列，取文本路的即可
        vo.setDates(textTrend.getDates());

        // 文本情感
        vo.setTextPositiveCount(textTrend.getPositiveCount());
        vo.setTextNeutralCount(textTrend.getNeutralCount());
        vo.setTextNegativeCount(textTrend.getNegativeCount());
        vo.setTextPositiveRate(textTrend.getPositiveRate());
        vo.setTextNeutralRate(textTrend.getNeutralRate());
        vo.setTextNegativeRate(textTrend.getNegativeRate());
        vo.setTextTotalPositiveRate(textTrend.getTotalPositiveRate());
        vo.setTextTotalNeutralRate(textTrend.getTotalNeutralRate());
        vo.setTextTotalNegativeRate(textTrend.getTotalNegativeRate());
        vo.setTextRecordCount(sum(textTrend.getPositiveCount(), textTrend.getNeutralCount(),
                textTrend.getNegativeCount()));

        // 面部表情
        vo.setFaceJoyCount(faceTrend.getJoyCount());
        vo.setFaceSurpriseCount(faceTrend.getSurpriseCount());
        vo.setFaceNeutralCount(faceTrend.getNeutralCount());
        vo.setFaceConfusionCount(faceTrend.getConfusionCount());
        vo.setFaceDisgustCount(faceTrend.getDisgustCount());
        vo.setFaceAngerCount(faceTrend.getAngerCount());
        vo.setFaceSadnessCount(faceTrend.getSadnessCount());
        vo.setFaceJoyRate(faceTrend.getJoyRate());
        vo.setFaceSurpriseRate(faceTrend.getSurpriseRate());
        vo.setFaceNeutralRate(faceTrend.getNeutralRate());
        vo.setFaceConfusionRate(faceTrend.getConfusionRate());
        vo.setFaceDisgustRate(faceTrend.getDisgustRate());
        vo.setFaceAngerRate(faceTrend.getAngerRate());
        vo.setFaceSadnessRate(faceTrend.getSadnessRate());
        vo.setFaceTotalJoyRate(faceTrend.getTotalJoyRate());
        vo.setFaceTotalSurpriseRate(faceTrend.getTotalSurpriseRate());
        vo.setFaceTotalNeutralRate(faceTrend.getTotalNeutralRate());
        vo.setFaceTotalConfusionRate(faceTrend.getTotalConfusionRate());
        vo.setFaceTotalDisgustRate(faceTrend.getTotalDisgustRate());
        vo.setFaceTotalAngerRate(faceTrend.getTotalAngerRate());
        vo.setFaceTotalSadnessRate(faceTrend.getTotalSadnessRate());
        vo.setFaceRecordCount(sum(faceTrend.getJoyCount(), faceTrend.getSurpriseCount(),
                faceTrend.getNeutralCount(), faceTrend.getConfusionCount(),
                faceTrend.getDisgustCount(), faceTrend.getAngerCount(),
                faceTrend.getSadnessCount()));

        // 关注点（文本来源，面部无此概念）
        vo.setTopFocus(card.getTopFocus());
        vo.setWorstFocus(card.getWorstFocus());

        return vo;
    }

    @SafeVarargs
    private static int sum(java.util.List<Integer>... lists) {
        int total = 0;
        for (java.util.List<Integer> list : lists) {
            if (list != null) {
                for (Integer v : list) {
                    if (v != null) total += v;
                }
            }
        }
        return total;
    }
}
