package com.guying.service.Impl;

import com.guying.common.enums.SuggestionTypeEnum;
import com.guying.mapper.AiServiceSuggestionMapper;
import com.guying.pojo.dto.Suggestion;
import com.guying.pojo.entity.AiServiceSuggestion;
import com.guying.service.AiServiceSuggestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AiServiceSuggestionServiceImpl implements AiServiceSuggestionService {
    @Autowired
    private AiServiceSuggestionMapper aiServiceSuggestionMapper;

    @Override
    public void addSuggestion(Suggestion suggestion, Long attractionId, SuggestionTypeEnum suggestionTypeEnum) {
        if (suggestion == null) {
            return;
        }
        AiServiceSuggestion aiServiceSuggestion = new AiServiceSuggestion();
        aiServiceSuggestion.setAttractionId(attractionId);
        aiServiceSuggestion.setType(suggestionTypeEnum.getCode());
        aiServiceSuggestion.setSummary(suggestion.getSummary());
        aiServiceSuggestion.setSuggestion(suggestion.getSuggestion());
        aiServiceSuggestionMapper.insert(aiServiceSuggestion);
    }
}
