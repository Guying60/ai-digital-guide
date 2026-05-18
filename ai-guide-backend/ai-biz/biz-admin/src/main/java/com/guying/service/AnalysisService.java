package com.guying.service;

import com.guying.pojo.vo.EmotionFocusCardVO;
import com.guying.pojo.vo.EmotionTrendVO;
import com.guying.pojo.vo.SuggestionVO;

public interface AnalysisService {
    EmotionTrendVO getEmotionTrend(Long attractionId, Integer days);

    EmotionFocusCardVO getEmotionFocusCard(Long attractionId, Integer days);

    SuggestionVO getAiServiceSuggestion(Long attractionId, Integer type);
}
