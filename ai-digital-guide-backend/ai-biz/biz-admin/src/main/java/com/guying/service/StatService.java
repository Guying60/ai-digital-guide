package com.guying.service;

import com.guying.pojo.entity.FaqDailyStats;
import com.guying.pojo.vo.ChatTrendVO;
import com.guying.pojo.vo.HotFaqChartVO;
import com.guying.pojo.vo.SatisfactionTrendVO;

import java.util.List;

public interface StatService {
    ChatTrendVO getChatTrend(Long attractionId, Integer days);

    List<HotFaqChartVO> getFaq(Long attractionId, Integer days);

    SatisfactionTrendVO getSatisfactionTrend(Long attractionId, Integer days);

    void saveFaqDailyStats(List<FaqDailyStats> dbList);
}
