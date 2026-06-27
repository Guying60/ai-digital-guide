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
        generateSuggestions(7, SuggestionTypeEnum.WEEKLY);
    }

    // 每月1号凌晨1:30生成近30天建议（错开周一 1:00，避免逢“1号又周一”双跑）
    @Scheduled(cron = "0 30 1 1 * *")
    public void generateMonthlySuggestion() {
        generateSuggestions(30, SuggestionTypeEnum.MONTHLY);
    }

    /**
     * 逐景点生成运营建议。单个景点失败（如 LLM 调用异常）只跳过该景点，
     * 不影响其它景点；无数据景点返回“暂无数据”则跳过入库，避免脏数据。
     */
    private void generateSuggestions(int days, SuggestionTypeEnum type) {
        List<Long> attractionIdList = adminAttractionsInternalService.getAttractionIdList();
        log.info("开始生成{}建议, 景点数量:{}", type.getDesc(), attractionIdList == null ? 0 : attractionIdList.size());
        if (attractionIdList == null) {
            return;
        }
        for (Long attractionId : attractionIdList) {
            try {
                Suggestion suggestion = experienceAnalysisService.generateSuggestion(attractionId, days);
                if (suggestion == null || "暂无数据".equals(suggestion.getSummary())) {
                    log.info("景点{}无足够数据，跳过{}建议入库", attractionId, type.getDesc());
                    continue;
                }
                aiServiceSuggestionService.addSuggestion(suggestion, attractionId, type);
            } catch (Exception e) {
                log.error("景点{}生成{}建议失败，跳过", attractionId, type.getDesc(), e);
            }
        }
    }
}
