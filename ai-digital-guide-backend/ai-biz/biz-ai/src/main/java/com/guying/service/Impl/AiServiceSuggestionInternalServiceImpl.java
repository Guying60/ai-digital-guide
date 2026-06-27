package com.guying.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guying.ai.dto.SuggestionDTO;
import com.guying.ai.service.AiServiceSuggestionInternalService;
import com.guying.mapper.AiServiceSuggestionMapper;
import com.guying.pojo.entity.AiServiceSuggestion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AiServiceSuggestionInternalServiceImpl implements AiServiceSuggestionInternalService {

    @Autowired
    private AiServiceSuggestionMapper aiServiceSuggestionMapper;

    /**
     * 根据 attractionId 和 type 获取建议
     * @param attractionId
     * @param type
     * @return
     */
    @Override
    public SuggestionDTO getSuggestion(Long attractionId, Integer type) {
        SuggestionDTO suggestionDTO = new SuggestionDTO();
        // 每周/每月都会 insert 新建议，同一 (attractionId,type) 会有多行；
        // 必须按时间倒序取最新一条并 LIMIT 1，否则 selectOne 命中多行会抛 TooManyResultsException。
        LambdaQueryWrapper<AiServiceSuggestion> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AiServiceSuggestion::getAttractionId, attractionId)
                    .eq(AiServiceSuggestion::getType, type)
                    .orderByDesc(AiServiceSuggestion::getCreateTime)
                    .last("LIMIT 1");
        AiServiceSuggestion aiServiceSuggestion = aiServiceSuggestionMapper.selectOne(queryWrapper);
        if (aiServiceSuggestion != null) {
            suggestionDTO.setSummary(aiServiceSuggestion.getSummary());
            suggestionDTO.setSuggestion(aiServiceSuggestion.getSuggestion());
        }
        return suggestionDTO;
    }
}
