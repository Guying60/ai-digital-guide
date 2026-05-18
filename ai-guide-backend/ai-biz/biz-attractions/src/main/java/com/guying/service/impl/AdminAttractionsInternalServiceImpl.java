package com.guying.service.impl;

import com.guying.attractions.dto.AttractionDocumentDTO;
import com.guying.attractions.service.AdminAttractionsInternalService;
import com.guying.converter.AdminAttractionsConverter;
import com.guying.mapper.AttractionDocumentMapper;
import com.guying.mapper.AttractionsMapper;
import com.guying.pojo.entity.Attraction;
import com.guying.pojo.entity.AttractionDocument;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminAttractionsInternalServiceImpl implements AdminAttractionsInternalService {
    @Autowired
    private AdminAttractionsConverter adminAttractionsConverter;
    @Autowired
    private AttractionDocumentMapper attractionDocumentMapper;
    @Autowired
    private AttractionsMapper attractionsMapper;



    /**
     * 保存景点文档
     * @param attractionDocumentDTO
     */
    @Override
    public void saveDocumentToMySql(AttractionDocumentDTO attractionDocumentDTO) {
        AttractionDocument attractionDocument = adminAttractionsConverter.toAttractionDocument(attractionDocumentDTO);
        attractionDocumentMapper.insert(attractionDocument);
    }

    /**
     * 获取景点id列表
     * @return
     */
    @Override
    public List<Long> getAttractionIdList() {
        LambdaQueryWrapper<Attraction> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(Attraction::getId);

        List<Object> ids = attractionsMapper.selectObjs(queryWrapper);

        // 转换类型并返回 (安全转型，避免不同数据库驱动返回 Integer 或 BigInteger 导致强转 Long 报错)
        return ids.stream()
                .map(id -> Long.valueOf(id.toString()))
                .collect(Collectors.toList());
    }

}
