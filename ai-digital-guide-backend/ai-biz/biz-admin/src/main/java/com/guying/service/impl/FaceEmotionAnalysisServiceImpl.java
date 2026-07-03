package com.guying.service.impl;

import com.guying.ai.dto.FaceEmotionTrendDTO;
import com.guying.ai.service.FaceEmotionInternalService;
import com.guying.common.enums.ExpressionEnum;
import com.guying.pojo.vo.FaceEmotionTrendVO;
import com.guying.service.FaceEmotionAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 面部表情趋势分析服务实现（管理端）。
 * 聚合逻辑完全复刻 AnalysisServiceImpl.getEmotionTrend，维度从 3 类扩为 7 类表情。
 * 无数据日期 count 为 0、rate 为 0.0，日期序列不断档。
 */
@Service
public class FaceEmotionAnalysisServiceImpl implements FaceEmotionAnalysisService {

    @Autowired
    private FaceEmotionInternalService faceEmotionInternalService;

    @Override
    public FaceEmotionTrendVO getExpressionTrend(Long attractionId, Integer days) {
        List<FaceEmotionTrendDTO> rawList = faceEmotionInternalService.getExpressionTrend(attractionId, days);

        // 生成完整日期序列，防止某天无数据导致断点
        List<String> dates = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            dates.add(LocalDate.now().minusDays(i)
                    .format(DateTimeFormatter.ofPattern("MM-dd")));
        }

        // 按日期分组：date -> (expression -> count)
        Map<String, Map<Integer, Integer>> byDate = new LinkedHashMap<>();
        for (String date : dates) {
            byDate.put(date, new HashMap<>());
        }
        for (FaceEmotionTrendDTO dto : rawList) {
            byDate.computeIfAbsent(dto.getDate(), k -> new HashMap<>())
                    .put(dto.getExpression(), dto.getCount());
        }

        // 每个表情一对 count[] / rate[]，顺序与 Enum 一致
        Map<Integer, List<Integer>> countsByExpr = new LinkedHashMap<>();
        Map<Integer, List<Double>> ratesByExpr = new LinkedHashMap<>();
        for (ExpressionEnum e : ExpressionEnum.values()) {
            countsByExpr.put(e.getCode(), new ArrayList<>());
            ratesByExpr.put(e.getCode(), new ArrayList<>());
        }

        for (String date : dates) {
            Map<Integer, Integer> emotionMap = byDate.getOrDefault(date, Collections.emptyMap());
            int total = emotionMap.values().stream().mapToInt(Integer::intValue).sum();
            for (ExpressionEnum e : ExpressionEnum.values()) {
                int cnt = emotionMap.getOrDefault(e.getCode(), 0);
                countsByExpr.get(e.getCode()).add(cnt);
                double rate = total == 0 ? 0.0 : Math.round(cnt * 1000.0 / total) / 10.0;
                ratesByExpr.get(e.getCode()).add(rate);
            }
        }

        // 整体占比（环形图用）
        int totalAll = countsByExpr.values().stream()
                .flatMapToInt(list -> list.stream().mapToInt(Integer::intValue))
                .sum();

        FaceEmotionTrendVO vo = new FaceEmotionTrendVO();
        vo.setDates(dates);
        fillVo(vo, countsByExpr, ratesByExpr, totalAll);
        return vo;
    }

    /** 按 code 把 count/rate/totalRate 填入 VO */
    private void fillVo(FaceEmotionTrendVO vo,
                        Map<Integer, List<Integer>> countsByExpr,
                        Map<Integer, List<Double>> ratesByExpr,
                        int totalAll) {
        vo.setJoyCount(countsByExpr.get(0));
        vo.setJoyRate(ratesByExpr.get(0));
        vo.setTotalJoyRate(totalAll == 0 ? 0.0 : safePct(countsByExpr.get(0), totalAll));

        vo.setSurpriseCount(countsByExpr.get(1));
        vo.setSurpriseRate(ratesByExpr.get(1));
        vo.setTotalSurpriseRate(totalAll == 0 ? 0.0 : safePct(countsByExpr.get(1), totalAll));

        vo.setNeutralCount(countsByExpr.get(2));
        vo.setNeutralRate(ratesByExpr.get(2));
        vo.setTotalNeutralRate(totalAll == 0 ? 0.0 : safePct(countsByExpr.get(2), totalAll));

        vo.setConfusionCount(countsByExpr.get(3));
        vo.setConfusionRate(ratesByExpr.get(3));
        vo.setTotalConfusionRate(totalAll == 0 ? 0.0 : safePct(countsByExpr.get(3), totalAll));

        vo.setDisgustCount(countsByExpr.get(4));
        vo.setDisgustRate(ratesByExpr.get(4));
        vo.setTotalDisgustRate(totalAll == 0 ? 0.0 : safePct(countsByExpr.get(4), totalAll));

        vo.setAngerCount(countsByExpr.get(5));
        vo.setAngerRate(ratesByExpr.get(5));
        vo.setTotalAngerRate(totalAll == 0 ? 0.0 : safePct(countsByExpr.get(5), totalAll));

        vo.setSadnessCount(countsByExpr.get(6));
        vo.setSadnessRate(ratesByExpr.get(6));
        vo.setTotalSadnessRate(totalAll == 0 ? 0.0 : safePct(countsByExpr.get(6), totalAll));
    }

    private Double safePct(List<Integer> counts, int totalAll) {
        int sum = counts.stream().mapToInt(Integer::intValue).sum();
        return Math.round(sum * 1000.0 / totalAll) / 10.0;
    }
}
