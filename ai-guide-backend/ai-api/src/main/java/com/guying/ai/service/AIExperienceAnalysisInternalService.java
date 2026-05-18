package com.guying.ai.service;

import com.guying.ai.dto.EmotionStatDTO;
import com.guying.ai.dto.EmotionTrendDTO;
import com.guying.ai.dto.FocusStatDTO;

import java.util.List;

public interface AIExperienceAnalysisInternalService {
    List<EmotionTrendDTO> getEmotionTrend(Long attractionId, Integer days);


    EmotionStatDTO getEmotionStat(Long attractionId, Integer days);

    List<FocusStatDTO> getFocusStat(Long attractionId, Integer days);

    List<FocusStatDTO> getWorstFocus(Long attractionId, Integer days);
}
