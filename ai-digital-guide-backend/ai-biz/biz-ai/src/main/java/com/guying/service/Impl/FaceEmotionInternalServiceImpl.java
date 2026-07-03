package com.guying.service.Impl;

import com.guying.ai.dto.FaceEmotionTrendDTO;
import com.guying.ai.service.FaceEmotionInternalService;
import com.guying.mapper.FaceEmotionRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * FaceEmotionInternalService 实现：转发到 FaceEmotionRecordMapper。
 * 供 biz-admin 消费聚合数据，参照 AIExperienceAnalysisInternalServiceImpl。
 */
@Service
@Slf4j
public class FaceEmotionInternalServiceImpl implements FaceEmotionInternalService {

    @Autowired
    private FaceEmotionRecordMapper faceEmotionRecordMapper;

    @Override
    public List<FaceEmotionTrendDTO> getExpressionTrend(Long attractionId, Integer days) {
        return faceEmotionRecordMapper.getExpressionTrend(attractionId, days);
    }
}
