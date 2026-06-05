package com.guying.websocket.chat;

import com.guying.ratelimit.RateLimiterUtil;
import com.guying.service.ExperienceAnalysisService;
import com.guying.websocket.protocol.WsMessageSender;
import com.guying.websocket.session.ChatSessionContext;
import com.guying.websocket.tts.CosyVoiceConnector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * AI 讲解员调用入口：
 *  - 根据是否带图选择 VL / DS 模型；
 *  - 流式接收文本，按标点切句，逐句下发 aiOutput 并入队 TTS；
 *  - 异步触发用户体验/情感分析。
 */
@Service
@Slf4j
public class AiChatService {

    @Autowired
    private RateLimiterUtil rateLimiterUtil;
    @Autowired
    @Qualifier("vlGuideChatClient")
    private ChatClient vlGuideChatClient;

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

    public void invoke(ChatSessionContext ctx, String userText) {
        log.info("调用 AI 服务，用户输入：{}", userText);
        // 令牌桶限流：每用户每分钟最多10次
        if (!rateLimiterUtil.tryAcquire("ai:chat:" + ctx.getUserId(), 10, 60)) {
            sender.sendError(ctx, "请求太频繁，请稍后再试");
            return;
        }
        String conversationId = ctx.conversationId();
        String prompt = dynamicPromptService.build(userText, ctx.getUserId(), ctx.getAttractionId());
        String pendingImage = ctx.consumePendingImage();

        Flux<String> stream = (pendingImage == null)
                ? streamDs(userText, prompt, conversationId)
                : streamVl(userText, prompt, conversationId, pendingImage);

        consumeStream(ctx, stream);

        // 异步用户体验/情感分析
        experienceAnalysisService.analyze(userText, ctx.getUserId(), ctx.getAttractionId());
    }

    private Flux<String> streamDs(String userText, String prompt, String conversationId) {
        log.info("调用 llm 模型");
        return llmGuideChatClient.prompt()
                .user(u -> u.text(userText))
                .system(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();
    }

    private Flux<String> streamVl(String userText, String prompt, String conversationId, String base64Image) {
        log.info("调用 VL 模型");
        byte[] imageBytes = Base64.getDecoder().decode(base64Image);
        Resource resource = new ByteArrayResource(imageBytes);
        return vlGuideChatClient.prompt()
                .user(u -> u.text(userText).media(MimeTypeUtils.IMAGE_PNG, resource))
                .system(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();
    }

    private void consumeStream(ChatSessionContext ctx, Flux<String> stream) {
        StreamingSentenceSplitter splitter = new StreamingSentenceSplitter();
        stream.subscribe(
                delta -> {
                    List<String> sentences = splitter.consume(delta);
                    for (String sentence : sentences) {
                        emitSentence(ctx, sentence);
                    }
                },
                error -> {
                    log.error("调用 AI 服务失败", error);
                    sender.sendError(ctx, "调用 AI 服务失败");
                },
                () -> {
                    log.info("调用 AI 服务完成");
                    String tail = splitter.drain();
                    if (tail != null) {
                        sender.sendJson(ctx, "aiOutput", tail);
                    }
                    sender.sendJson(ctx, "responseDone", null);
                }
        );
    }

    private void emitSentence(ChatSessionContext ctx, String sentence) {
        sender.sendJson(ctx, "aiOutput", sentence);
        log.info("aiOutput:{}", sentence);

        ExecutorService executor = ctx.getTtsExecutor();
        if (executor != null && !executor.isShutdown()) {
            // 把可能阻塞的 TTS 调度扔进单线程池里串行执行，
            // 仅阻塞当前用户的 TTS 队列，不影响 WebFlux 主流和其它会话
            executor.submit(() -> cosyVoiceConnector.synthesize(ctx, sentence));
        }
    }
}
