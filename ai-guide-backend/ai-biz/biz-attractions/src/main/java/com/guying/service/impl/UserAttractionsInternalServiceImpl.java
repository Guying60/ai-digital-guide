package com.guying.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guying.attractions.dto.AttractionDTO;
import com.guying.attractions.service.UserAttractionsInternalService;
import com.guying.mapper.AttractionFaqMapper;
import com.guying.pojo.entity.Attraction;
import com.guying.pojo.entity.AttractionFaq;
import com.guying.service.UserAttractionsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UserAttractionsInternalServiceImpl implements UserAttractionsInternalService {
    @Autowired
    private UserAttractionsService attractionService;
    @Autowired
    private AttractionFaqMapper attractionFaqMapper;

    /**
     * 根据id获取景点
     * @param attractionId
     * @return
     */
    @Override
    public AttractionDTO getAttraction(Long attractionId) {
        Attraction attraction = attractionService.getById(attractionId);
        if (attraction == null) {
            return null;
        }
        AttractionDTO attractionDTO = new AttractionDTO();
        attractionDTO.setAttractionName(attraction.getAttractionName());
        attractionDTO.setCoverUrl(attraction.getCoverUrl());
        attractionDTO.setType(attraction.getType());
        return attractionDTO;
    }

    /**
     * 根据docId获取绝对事实
     * @param faqId
     * @return
     */
    @Override
    public String getAbsoluteFactByQuestionId(String faqId) {
        log.info("getAbsoluteFactByQuestionId:{}", faqId);
        AttractionFaq attractionFaq = attractionFaqMapper.selectById(Long.valueOf(faqId));
        return attractionFaq.getAnswer();
    }
}
