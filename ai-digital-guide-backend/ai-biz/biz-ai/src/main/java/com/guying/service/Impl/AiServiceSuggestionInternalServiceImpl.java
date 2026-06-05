package com.guying.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guying.ai.dto.SuggestionDTO;
import com.guying.ai.service.AiServiceSuggestionInternalService;
import com.guying.mapper.AiServiceSuggestionMapper;
import com.guying.pojo.entity.AiServiceSuggestion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AiServiceSuggestionInternalServiceImpl implements AiServiceSuggestionInternalService {

    @Autowired
    private AiServiceSuggestionMapper aiServiceSuggestionMapper;

    /**
     * 根据 attractionId 和 type 获取建议
     * @param attractionId
     * @param type
     * @return
     */
    @Override
    public SuggestionDTO getSuggestion(Long attractionId, Integer type) {
        SuggestionDTO suggestionDTO = new SuggestionDTO();
        LambdaQueryWrapper<AiServiceSuggestion> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AiServiceSuggestion::getAttractionId, attractionId)
                    .eq(AiServiceSuggestion::getType, type);
        AiServiceSuggestion aiServiceSuggestion = aiServiceSuggestionMapper.selectOne(queryWrapper);
        if (aiServiceSuggestion != null) {
            suggestionDTO.setSummary(aiServiceSuggestion.getSummary());
            suggestionDTO.setSuggestion(aiServiceSuggestion.getSuggestion());
        }
        return suggestionDTO;
    }
}
