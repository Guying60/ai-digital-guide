package com.guying.controller;

import com.guying.common.result.Result;
import com.guying.pojo.dto.UserGuidePreferenceDTO;
import com.guying.pojo.vo.UserGuidePreferenceVO;
import com.guying.service.UserGuidePreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@Tag(name = "用户导览偏好")
@RequestMapping("/v1/users/guide-preference")
public class UserGuidePreferenceController {

    @Autowired
    private UserGuidePreferenceService userGuidePreferenceService;

    @Operation(summary = "保存或更新导览偏好")
    @PutMapping
    public Result savePreference(@RequestBody @Valid UserGuidePreferenceDTO dto) {
        log.info("保存导览偏好: {}", dto);
        userGuidePreferenceService.savePreference(dto);
        return Result.success();
    }

    @Operation(summary = "查询当前用户导览偏好")
    @GetMapping
    public Result<UserGuidePreferenceVO> getPreference() {
        UserGuidePreferenceVO vo = userGuidePreferenceService.getPreference();
        return Result.success(vo);
    }
}
