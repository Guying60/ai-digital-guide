package com.guying.service;


import com.guying.pojo.dto.Suggestion;

public interface ExperienceAnalysisService {

    void analyze(String userMessage, Long userId, Long attractionId);


    Suggestion generateSuggestion(Long attractionId, int days);
}