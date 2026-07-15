package com.guying.controller;

import com.guying.common.result.Result;
import com.guying.context.UserContext;
import com.guying.pojo.vo.RoutePlanVO;
import com.guying.service.RouteRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@Tag(name = "AI路线推荐接口")
@RequestMapping("/v1/users/route")
public class UserRouteController {

    @Autowired
    private RouteRecommendationService routeRecommendationService;

    @Operation(summary = "恢复当前激活的路线时间轴（刷新/重连用）")
    @GetMapping("/current")
    public Result<RoutePlanVO> current(@RequestParam Long attractionId,
                                       @RequestParam String conversationId) {
        Long userId = UserContext.getUserId();
        return Result.success(routeRecommendationService.getCurrentPlan(userId, attractionId, conversationId));
    }
}
