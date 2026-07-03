package com.guying.service.Impl;

import com.guying.common.enums.ExpressionEnum;
import com.guying.mapper.FaceEmotionRecordMapper;
import com.guying.pojo.dto.FaceEmotionResult;
import com.guying.pojo.entity.FaceEmotionRecord;
import com.guying.service.FaceEmotionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeTypeUtils;

import java.util.Base64;

/**
 * 面部表情情感分析实现：参照 ExperienceAnalysisServiceImpl 的异步 + BeanOutputConverter 模式。
 * - @Async 保证不阻塞 WebSocket 线程；
 * - 非流式调用视觉大模型，整段返回 JSON 供 BeanOutputConverter 解析；
 * - 置信度过低或解析失败仅告警并跳过，不污染统计；
 * - try/catch 吞异常，单帧失败不影响其它帧与会话。
 */
@Service
@Slf4j
public class FaceEmotionServiceImpl implements FaceEmotionService {

    /** 置信度低于该阈值视为不可信，跳过入库 */
    private static final double CONFIDENCE_THRESHOLD = 0.3;

    @Autowired
    @Qualifier("emotionVisionChatClient")
    private ChatClient emotionVisionChatClient;

    @Autowired
    private FaceEmotionRecordMapper faceEmotionRecordMapper;

    @Async
    @Transactional
    @Override
    public void analyze(String base64Image, Long userId, Long attractionId, String conversationId) {
        if (base64Image == null || base64Image.isEmpty()) {
            return;
        }
        BeanOutputConverter<FaceEmotionResult> converter =
                new BeanOutputConverter<>(FaceEmotionResult.class);

        String prompt = "分析画面中游客的面部表情，从以下七个表情里选最贴合的一个：喜悦、惊讶、中性、困惑、厌恶、愤怒、悲伤。\n"
                + "要求：\n"
                + "1. expression 必须是上述七个词之一\n"
                + "2. confidence 为该判断的置信度，0~1 之间\n"
                + "3. reason 为不超过 20 字的简短依据\n"
                + "若画面中无人脸或无法判断，expression 填“中性”、confidence 填 0。\n"
                + converter.getFormat(); // 自动附加 JSON 格式要求
        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64Image);
            ByteArrayResource resource = new ByteArrayResource(imageBytes);

            String json = emotionVisionChatClient.prompt()
                    .user(u -> u.text(prompt).media(MimeTypeUtils.IMAGE_JPEG, resource))
                    .call()
                    .content();

            FaceEmotionResult result = converter.convert(json);
            if (result == null || result.getExpression() == null) {
                log.warn("面部表情分析结果为空, userId={}, conversationId={}, json={}", userId, conversationId, json);
                return;
            }
            Double confidence = result.getConfidence();
            if (confidence != null && confidence < CONFIDENCE_THRESHOLD) {
                log.info("面部表情分析置信度过低({}), 跳过入库, userId={}, conversationId={}", confidence, userId, conversationId);
                return;
            }

            ExpressionEnum expression = ExpressionEnum.fromDesc(result.getExpression());
            // 兜底告警：LLM 返回了枚举外的表情描述（会被静默兜底为中性，污染统计）
            if (!expression.getDesc().equals(result.getExpression())) {
                log.warn("表情描述[{}]未匹配枚举，已兜底为[{}], userId={}, conversationId={}",
                        result.getExpression(), expression.getDesc(), userId, conversationId);
            }

            FaceEmotionRecord record = new FaceEmotionRecord();
            record.setUserId(userId);
            record.setAttractionId(attractionId);
            record.setConversationId(conversationId);
            record.setExpression(expression.getCode());
            record.setConfidence(confidence);
            record.setDetail(buildDetail(result));
            faceEmotionRecordMapper.insert(record);
        } catch (Exception e) {
            log.error("面部表情分析异常, userId={}, conversationId={}", userId, conversationId, e);
        }
    }

    /** 拼接 detail JSON：{"reason":"..."}，reason 为空则存 null，避免无意义空对象。 */
    private String buildDetail(FaceEmotionResult r) {
        if (r.getReason() == null || r.getReason().isBlank()) {
            return null;
        }
        // 简单转义双引号与反斜杠，避免破坏 JSON
        String safe = r.getReason().replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"reason\":\"" + safe + "\"}";
    }
}
