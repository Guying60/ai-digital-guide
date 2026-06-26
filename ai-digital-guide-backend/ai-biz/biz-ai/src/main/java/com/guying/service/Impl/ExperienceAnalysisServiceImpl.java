package com.guying.service.Impl;

import com.guying.ai.dto.EmotionStatDTO;
import com.guying.ai.dto.FocusStatDTO;
import com.guying.common.enums.EmotionEnum;
import com.guying.common.enums.FocusEnum;
import com.guying.mapper.ExperienceAnalysisMapper;
import com.guying.pojo.dto.ExperienceAnalysisResult;
import com.guying.pojo.dto.NegativeSampleDTO;
import com.guying.pojo.dto.Suggestion;
import com.guying.pojo.entity.AiExperienceAnalysis;
import com.guying.service.ExperienceAnalysisService;
import com.guying.attractions.service.ReviewInternalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ExperienceAnalysisServiceImpl implements ExperienceAnalysisService {

    @Autowired
    @Qualifier("expertChatClient")
    private ChatClient expertChatClient;
    @Autowired
    private ExperienceAnalysisMapper analysisMapper;

    @Autowired
    private ReviewInternalService reviewInternalService;

    @Async
    @Transactional
    @Override
    public void analyze(String userMessage, Long userId, Long attractionId, String conversationId) {
        BeanOutputConverter<ExperienceAnalysisResult> converter =
                new BeanOutputConverter<>(ExperienceAnalysisResult.class);

        String prompt = "分析游客消息的情感和关注点。\n"
                + "情感从以下选一个：正面、中性、负面\n"
                + "关注点从以下选，可多选：餐饮、票务、导览、交通、停车、住宿、景点体验、投诉建议、其他\n"
                + "游客消息：" + userMessage + "\n"
                + converter.getFormat(); // 自动附加 JSON 格式要求
        String json = expertChatClient.prompt()
                .user(prompt)
                .call()
                .content();
        ExperienceAnalysisResult result = converter.convert(json);
        EmotionEnum emotion = EmotionEnum.fromDesc(result.getEmotion());
        for (String focusDesc : result.getFocus()) {
            AiExperienceAnalysis record = new AiExperienceAnalysis();
            record.setConversationId(conversationId);
            record.setUserId(userId);
            record.setAttractionId(attractionId);
            record.setEmotion(emotion.getCode());
            record.setFocus(FocusEnum.fromDesc(focusDesc).getCode());
            analysisMapper.insert(record);
        }




    }

    @Override
    public Suggestion generateSuggestion(Long attractionId, int days) {
        EmotionStatDTO emotionStat          = analysisMapper.getEmotionStat(attractionId, days, days * 2);
        List<FocusStatDTO> worstFocusList   = analysisMapper.getWorstFocus(attractionId, days);
        List<NegativeSampleDTO> sampleList = analysisMapper.getNegativeSample(attractionId, days);
        List<String> feedbackText = reviewInternalService.getFeedbackText(attractionId, days);
        if (worstFocusList.isEmpty() || emotionStat == null) {
            log.warn("该景点没有数据:{}", attractionId);
            return new Suggestion("暂无数据","暂无数据");
        }

        // 计算情感占比
        int total = emotionStat.getTotal() == null ? 0 : emotionStat.getTotal();
        if (total == 0) return null;

        double positiveRate = Math.round(emotionStat.getPositiveCount() * 1000.0 / total) / 10.0;
        double negativeRate = Math.round(100 - positiveRate - (total - emotionStat.getPositiveCount()) * 1000.0 / total) / 10.0;
        double neutralRate  = Math.round(100 - positiveRate - negativeRate * 10) / 10.0;


        // 拼接负面情感关注点分布
        String worstFocusDesc = worstFocusList.stream()
                .limit(3)
                .map(f -> FocusEnum.fromCode(f.getFocus()).getDesc()
                        + "(" + Math.round(f.getCount() * 1000.0 / total) / 10.0 + "%)")
                .collect(Collectors.joining("、"));

        // 拼接负面情感关注点分布
        Map<Integer, List<String>> sampleByFocus = sampleList.stream()
                .collect(Collectors.groupingBy(
                        NegativeSampleDTO::getFocus,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> list.stream()
                                        .limit(5)
                                        .map(NegativeSampleDTO::getContent)
                                        .collect(Collectors.toList())
                        )
                ));

        StringBuilder sampleDesc = new StringBuilder();
        sampleByFocus.forEach((focusCode, contents) -> {
            sampleDesc.append("【").append(FocusEnum.fromCode(focusCode).getDesc()).append("】\n");
            contents.forEach(c -> sampleDesc.append("- ").append(c).append("\n"));
        });

        // 拼接游客反馈内容
        String feedbackDesc = feedbackText.stream()
                .limit(20)
                .collect(Collectors.joining("\n- ", "- ", ""));

        //准备生成建议
        BeanOutputConverter<Suggestion> converter =
                new BeanOutputConverter<>(Suggestion.class);
        // 组装 Prompt
        String prompt = "你是一个专业的景区运营分析师，根据以下数据为景区管理方生成服务改善建议。\n"
                + "要求：\n"
                + "1. 先输出一句不超过30字的简短总结\n"
                + "2. 再用一段话（不超过200字）列出具体可执行的建议，每条建议需针对具体问题给出具体措施\n"
                + "3. 建议内容需结合游客原话，不要泛泛而谈\n\n"
                + "近" + days + "天数据摘要：\n"
                + "- 总对话数：" + total + "\n"
                + "- 情感分布：正面 " + positiveRate + "%、中性 " + neutralRate + "%、负面 " + negativeRate + "%\n"
                + "- 负面关注点分布：" + worstFocusDesc + "\n\n"
                + "负面对话样本（可能存在相似内容，请归纳后给出建议）：\n"
                + sampleDesc + "\n"
                + "游客评价反馈（可能存在相似内容，请归纳后给出建议）：\n"
                + feedbackDesc + "\n\n"
                + converter.getFormat();

        log.info("prompt:{}", prompt);
        String json = expertChatClient.prompt().user(prompt).call().content();
        log.info("景点ID:{} 生成AI建议json: {}", attractionId,json);
        return converter.convert(json);


    }
}