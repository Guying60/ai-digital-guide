package com.guying.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.guying.common.result.ScrollResult;
import com.guying.pojo.dto.AttractionsPageQueryDTO;
import com.guying.pojo.entity.Attraction;
import com.guying.pojo.vo.AttractionDetailVO;
import com.guying.pojo.vo.AttractionsAroundPageQueryVO;
import jakarta.validation.Valid;

public interface UserAttractionsService extends IService<Attraction> {
    ScrollResult<AttractionsAroundPageQueryVO> getAttractionsAround(@Valid AttractionsPageQueryDTO attractionsPageQueryDTO);

}
