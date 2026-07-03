package com.guying.service.impl;

import com.guying.ai.dto.EmotionStatDTO;
import com.guying.ai.dto.EmotionTrendDTO;
import com.guying.ai.dto.FaceEmotionTrendDTO;
import com.guying.ai.dto.FocusStatDTO;
import com.guying.ai.dto.SuggestionDTO;
import com.guying.ai.service.AIExperienceAnalysisInternalService;
import com.guying.ai.service.AiServiceSuggestionInternalService;
import com.guying.ai.service.FaceEmotionInternalService;
import com.guying.common.enums.EmotionEnum;
import com.guying.common.enums.ExpressionEnum;
import com.guying.common.enums.FocusEnum;
import com.guying.converter.AnalysisConverter;
import com.guying.pojo.vo.EmotionFocusCardVO;
import com.guying.pojo.vo.EmotionTrendVO;
import com.guying.pojo.vo.SuggestionVO;
import com.guying.service.AnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AnalysisServiceImpl implements AnalysisService {
    @Autowired
    private AiServiceSuggestionInternalService aiServiceSuggestionInternalService;

    @Autowired
    private AIExperienceAnalysisInternalService aiExperienceAnalysisInternalService;
    @Autowired
    private FaceEmotionInternalService faceEmotionInternalService;
    @Autowired
    private AnalysisConverter analysisConverter;

    /**
     * 面部表情 code → 文本情感 code 的映射。
     * 喜悦/惊讶 → 正面，中性/困惑 → 中性，厌恶/愤怒/悲伤 → 负面。
     */
    private static EmotionEnum mapFaceToEmotion(int expressionCode) {
        return switch (ExpressionEnum.fromCode(expressionCode)) {
            case JOY, SURPRISE -> EmotionEnum.POSITIVE;
            case NEUTRAL, CONFUSION -> EmotionEnum.NEUTRAL;
            case DISGUST, ANGER, SADNESS -> EmotionEnum.NEGATIVE;
        };
    }

    @Override
    public EmotionTrendVO getEmotionTrend(Long attractionId, Integer days) {
        List<EmotionTrendDTO> textList = aiExperienceAnalysisInternalService.getEmotionTrend(attractionId, days);
        List<FaceEmotionTrendDTO> faceList = faceEmotionInternalService.getExpressionTrend(attractionId, days);

        // 生成完整日期序列，防止某天无数据导致断点
        List<String> dates = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            dates.add(LocalDate.now().minusDays(i)
                    .format(DateTimeFormatter.ofPattern("MM-dd")));
        }

        // 按日期分组：date -> (emotion -> count)
        Map<String, Map<Integer, Integer>> byDate = new LinkedHashMap<>();
        for (String date : dates) {
            byDate.put(date, new HashMap<>());
        }

        // 文本情感数据
        for (EmotionTrendDTO dto : textList) {
            byDate.computeIfAbsent(dto.getDate(), k -> new HashMap<>())
                    .put(dto.getEmotion(), dto.getCount());
        }

        // 面部表情数据：映射到文本三类后累加
        for (FaceEmotionTrendDTO dto : faceList) {
            EmotionEnum target = mapFaceToEmotion(dto.getExpression());
            byDate.computeIfAbsent(dto.getDate(), k -> new HashMap<>())
                    .merge(target.getCode(), dto.getCount(), Integer::sum);
        }

        // 组装 VO
        List<Integer> positiveCount = new ArrayList<>();
        List<Integer> neutralCount  = new ArrayList<>();
        List<Integer> negativeCount = new ArrayList<>();
        List<Double>  positiveRate  = new ArrayList<>();
        List<Double>  neutralRate   = new ArrayList<>();
        List<Double>  negativeRate  = new ArrayList<>();

        // 严格按 dates(权威横轴) 遍历，保证各 count/rate 数组长度与 dates 完全对齐，
        // 不受 SQL 边界多返回一天的影响
        for (String date : dates) {
            Map<Integer, Integer> emotionMap = byDate.getOrDefault(date, Collections.emptyMap());
            int pos   = emotionMap.getOrDefault(EmotionEnum.POSITIVE.getCode(), 0);
            int neu   = emotionMap.getOrDefault(EmotionEnum.NEUTRAL.getCode(),  0);
            int neg   = emotionMap.getOrDefault(EmotionEnum.NEGATIVE.getCode(), 0);
            int total = pos + neu + neg;

            positiveCount.add(pos);
            neutralCount.add(neu);
            negativeCount.add(neg);

            if (total == 0) {
                positiveRate.add(0.0);
                neutralRate.add(0.0);
                negativeRate.add(0.0);
            } else {
                positiveRate.add(Math.round(pos * 1000.0 / total) / 10.0);
                neutralRate.add(Math.round(neu * 1000.0 / total) / 10.0);
                negativeRate.add(Math.round(neg * 1000.0 / total) / 10.0);
            }
        }

        EmotionTrendVO vo = new EmotionTrendVO();
        vo.setDates(dates);
        vo.setPositiveCount(positiveCount);
        vo.setNeutralCount(neutralCount);
        vo.setNegativeCount(negativeCount);
        vo.setPositiveRate(positiveRate);
        vo.setNeutralRate(neutralRate);
        vo.setNegativeRate(negativeRate);

        int totalPos = positiveCount.stream().mapToInt(Integer::intValue).sum();
        int totalNeu = neutralCount.stream().mapToInt(Integer::intValue).sum();
        int totalNeg = negativeCount.stream().mapToInt(Integer::intValue).sum();
        int total = totalPos + totalNeu + totalNeg;

        if (total == 0) {
            vo.setTotalPositiveRate(0.0);
            vo.setTotalNeutralRate(0.0);
            vo.setTotalNegativeRate(0.0);
        } else {
            vo.setTotalPositiveRate(Math.round(totalPos * 1000.0 / total) / 10.0);
            vo.setTotalNeutralRate(Math.round(totalNeu * 1000.0 / total) / 10.0);
            vo.setTotalNegativeRate(Math.round(totalNeg * 1000.0 / total) / 10.0);
        }

        return vo;
    }

    @Override
    public EmotionFocusCardVO getEmotionFocusCard(Long attractionId, Integer days) {
        EmotionStatDTO emotionStat = aiExperienceAnalysisInternalService.getEmotionStat(attractionId, days);
        List<FocusStatDTO> focusStatList = aiExperienceAnalysisInternalService.getFocusStat(attractionId, days);
        List<FocusStatDTO> worstFocusList = aiExperienceAnalysisInternalService.getWorstFocus(attractionId, days);
        List<FaceEmotionTrendDTO> faceList = faceEmotionInternalService.getExpressionTrend(attractionId, days);

        // 汇总面部贡献：喜悦+惊讶 → 正面
        int facePositive = 0;
        int faceTotal = 0;
        for (FaceEmotionTrendDTO dto : faceList) {
            faceTotal += dto.getCount();
            EmotionEnum mapped = mapFaceToEmotion(dto.getExpression());
            if (mapped == EmotionEnum.POSITIVE) {
                facePositive += dto.getCount();
            }
        }

        EmotionFocusCardVO vo = new EmotionFocusCardVO();

        // 正面情感占比（文本 + 面部合并）
        long total = emotionStat.getTotal() + faceTotal;
        long positive = emotionStat.getPositiveCount() + facePositive;
        double positiveRate = total == 0 ? 0.0
                : Math.round(positive * 1000.0 / total) / 10.0;
        vo.setPositiveRate(positiveRate);

        // 较上期变化
        double preRate = emotionStat.getPreTotal() == 0 ? 0.0
                : Math.round(emotionStat.getPrePositiveCount() * 1000.0 / emotionStat.getPreTotal()) / 10.0;
        vo.setPositiveRateChange(Math.round((positiveRate - preRate) * 10.0) / 10.0);

        // 变化标签
        String changeLabel = switch (days) {
            case 1  -> "较昨日";
            case 7  -> "较上个7天";
            case 30 -> "较上个30天";
            default -> "较上期";
        };
        vo.setChangeLabel(changeLabel);

        // 高频关注点，取前两名
        String topFocus;
        if (focusStatList.size() >= 2) {
            FocusStatDTO first  = focusStatList.get(0);
            FocusStatDTO second = focusStatList.get(1);
            // 第二名数量不到第一名的一半，只展示第一名
            if (second.getCount() * 2 < first.getCount()) {
                topFocus = FocusEnum.fromCode(first.getFocus()).getDesc();
            } else {
                topFocus = FocusEnum.fromCode(first.getFocus()).getDesc()
                        + "/" + FocusEnum.fromCode(second.getFocus()).getDesc();
            }
        } else {
            topFocus = focusStatList.isEmpty() ? "暂无数据"
                    : FocusEnum.fromCode(focusStatList.getFirst().getFocus()).getDesc();
        }
        vo.setTopFocus(topFocus);

        // 待改善项，取前两名
        String worstFocus;
        if (worstFocusList.size() >= 2) {
            FocusStatDTO first  = worstFocusList.get(0);
            FocusStatDTO second = worstFocusList.get(1);
            if (second.getCount() * 2 < first.getCount()) {
                worstFocus = FocusEnum.fromCode(first.getFocus()).getDesc();
            } else {
                worstFocus = FocusEnum.fromCode(first.getFocus()).getDesc()
                        + "/" + FocusEnum.fromCode(second.getFocus()).getDesc();
            }
        }else{
            worstFocus = worstFocusList.isEmpty() ? "暂无数据"
                    : FocusEnum.fromCode(worstFocusList.getFirst().getFocus()).getDesc();
        }
        vo.setWorstFocus(worstFocus);
        return vo;
    }

    @Override
    public SuggestionVO getAiServiceSuggestion(Long attractionId, Integer type) {
        SuggestionDTO suggestionDTO = aiServiceSuggestionInternalService.getSuggestion(attractionId, type);
        return analysisConverter.convert2SuggestionVO(suggestionDTO);
    }
}
