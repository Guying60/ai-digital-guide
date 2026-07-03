package com.guying.controller;

import com.guying.common.result.Result;
import com.guying.pojo.vo.EmotionFocusCardVO;
import com.guying.pojo.vo.EmotionOverviewVO;
import com.guying.pojo.vo.EmotionTrendVO;
import com.guying.pojo.vo.SuggestionVO;
import com.guying.service.AnalysisService;
import com.guying.service.EmotionOverviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admins/analysis")
@Slf4j
@Tag(name = "游客分析")
public class AnalysisController {
    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private EmotionOverviewService emotionOverviewService;

    /**
     * 获取情感趋势
     * @param attractionId
     * @param days
     * @return
     */
    @Operation(summary = "获取情感趋势")
    @GetMapping("/emtion-trend/{attractionId}")
    public Result<EmotionTrendVO> getEmotionTrend(@PathVariable Long attractionId,
                                                  @RequestParam(defaultValue = "7") Integer days) {
        EmotionTrendVO emotionTrendVO = analysisService.getEmotionTrend(attractionId, days);
        return Result.success(emotionTrendVO);

    }

    /**
     * 获取情感和关注点
     * @return
     */
    @Operation(summary = "获取情感和关注点")
    @GetMapping("/emotion-focus-card/{attractionId}")
    public Result<EmotionFocusCardVO> getEmotionFocusCard(@PathVariable Long attractionId,
                                                          @RequestParam(defaultValue = "7") Integer days) {
        EmotionFocusCardVO emotionFocusCardVO = analysisService.getEmotionFocusCard(attractionId, days);
        return Result.success(emotionFocusCardVO);
    }


    @Operation(summary = "获取AI服务建议")
    @GetMapping("/ai-service-suggestion/{attractionId}")
    public Result<SuggestionVO> getAiServiceSuggestion(@PathVariable Long attractionId,
                                                       @RequestParam(defaultValue = "0") Integer type) {
        SuggestionVO suggestionVO = analysisService.getAiServiceSuggestion(attractionId, type);
        return Result.success(suggestionVO);
    }

    /**
     * 获取情感概览（文本情感 + 面部表情 + 关注点 合并）
     */
    @Operation(summary = "获取情感概览（文本+面部合并）")
    @GetMapping("/emotion-overview/{attractionId}")
    public Result<EmotionOverviewVO> getEmotionOverview(@PathVariable Long attractionId,
                                                         @RequestParam(defaultValue = "7") Integer days) {
        EmotionOverviewVO vo = emotionOverviewService.getOverview(attractionId, days);
        return Result.success(vo);
    }

}
