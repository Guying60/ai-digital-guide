package com.guying.controller;

import com.guying.common.result.Result;
import com.guying.common.result.ScrollResult;
import com.guying.pojo.dto.AttractionsPageQueryDTO;
import com.guying.pojo.vo.AttractionDetailVO;
import com.guying.pojo.vo.AttractionsAroundPageQueryVO;
import com.guying.service.UserAttractionsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@Tag(name = "用户景点相关接口")
@RequestMapping("/v1/users/attractions")
public class UserAttractionsController {
    @Autowired
    private UserAttractionsService userAttractionsService;


    /**]
     * 拿到附近景点
     * @param attractionsPageQueryDTO
     * @return
     */
    @Operation(description = "拿到附近景点")
    @GetMapping
    public ScrollResult<AttractionsAroundPageQueryVO> getAttractionsAround(@Valid AttractionsPageQueryDTO attractionsPageQueryDTO) {
        return userAttractionsService.getAttractionsAround(attractionsPageQueryDTO);
    }


}
