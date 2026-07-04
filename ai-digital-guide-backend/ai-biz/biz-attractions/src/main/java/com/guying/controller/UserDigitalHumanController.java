package com.guying.controller;

import com.guying.common.result.Result;
import com.guying.pojo.vo.DigitalHumanVO;
import com.guying.service.AdminDigitalHumanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 游客侧数字人接口：仅用于数字人页待机循环播放管理员上传的原始视频。
 */
@RestController
@Slf4j
@Tag(name = "用户数字人相关接口")
@RequestMapping("/v1/users/attractions/digital-human")
public class UserDigitalHumanController {

    @Autowired
    private AdminDigitalHumanService adminDigitalHumanService;

    /**
     * 查询景点数字人原始视频地址（含 videoUrl），供数字人页 WS 连接后待机循环播放。
     * @param attractionId 景点ID
     * @return DigitalHumanVO；无数字人时 data 为 null（前端优雅降级，不展示待机视频）
     */
    @Operation(summary = "查询景点数字人原始视频")
    @GetMapping("/{attractionId}")
    public Result<DigitalHumanVO> getDigitalHuman(@PathVariable Long attractionId) {
        DigitalHumanVO vo = adminDigitalHumanService.getByAttractionId(attractionId);
        return Result.success(vo);
    }
}
