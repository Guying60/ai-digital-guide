package com.guying.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guying.pojo.entity.FaqDailyStats;
import com.guying.pojo.vo.HotFaqChartVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FaqDailyStatsMapper extends BaseMapper<FaqDailyStats> {

    List<HotFaqChartVO> getHotFaqChartData(@Param("attractionId") Long attractionId, @Param("days") Integer days);
}
