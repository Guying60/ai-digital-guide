package com.guying.service.impl;

import com.guying.mapper.FaqDailyStatsMapper;
import com.guying.pojo.entity.FaqDailyStats;
import com.guying.pojo.vo.ChatTrendVO;
import com.guying.pojo.vo.HotFaqChartVO;
import com.guying.pojo.vo.SatisfactionTrendVO;
import com.guying.service.StatService;
import com.guying.user.dto.UserChatTrendDTO;
import com.guying.user.dto.UserSatisfactionTrendDTO;
import com.guying.attractions.service.ReviewInternalService;
import com.guying.user.service.UserTourHistoryInternalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StatServiceImpl implements StatService {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private UserTourHistoryInternalService userTourHistoryInternalService;
    @Autowired
    private ReviewInternalService reviewInternalService;
    @Autowired
    private FaqDailyStatsMapper faqDailyStatsMapper;


    @Override
    public ChatTrendVO getChatTrend(Long attractionId, Integer days) {
        UserChatTrendDTO dto = userTourHistoryInternalService.getUserChatTrend(attractionId, days);

        ChatTrendVO vo = new ChatTrendVO();
        ChatTrendVO.Summary summary = new ChatTrendVO.Summary();
        summary.setTotalChats(dto.getTotalCount() == null ? 0 : dto.getTotalCount());
        vo.setSummary(summary);

        // 原始数据 时间点 -> 数量
        Map<String, Integer> countMap = new HashMap<>();
        if (dto.getDailyList() != null) {
            for (UserChatTrendDTO.DailyItem item : dto.getDailyList()) {
                countMap.put(item.getDate(), item.getCount());
            }
        }

        // 生成完整时间轴并补零，防止前端折线断裂
        List<ChatTrendVO.Trend> trendList = new ArrayList<>();
        for (String t : buildChatAxis(days)) {
            ChatTrendVO.Trend trend = new ChatTrendVO.Trend();
            trend.setTime(t);
            trend.setCount(countMap.getOrDefault(t, 0));
            trendList.add(trend);
        }
        vo.setTrendList(trendList);
        return vo;
    }

    /**
     * 聊天趋势横轴：days==1 按 24 小时；否则按近 N 天（含今天）。
     * 格式需与 SQL 的 DATE_FORMAT 输出一致（小时 'HH:00'，日期 'yyyy-MM-dd'）。
     */
    private List<String> buildChatAxis(Integer days) {
        List<String> axis = new ArrayList<>();
        if (days != null && days == 1) {
            for (int h = 0; h < 24; h++) {
                axis.add(String.format("%02d:00", h));
            }
        } else {
            int n = (days == null || days <= 0) ? 7 : days;
            for (int i = n - 1; i >= 0; i--) {
                axis.add(LocalDate.now().minusDays(i).format(DAY_FMT));
            }
        }
        return axis;
    }

    @Override
    public List<HotFaqChartVO> getFaq(Long attractionId, Integer days) {
        return faqDailyStatsMapper.getHotFaqChartData(attractionId, days);
    }

    @Override
    public SatisfactionTrendVO getSatisfactionTrend(Long attractionId, Integer days) {
        UserSatisfactionTrendDTO dto = reviewInternalService.getSatisfactionTrend(attractionId, days);
        return convertToSatisfactionTrendVO(dto, days);
    }

    private SatisfactionTrendVO convertToSatisfactionTrendVO(UserSatisfactionTrendDTO dto, Integer days) {
        // 原始数据 date -> item
        Map<String, UserSatisfactionTrendDTO.SatisfactionItem> itemMap = new HashMap<>();
        if (dto.getItemList() != null) {
            for (UserSatisfactionTrendDTO.SatisfactionItem item : dto.getItemList()) {
                itemMap.put(item.getDate(), item);
            }
        }

        List<String> dates = new ArrayList<>();
        List<Double> avgScores = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();

        // 近 N 天（含今天）补零，保证横轴连续
        int n = (days == null || days <= 0) ? 7 : days;
        for (int i = n - 1; i >= 0; i--) {
            String date = LocalDate.now().minusDays(i).format(DAY_FMT);
            UserSatisfactionTrendDTO.SatisfactionItem item = itemMap.get(date);
            dates.add(date);
            avgScores.add(item != null && item.getAvgScore() != null ? item.getAvgScore() : 0.0);
            counts.add(item != null && item.getCount() != null ? item.getCount() : 0);
        }

        SatisfactionTrendVO vo = new SatisfactionTrendVO();
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
