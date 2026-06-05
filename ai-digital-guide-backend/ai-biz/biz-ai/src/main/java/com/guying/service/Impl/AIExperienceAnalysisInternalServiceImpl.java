package com.guying.service.Impl;

import com.guying.ai.dto.EmotionStatDTO;
import com.guying.ai.dto.EmotionTrendDTO;
import com.guying.ai.dto.FocusStatDTO;
import com.guying.ai.service.AIExperienceAnalysisInternalService;
import com.guying.mapper.ExperienceAnalysisMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class AIExperienceAnalysisInternalServiceImpl implements AIExperienceAnalysisInternalService {
    @Autowired
    private ExperienceAnalysisMapper experienceAnalysisMapper;


    @Override
    public List<EmotionTrendDTO> getEmotionTrend(Long attractionId, Integer days) {
        return experienceAnalysisMapper.getEmotionTrend(attractionId, days);
    }

    @Override
    public EmotionStatDTO getEmotionStat(Long attractionId, Integer days) {
        return experienceAnalysisMapper.getEmotionStat(attractionId, days, days * 2);
    }

    @Override
    public List<FocusStatDTO> getFocusStat(Long attractionId, Integer days) {
        return experienceAnalysisMapper.getFocusStat(attractionId, days);
    }

    @Override
    public List<FocusStatDTO> getWorstFocus(Long attractionId, Integer days) {
        return experienceAnalysisMapper.getWorstFocus(attractionId, days);
    }

}
