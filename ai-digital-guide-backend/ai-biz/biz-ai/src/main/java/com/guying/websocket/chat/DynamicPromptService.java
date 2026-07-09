package com.guying.websocket.chat;

import com.guying.attractions.service.UserAttractionsInternalService;
import com.guying.common.constants.RedisConstants;
import com.guying.common.enums.GenderEnum;
import com.guying.prompt.AiSystemConstants;
import com.guying.rag.VectorSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.guying.common.constants.RedisConstants.*;

/**
 * 拼接 AI 讲解员动态提示词：用户信息 + 命中的热门问答 / RAG 上下文。
 */
@Service
@Slf4j
public class DynamicPromptService {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private VectorSearchService vectorSearchService;

    @Autowired
    private UserAttractionsInternalService userAttractionsInternalService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public String build(String userText, Long userId, Long attractionId) {
        Map<String, Object> dynamicPrompt = stringRedisTemplate
                .<String, Object>opsForHash()
                .entries(RedisConstants.USER_INFO_KEY + userId);
        // gender 存的是数字码(0/1/2)，转成中文描述供提示词使用
        Object genderVal = dynamicPrompt.get("gender");
        if (genderVal != null) {
            int code = Integer.parseInt((String) genderVal);
            dynamicPrompt.put("gender", GenderEnum.fromCode(code).getDesc());
        }
        dynamicPrompt.put("locationInfo", "");
        dynamicPrompt.put("absoluteFact", "");
        dynamicPrompt.put("context", "");

        List<Document> hotQuestions = vectorSearchService.searchSimilarQuestion(userText, attractionId, 0.85);
        if (!hotQuestions.isEmpty()) {
            applyHotFaq(dynamicPrompt, hotQuestions.getFirst(), attractionId);
            String score = formatSimilarity(hotQuestions.getFirst());
            log.info("═══ [METRICS] RAG | attractionId={} | faqHit=true | faqSimilarity={} | docCount=0 ═══",
                    attractionId, score);
        } else {
            applyRagContext(dynamicPrompt, userText, attractionId);
        }

        return new PromptTemplate(AiSystemConstants.AI_GUIDE_SYSTEM_PROMPT).render(dynamicPrompt);
    }

    private static String formatSimilarity(Document doc) {
        Object score = doc.getMetadata().get("distance");
        if (score instanceof Number n) {
            return String.format("%.4f", n.doubleValue());
        }
        return "N/A";
    }

    private void applyHotFaq(Map<String, Object> dynamicPrompt, Document hitDoc, Long attractionId) {
        String faqId = hitDoc.getMetadata().get("faqId").toString();

        // 累加热门问题访问次数
        String today = LocalDate.now().format(DAY_FMT);
        String redisKey = HOT_FAQ_KEY + attractionId + ":" + today;
        stringRedisTemplate.opsForZSet().incrementScore(redisKey, faqId, 1);
        stringRedisTemplate.expire(redisKey, 2, TimeUnit.DAYS);

        // 优先取 redis 缓存的标准答案，未命中再回源 mysql
        String answerKey = HOT_ANSWER_KEY + attractionId + ":" + faqId;
        String redisAnswer = stringRedisTemplate.opsForValue().get(answerKey);
        if (redisAnswer != null) {
            dynamicPrompt.put("absoluteFact", redisAnswer);
        } else {
            String mysqlAnswer = userAttractionsInternalService.getAbsoluteFactByQuestionId(faqId);
            dynamicPrompt.put("absoluteFact", mysqlAnswer);
            stringRedisTemplate.opsForValue().set(
                    answerKey, mysqlAnswer, RedisConstants.HOT_ANSWER_EXPIRE_TIME, TimeUnit.HOURS);
        }
    }

    private void applyRagContext(Map<String, Object> dynamicPrompt, String userText, Long attractionId) {
        List<Document> documents = vectorSearchService.searchDocByAttraction(userText, attractionId, 5);
        String context = documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));
        dynamicPrompt.put("context", context);

        String topScore = documents.isEmpty() ? "N/A" : formatSimilarity(documents.getFirst());
        log.info("═══ [METRICS] RAG | attractionId={} | faqHit=false | docCount={} | docTopScore={} | contextLen={} ═══",
                attractionId, documents.size(), topScore, context.length());

        // 长度合规且样本未超量则记入待学习库
        Long size = stringRedisTemplate.opsForSet().size(HOT_QUESTION_KEY + attractionId);
        if (size == null || (size < 2000 && userText.length() > 2 && userText.length() <= 100)) {
            stringRedisTemplate.opsForSet().add(HOT_QUESTION_KEY + attractionId, userText);
            stringRedisTemplate.expire(HOT_QUESTION_KEY + attractionId, 48, TimeUnit.HOURS);
            log.debug("已将未命中问题加入待学习库，当前样本数: {}", (size == null ? 1 : size + 1));
        }
    }
}
