package com.guying.controller;

import com.guying.common.result.Result;
import com.guying.pojo.vo.FaceEmotionTrendVO;
import com.guying.service.FaceEmotionAnalysisService;
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
@Tag(name = "游客表情分析")
public class FaceEmotionAnalysisController {

    @Autowired
    private FaceEmotionAnalysisService faceEmotionAnalysisService;

    /**
     * 获取面部表情趋势（按日 + 7 个表情维度）。
     *
     * @param attractionId 景点 ID
     * @param days         时间范围，默认 7
     */
    @Operation(summary = "获取面部表情趋势")
    @GetMapping("/face-emotion-trend/{attractionId}")
    public Result<FaceEmotionTrendVO> getExpressionTrend(@PathVariable Long attractionId,
                                                         @RequestParam(defaultValue = "7") Integer days) {
        FaceEmotionTrendVO vo = faceEmotionAnalysisService.getExpressionTrend(attractionId, days);
        return Result.success(vo);
    }
}
