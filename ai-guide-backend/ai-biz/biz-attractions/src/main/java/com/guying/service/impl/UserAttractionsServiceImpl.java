package com.guying.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.guying.common.result.ScrollResult;
import com.guying.converter.UserAttractionsConverter;
import com.guying.mapper.AttractionsMapper;
import com.guying.pojo.dto.AttractionsPageQueryDTO;
import com.guying.pojo.entity.Attraction;
import com.guying.pojo.vo.AttractionDetailVO;
import com.guying.pojo.vo.AttractionsAroundPageQueryVO;
import com.guying.service.UserAttractionsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class UserAttractionsServiceImpl extends ServiceImpl<AttractionsMapper, Attraction> implements UserAttractionsService {
    @Autowired
    private AttractionsMapper attractionsMapper;
    @Autowired
    private UserAttractionsConverter userAttractionsConverter;


    @Override
    public ScrollResult<AttractionsAroundPageQueryVO> getAttractionsAround(AttractionsPageQueryDTO attractionsPageQueryDTO) {
        LambdaQueryWrapper<Attraction> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StringUtils.hasText(attractionsPageQueryDTO.getKeyWord()), Attraction::getAttractionName, attractionsPageQueryDTO.getKeyWord())
                .lt(StringUtils.hasText(attractionsPageQueryDTO.getLastId()), Attraction::getId, attractionsPageQueryDTO.getLastId())
                .orderByDesc(Attraction::getId)
                .last("limit " + (attractionsPageQueryDTO.getPageSize()+1));

        List<Attraction> attractions = attractionsMapper.selectList(queryWrapper);
        boolean hasMore = attractions.size() > attractionsPageQueryDTO.getPageSize();
        if (hasMore) {
            attractions.removeLast();
        }
        Long nextLastId = attractions.isEmpty() ? null : attractions.getLast().getId();

        List<AttractionsAroundPageQueryVO> attractionsAroundPageQueryVOList =
                userAttractionsConverter.toAttractionsAroundPageQueryVOList(attractions);

        return new ScrollResult<>(attractionsAroundPageQueryVOList, nextLastId, hasMore);
    }


}
