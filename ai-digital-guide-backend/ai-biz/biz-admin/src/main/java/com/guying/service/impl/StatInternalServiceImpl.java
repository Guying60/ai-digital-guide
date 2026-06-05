package com.guying.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guying.admin.service.StatInternalService;
import com.guying.mapper.FaqDailyStatsMapper;
import com.guying.pojo.entity.FaqDailyStats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class StatInternalServiceImpl implements StatInternalService {
    @Autowired
    private FaqDailyStatsMapper faqDailyStatsMapper;


    @Override
    public void deleteFaqDailyStats(Long attractionId) {
        LambdaQueryWrapper<FaqDailyStats> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FaqDailyStats::getAttractionId, attractionId);
        faqDailyStatsMapper.delete(queryWrapper);
    }
}
