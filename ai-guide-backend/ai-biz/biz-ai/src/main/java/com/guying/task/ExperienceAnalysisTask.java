package com.guying.task;

import com.guying.attractions.service.AdminAttractionsInternalService;
import com.guying.common.enums.SuggestionTypeEnum;
import com.guying.pojo.dto.Suggestion;
import com.guying.service.AiServiceSuggestionService;
import com.guying.service.ExperienceAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class ExperienceAnalysisTask {

    @Autowired
    private ExperienceAnalysisService experienceAnalysisService;

    @Autowired
    private AdminAttractionsInternalService adminAttractionsInternalService;

    @Autowired
    private AiServiceSuggestionService aiServiceSuggestionService;

    @Scheduled(cron = "0 0 1 * * MON")
    public void generateWeeklySuggestion() {
        List<Long> attractionIdList = adminAttractionsInternalService.getAttractionIdList();
        log.info("attractionIdList:{}", attractionIdList);
        for( Long attractionId : attractionIdList){
            Suggestion suggestion = experienceAnalysisService.generateSuggestion(attractionId, 7);
            aiServiceSuggestionService.addSuggestion(suggestion, attractionId, SuggestionTypeEnum.WEEKLY);
        }
    }

    // 每月1号凌晨1点生成近30天建议
    @Scheduled(cron = "0 0 1 1 * *")
    public void generateMonthlySuggestion() {
        List<Long> attractionIdList = adminAttractionsInternalService.getAttractionIdList();
        for( Long attractionId : attractionIdList){
            Suggestion suggestion = experienceAnalysisService.generateSuggestion(attractionId, 30);
            aiServiceSuggestionService.addSuggestion(suggestion, attractionId, SuggestionTypeEnum.MONTHLY);


        }
    }
}
