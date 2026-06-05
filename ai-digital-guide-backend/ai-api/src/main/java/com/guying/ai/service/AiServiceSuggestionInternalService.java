package com.guying.ai.service;

import com.guying.ai.dto.SuggestionDTO;

public interface AiServiceSuggestionInternalService {
    SuggestionDTO getSuggestion(Long attractionId, Integer type);
}
