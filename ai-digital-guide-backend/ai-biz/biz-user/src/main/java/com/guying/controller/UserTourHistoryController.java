package com.guying.controller;

import com.guying.common.result.Result;
import com.guying.common.result.ScrollResult;
import com.guying.pojo.dto.TourEvaluateDTO;
import com.guying.pojo.dto.UserTourHistoryPageQueryDTO;
import com.guying.service.UserTourHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@Valid
@Tag(name = "用户旅游记录管理")
@RequestMapping("/v1/users/tourHistory")
public class UserTourHistoryController {
    @Autowired
    private UserTourHistoryService userTourHistoryService;


    @Operation(summary = "获取旅游历史")
    @GetMapping
    public Result<ScrollResult> getTourHistory(@Valid UserTourHistoryPageQueryDTO userTourHistoryPageQueryDTO) {
        log.info("userTourHistoryPageQueryDTO:{}", userTourHistoryPageQueryDTO);
        ScrollResult scrollResult = userTourHistoryService.getTourHistory(userTourHistoryPageQueryDTO);
        return Result.success(scrollResult);
    }

    @Operation(summary = "删除旅游历史")
    @DeleteMapping ("/delete/{id}")
    public Result<?> deleteTourHistory(@PathVariable Long id) {
        log.info("id:{}", id);
        userTourHistoryService.deleteTourHistory(id);
        return Result.success();
    }


    @Operation(summary = "评价旅游历史")
    @PostMapping("/evaluate")
    public Result<?> evaluate(@RequestBody TourEvaluateDTO tourEvaluateDTO) {
        userTourHistoryService.evaluateTourHistory(tourEvaluateDTO);
        return Result.success();
    }
}
