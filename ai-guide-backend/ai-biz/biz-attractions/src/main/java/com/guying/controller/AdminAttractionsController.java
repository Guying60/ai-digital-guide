package com.guying.controller;

import com.guying.common.result.Result;
import com.guying.common.result.ScrollResult;
import com.guying.pojo.dto.AttractionBatchDeleteDTO;
import com.guying.pojo.dto.AttractionCreateDTO;
import com.guying.pojo.dto.AttractionListQueryDTO;
import com.guying.pojo.dto.AttractionUpdateDTO;
import com.guying.pojo.vo.AttractionAdditionVO;
import com.guying.pojo.vo.AttractionDetailVO;
import com.guying.pojo.vo.AttractionListVO;
import com.guying.pojo.vo.DocumentsQueryVO;
import com.guying.service.AdminAttractionsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Slf4j
@Tag(name = "管理员景点管理")
@RequestMapping("/v1/admins/attractions")
public class AdminAttractionsController {

    @Autowired
    private AdminAttractionsService adminAttractionsService;

    /**
     * 添加景点
     *
     * @param attractionCreateDTO 景点创建参数
     * @return Result
     */
    @Operation(summary = "添加景点")
    @PostMapping()
    public Result<AttractionAdditionVO> addAttraction(@RequestBody @Valid AttractionCreateDTO attractionCreateDTO) {
        log.info("attractionCreateDTO:{}", attractionCreateDTO);
        AttractionAdditionVO attractionAdditionVO = adminAttractionsService.addAttraction(attractionCreateDTO);
        return Result.success(attractionAdditionVO);
    }

    /**
     * 获取景点数据回显
     * @param attractionId 景点Id
     * @return Result
     */
    @Operation(summary = "获取景点数据回显")
    @GetMapping("/{attractionId}")
    public Result<AttractionDetailVO> getAttraction(@PathVariable Long attractionId) {
        AttractionDetailVO attractionDetailVO = adminAttractionsService.getAttractionDetail(attractionId);
        return Result.success(attractionDetailVO);
    }

    /**
     * 获取景点文档数据回显
     * @param attractionId 景点Id
     * @return Result
     */
    @Operation(summary = "获取景点文档数据回显")
    @GetMapping("/documents/{attractionId}")
    public Result<List<DocumentsQueryVO>> getDocument(@PathVariable Long attractionId) {
        List<DocumentsQueryVO> documentsQueryVOList = adminAttractionsService.getDocuments(attractionId);
        return Result.success(documentsQueryVOList);
    }

    /**
     * 删除景点文档
     * @param fileId 文件Id
     * @return Result
     */
    @Operation(summary = "删除景点文档")
    @DeleteMapping("/documents/{fileId}")
    public Result deleteDocument(@PathVariable Long fileId) {
        log.info("删除景点文档:fileId:{}", fileId);
        adminAttractionsService.deleteDocument(fileId);
        return Result.success();
    }

    /**
     * 检查景点文档状态
     * @param taskId 任务Id
     * @return Result
     */
    @Operation(summary = "检查景点文档状态")
    @GetMapping("/documents/status/{taskId}")
    public Result<String> checkDocumentStatus(@PathVariable String taskId) {
        log.info("检查景点文档:taskId:{}", taskId);
        String status = adminAttractionsService.checkDocumentStatus(taskId);
        return Result.success(status);
    }

    /**
     * 更新景点
     * @param attractionUpdateDTO 景点更新参数
     * @return Result
     */
    @Operation(summary = "更新景点")
    @PutMapping
    public Result<AttractionDetailVO> updateAttraction(@RequestBody @Valid AttractionUpdateDTO attractionUpdateDTO) {
        AttractionDetailVO attractionDetailVO = adminAttractionsService.updateAttraction(attractionUpdateDTO);
        return Result.success(attractionDetailVO);
    }

    @Operation(summary = "删除景点")
    @DeleteMapping("/{attractionId}")
    public Result deleteAttraction(@PathVariable Long attractionId) {
        adminAttractionsService.deleteAttraction(attractionId);
        return Result.success();
    }

    @Operation(summary = "批量删除景点")
    @DeleteMapping("/batch")
    public Result deleteAttractions(@RequestBody @Valid AttractionBatchDeleteDTO dto) {
        adminAttractionsService.deleteAttractions(dto.getIds());
        return Result.success();
    }

    @Operation(summary = "获取景点列表")
    @GetMapping()
    public Result<ScrollResult<AttractionListVO>> getAttractionList(AttractionListQueryDTO attractionListQueryDTO) {
        ScrollResult<AttractionListVO> attractionListVO = adminAttractionsService.getAttractionList(attractionListQueryDTO);
        return Result.success(attractionListVO);
    }


}
