package com.guying.controller;

import com.guying.common.result.Result;
import com.guying.pojo.vo.ChatTrendVO;
import com.guying.pojo.vo.HotFaqChartVO;
import com.guying.pojo.vo.SatisfactionTrendVO;
import com.guying.service.StatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/admins/stat")
@Slf4j
@Tag(name = "统计管理")
public class StatController {

    @Autowired
    private StatService statService;


    @Operation(summary = "获取聊天趋势")
    @GetMapping("/chat-trend/{attractionId}")
    public Result<ChatTrendVO> chatTrend(@PathVariable Long attractionId,Integer days){
        ChatTrendVO chatTrend = statService.getChatTrend(attractionId, days);
        return Result.success(chatTrend);
    }

    @Operation(summary = "获取FAQ")
    @GetMapping("/faq/{attractionId}")
    public Result<List<HotFaqChartVO>> getFaq(@PathVariable Long attractionId, Integer days){
        List<HotFaqChartVO> hotFaqChartVO = statService.getFaq(attractionId, days);
        return Result.success(hotFaqChartVO);
    }

    @Operation(summary = "获取用户满意度趋势")
    @GetMapping("/satisfaction-trend/{attractionId}")
    public Result<SatisfactionTrendVO> satisfactionTrend(@PathVariable Long attractionId, Integer days){
        SatisfactionTrendVO satisfactionTrendVO = statService.getSatisfactionTrend(attractionId, days);
        return Result.success(satisfactionTrendVO);
    }

}
