package com.guying.controller;

import com.guying.common.result.Result;
import com.guying.pojo.dto.DigitalHumanCreateDTO;
import com.guying.pojo.dto.DigitalHumanUpdateDTO;
import com.guying.pojo.vo.DigitalHumanVO;
import com.guying.service.AdminDigitalHumanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public Result<DigitalHumanVO> add(@Valid @RequestBody DigitalHumanCreateDTO dto) {
        DigitalHumanVO vo = adminDigitalHumanService.add(dto);
        return Result.success(vo);
    }

    /**
     * 删除数字人
     * @param id
     * @return
     */
    @Operation(summary = "删除数字人")
    @DeleteMapping("/{id}")
    public Result<Void> deleteById(@PathVariable Long id) {
        adminDigitalHumanService.deleteById(id);
        return Result.success();
    }

    /**
     * 修改数字人
     * @param dto
     * @return
     */
    @Operation(summary = "修改数字人")
    @PutMapping
    public Result<DigitalHumanVO> update(@Valid @RequestBody DigitalHumanUpdateDTO dto) {
        DigitalHumanVO vo = adminDigitalHumanService.update(dto);
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

}
