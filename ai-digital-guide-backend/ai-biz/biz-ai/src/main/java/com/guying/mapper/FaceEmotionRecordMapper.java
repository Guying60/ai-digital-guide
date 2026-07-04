package com.guying.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guying.ai.dto.FaceEmotionTrendDTO;
import com.guying.pojo.entity.FaceEmotionRecord;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FaceEmotionRecordMapper extends BaseMapper<FaceEmotionRecord> {

    /**
     * 按景点+天数聚合面部表情趋势（按日 + 按表情分组计数）。
     */
    List<FaceEmotionTrendDTO> getExpressionTrend(Long attractionId, Integer days);
}
