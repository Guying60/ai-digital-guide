package com.guying.controller;

import com.guying.common.enums.TaskStatusEnum;
import com.guying.common.result.Result;
import com.guying.pojo.dto.DigitalHumanCreateDTO;
import com.guying.pojo.dto.TestVideoRequestDTO;
import com.guying.pojo.vo.DigitalHumanVO;
import com.guying.service.AdminDigitalHumanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@Slf4j
@Tag(name = "管理员数字人管理")
@RequestMapping("/v1/admins/attractions/digital-human")
public class AdminDigitalHumanController {

    @Autowired
    private AdminDigitalHumanService adminDigitalHumanService;

    /**
     * 新增数字人
     * @param dto
     * @return
     */
    @Operation(summary = "新增数字人")
    @PostMapping
    public Result<DigitalHumanVO> addOrUpdate(@Valid @RequestBody DigitalHumanCreateDTO dto) {
        DigitalHumanVO vo = adminDigitalHumanService.addOrUpdate(dto);
        return Result.success(vo);
    }



    /**
     * 用于数据回显
     * @param attractionId
     * @return
     */
    @Operation(summary = "查询数字人详情")
    @GetMapping("/{attractionId}")
    public Result<DigitalHumanVO> getDetail(@PathVariable Long attractionId) {
        DigitalHumanVO vo = adminDigitalHumanService.getDetail(attractionId);
        return Result.success(vo);
    }

    /**
     * 删除数字人
     * @param id 数字人ID
     * @return
     */
    @Operation(summary = "删除数字人")
    @DeleteMapping("/{id}")
    public Result<Void> deleteDigitalHuman(@PathVariable Long id) {
        adminDigitalHumanService.deleteDigitalHuman(id);
        return Result.success();
    }

    /**
     * 轮询数字人视频预加载状态
     * @param digitalHumanId 数字人 ID
     * @return PROCESSING | SUCCESS | FAILED
     */
    @Operation(summary = "检查数字人预加载状态")
    @GetMapping("/preload-status/{digitalHumanId}")
    public Result<String> checkPreloadStatus(@PathVariable Long digitalHumanId) {
        log.info("检查数字人预加载状态: digitalHumanId={}", digitalHumanId);
        String status = adminDigitalHumanService.checkPreloadStatus(digitalHumanId);
        return Result.success(status);
    }

    /**
     * 触发测试视频生成
     * @param digitalHumanId 数字人 ID
     * @param dto 可选测试文本
     * @return
     */
    @Operation(summary = "生成测试视频")
    @PostMapping("/test-video/{digitalHumanId}")
    public Result<String> generateTestVideo(@PathVariable Long digitalHumanId, @RequestBody TestVideoRequestDTO dto) {
        log.info("生成测试视频: digitalHumanId={}, text={}", digitalHumanId, dto.getText());
        String msg = adminDigitalHumanService.generateTestVideo(digitalHumanId, dto.getText());
        return Result.success(msg);
    }

    /**
     * 轮询测试视频生成状态
     * @param digitalHumanId 数字人 ID
     * @return status + videoUrl(成功时)
     */
    @Operation(summary = "检查测试视频生成状态")
    @GetMapping("/test-video-status/{digitalHumanId}")
    public Result<Map<String, Object>> checkTestVideoStatus(@PathVariable Long digitalHumanId) {
        String status = adminDigitalHumanService.checkTestVideoStatus(digitalHumanId);
        Map<String, Object> result = new HashMap<>();
        result.put("status", status);
        if (TaskStatusEnum.SUCCESS.toString().equals(status)) {
            result.put("videoUrl", "/ai-project/v1/admins/attractions/digital-human/test-video-file/" + digitalHumanId);
        }
        return Result.success(result);
    }

    /**
     * 代理获取测试视频文件
     * @param digitalHumanId 数字人 ID
     * @return MP4 视频流
     */
    @Operation(summary = "获取测试视频文件")
    @GetMapping("/test-video-file/{digitalHumanId}")
    public ResponseEntity<Resource> proxyTestVideoFile(@PathVariable Long digitalHumanId) {
        Resource resource = adminDigitalHumanService.proxyTestVideo(digitalHumanId);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("video/mp4"))
                .body(resource);
    }

}
