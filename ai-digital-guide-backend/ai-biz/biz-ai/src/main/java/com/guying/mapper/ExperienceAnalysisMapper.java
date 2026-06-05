package com.guying.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guying.ai.dto.EmotionStatDTO;
import com.guying.ai.dto.EmotionTrendDTO;
import com.guying.ai.dto.FocusStatDTO;
import com.guying.pojo.dto.NegativeSampleDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ExperienceAnalysisMapper extends BaseMapper<com.guying.pojo.entity.AiExperienceAnalysis> {


    List<EmotionTrendDTO> getEmotionTrend(Long attractionId, Integer days);

    EmotionStatDTO getEmotionStat(Long attractionId, Integer days,Integer doubleDays);

    List<FocusStatDTO> getFocusStat(Long attractionId, Integer days);

    List<FocusStatDTO> getWorstFocus(Long attractionId, Integer days);

    List<NegativeSampleDTO> getNegativeSample(Long attractionId, int days);
}
