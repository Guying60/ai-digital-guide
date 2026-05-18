package com.guying.service;

import com.guying.common.enums.SuggestionTypeEnum;
import com.guying.pojo.dto.Suggestion;

public interface AiServiceSuggestionService {
    void addSuggestion(Suggestion suggestion, Long attractionId, SuggestionTypeEnum suggestionTypeEnum);
}
