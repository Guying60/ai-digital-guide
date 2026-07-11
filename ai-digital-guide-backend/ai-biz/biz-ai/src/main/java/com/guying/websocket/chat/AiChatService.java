package com.guying.websocket.chat;

import com.guying.ratelimit.RateLimiterUtil;
import com.guying.service.ExperienceAnalysisService;
import com.guying.websocket.metrics.E2eMetricsLogger;
import com.guying.websocket.protocol.WsMessageSender;
import com.guying.websocket.session.ChatSessionContext;
import com.guying.websocket.tts.CosyVoiceConnector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * AI 讲解员调用入口：
 *  - 纯文本对话流（后置摄像头"景象识别"方案已弃用，VL 图像问答链路移除）；
 *  - 流式接收文本，按标点切句，逐句下发 aiOutput 并入队 TTS；
 *  - 异步触发用户体验/情感分析。
 */
@Service
@Slf4j
public class AiChatService {

    @Autowired
    private RateLimiterUtil rateLimiterUtil;

    @Autowired
    @Qualifier("llmGuideChatClient")
    private ChatClient llmGuideChatClient;

    @Autowired
    private DynamicPromptService dynamicPromptService;

    @Autowired
    private ExperienceAnalysisService experienceAnalysisService;

    @Autowired
    private WsMessageSender sender;

    @Autowired
    private CosyVoiceConnector cosyVoiceConnector;

    @Autowired
    private E2eMetricsLogger e2eMetricsLogger;

    public void invoke(ChatSessionContext ctx, String userText) {
        // 令牌桶限流：每用户每分钟最多10次
        if (!rateLimiterUtil.tryAcquire("ai:chat:" + ctx.getUserId(), 10, 60)) {
            sender.sendError(ctx, "请求太频繁，请稍后再试");
            return;
        }
        // 本会话首次提问，标记为"有效会话"（断开时据此决定是否落历史/待评价）
        ctx.incrementQuestionCount();
        String conversationId = ctx.conversationId();
        String prompt = dynamicPromptService.build(userText, ctx.getUserId(), ctx.getAttractionId());
        ctx.markE2eAfterPrompt();  // T1: Prompt+RAG 组装完成

        // 后置摄像头"景象识别"方案已弃用，对话一律走纯文本流
        Flux<String> stream = streamDs(userText, prompt, conversationId);

        consumeStream(ctx, stream);

        // 异步用户体验/情感分析
        experienceAnalysisService.analyze(userText, ctx.getUserId(), ctx.getAttractionId(), conversationId);
    }

    private Flux<String> streamDs(String userText, String prompt, String conversationId) {
        return llmGuideChatClient.prompt()
                .user(u -> u.text(userText))
                .system(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();
    }

    private void consumeStream(ChatSessionContext ctx, Flux<String> stream) {
        StreamingSentenceSplitter splitter = new StreamingSentenceSplitter();
        stream.subscribe(
                delta -> {
                    // T2: LLM 首个 token
                    if (ctx.getE2eLlmFirstTokenTime() == 0) {
                        ctx.markE2eLlmFirstToken();
                    }
                    List<String> sentences = splitter.consume(delta);
                    log.debug("收到 delta，切出 {} 个句子", sentences.size());
                    for (String sentence : sentences) {
                        emitSentence(ctx, sentence);
                    }
                },
                error -> {
                    log.error("调用 AI 服务失败", error);
                    sender.sendError(ctx, "调用 AI 服务失败");
                },
                () -> {
                    String tail = splitter.drain();
                    if (tail != null) {
                        emitSentence(ctx, tail);
                    }
                    // 仅打 LLM 阶段；首音/首帧在 TTS / MuseTalk 就绪后由 E2eMetricsLogger 补 COMPLETE
                    e2eMetricsLogger.logLlmStage(ctx);
                    sender.sendJson(ctx, "responseDone", null);
                }
        );
    }

    private void emitSentence(ChatSessionContext ctx, String sentence) {
        sender.sendJson(ctx, "aiOutput", sentence);

        // 未配置数字人时为纯文本会话：无 CosyVoice 连接，跳过 TTS 调度，
        // 避免每句都走到 synthesize 的 "CosyVoice session not ready" 兜底分支造成无效告警刷屏
        if (ctx.getDigitalHumanId() == null) {
            return;
        }
        ExecutorService executor = ctx.getTtsExecutor();
        // 把可能阻塞的 TTS 调度扔进单线程池里串行执行，
        // 仅阻塞当前用户的 TTS 队列，不影响 WebFlux 主流和其它会话
        executor.submit(() -> cosyVoiceConnector.synthesize(ctx, sentence));
    }
}
