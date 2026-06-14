package com.guying.controller;

import com.guying.common.result.Result;
import com.guying.common.result.ScrollResult;
import com.guying.context.UserContext;
import com.guying.pojo.dto.UserReviewPageQueryDTO;
import com.guying.pojo.dto.UserReviewSubmitDTO;
import com.guying.pojo.vo.UserReviewVO;
import com.guying.service.UserReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@Tag(name = "用户评价接口")
@RequestMapping("/v1/users/reviews")
public class UserReviewController {

    @Autowired
    private UserReviewService userReviewService;

    @Operation(summary = "获取我的评价列表（游标分页）")
    @GetMapping
    public Result<ScrollResult<UserReviewVO>> getMyReviews(UserReviewPageQueryDTO dto) {
        Long userId = UserContext.getUserId();
        ScrollResult<UserReviewVO> result = userReviewService.getMyReviews(dto, userId);
        return Result.success(result);
    }

    @Operation(summary = "提交评价")
    @PostMapping("/submit")
    public Result submitReview(@RequestBody @Valid UserReviewSubmitDTO dto) {
        Long userId = UserContext.getUserId();
        userReviewService.submitReview(dto, userId);
        return Result.success();
    }

    @Operation(summary = "删除评价")
    @DeleteMapping("/{reviewId}")
    public Result deleteReview(@PathVariable Long reviewId) {
        Long userId = UserContext.getUserId();
        userReviewService.deleteReview(reviewId, userId);
        return Result.success();
    }
}
