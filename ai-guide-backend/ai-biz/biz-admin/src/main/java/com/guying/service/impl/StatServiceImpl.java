package com.guying.service.impl;

import com.guying.converter.StatConverter;
import com.guying.mapper.FaqDailyStatsMapper;
import com.guying.pojo.entity.FaqDailyStats;
import com.guying.pojo.vo.ChatTrendVO;
import com.guying.pojo.vo.HotFaqChartVO;
import com.guying.pojo.vo.SatisfactionTrendVO;
import com.guying.service.StatService;
import com.guying.user.dto.UserChatTrendDTO;
import com.guying.user.dto.UserSatisfactionTrendDTO;
import com.guying.user.service.UserTourHistoryInternalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class StatServiceImpl implements StatService {
    @Autowired
    private UserTourHistoryInternalService userTourHistoryInternalService;
    @Autowired
    private StatConverter statConverter;
    @Autowired
    private FaqDailyStatsMapper faqDailyStatsMapper;



    @Override
    public ChatTrendVO getChatTrend(Long attractionId, Integer days) {
        UserChatTrendDTO userChatTrendDTO = userTourHistoryInternalService.getUserChatTrend(attractionId, days);

        return statConverter.convertToChatTrendVO(userChatTrendDTO);
    }

    @Override
    public List<HotFaqChartVO> getFaq(Long attractionId, Integer days) {
        return faqDailyStatsMapper.getHotFaqChartData(attractionId, days);
    }

    @Override
    public SatisfactionTrendVO getSatisfactionTrend(Long attractionId, Integer days) {
        UserSatisfactionTrendDTO dto = userTourHistoryInternalService.getUserSatisfactionTrend(attractionId, days);
        return convertToSatisfactionTrendVO(dto);
    }

    private SatisfactionTrendVO convertToSatisfactionTrendVO(UserSatisfactionTrendDTO dto) {
        SatisfactionTrendVO vo = new SatisfactionTrendVO();
        List<String> dates = new ArrayList<>();
        List<Double> avgScores = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();

        if (dto.getItemList() != null) {
            for (UserSatisfactionTrendDTO.SatisfactionItem item : dto.getItemList()) {
                dates.add(item.getDate());
                avgScores.add(item.getAvgScore());
                counts.add(item.getCount());
            }
        }

        vo.setDates(dates);
        vo.setAvgScores(avgScores);
        vo.setCounts(counts);
        vo.setTotalAvgScore(dto.getTotalAvgScore());
        return vo;
    }

    /**
     * 保存faq每日统计
     * @param dbList
     */
    @Override
    public void saveFaqDailyStats(List<FaqDailyStats> dbList) {
        faqDailyStatsMapper.insert(dbList);
    }
}