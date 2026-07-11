package com.guying.websocket.metrics;

import com.guying.websocket.session.ChatSessionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * E2E 延迟埋点：按轮次输出 LLM 阶段与完整链路（含首音/首帧）。
 * <p>
 * 多轮对话依赖 {@link ChatSessionContext#markE2eUserInput()} 清零上一轮时间戳；
 * 完整链路在首音（及数字人首帧）就绪后单独打点，避免 LLM 流结束时音视频尚未到达。
 */
@Component
@Slf4j
public class E2eMetricsLogger {

    /** LLM 流结束后：prompt+RAG + LLM→firstToken（每轮一次）。纯文本模式同时打 COMPLETE。 */
    public void logLlmStage(ChatSessionContext ctx) {
        if (ctx.getE2eUserInputTime() == 0) {
            return;
        }
        if (!ctx.tryClaimE2eLlmLog()) {
            return;
        }

        long promptRag = elapsed(ctx.getE2eAfterPromptTime(), ctx.getE2eUserInputTime());
        long llmToken = elapsed(
                ctx.getE2eLlmFirstTokenTime(),
                ctx.getE2eAfterPromptTime() > 0 ? ctx.getE2eAfterPromptTime() : ctx.getE2eUserInputTime());

        log.info("═══ [METRICS] E2E-LATENCY | stage=LLM | userId={} | prompt+RAG={}ms | LLM→firstToken={}ms ═══",
                ctx.getUserId(), promptRag, llmToken);

        if (ctx.getDigitalHumanId() == null && ctx.tryClaimE2eCompleteLog()) {
            log.info("═══ [METRICS] E2E-LATENCY | stage=COMPLETE | userId={} | prompt+RAG={}ms | LLM→firstToken={}ms | firstAudio=-1ms | firstVideo=-1ms | mode=text ═══",
                    ctx.getUserId(), promptRag, llmToken);
        }
    }

    /**
     * 首音/首帧到达后尝试输出完整 E2E（每轮一次）。
     * 数字人模式：需首音；若 MuseTalk 已连接则再等首帧。
     */
    public void tryLogAvStage(ChatSessionContext ctx) {
        if (ctx.getDigitalHumanId() == null || ctx.getE2eUserInputTime() == 0) {
            return;
        }
        if (ctx.getE2eFirstAudioTime() == 0) {
            return;
        }
        if (needVideo(ctx) && ctx.getE2eFirstVideoTime() == 0) {
            return;
        }
        if (!ctx.tryClaimE2eCompleteLog()) {
            return;
        }

        long t0 = ctx.getE2eUserInputTime();
        long promptRag = elapsed(ctx.getE2eAfterPromptTime(), t0);
        long llmToken = elapsed(
                ctx.getE2eLlmFirstTokenTime(),
                ctx.getE2eAfterPromptTime() > 0 ? ctx.getE2eAfterPromptTime() : t0);
        long firstAudio = elapsed(ctx.getE2eFirstAudioTime(), t0);
        long firstVideo = elapsed(ctx.getE2eFirstVideoTime(), t0);

        log.info("═══ [METRICS] E2E-LATENCY | stage=COMPLETE | userId={} | prompt+RAG={}ms | LLM→firstToken={}ms | firstAudio={}ms | firstVideo={}ms ═══",
                ctx.getUserId(), promptRag, llmToken, firstAudio, firstVideo);
    }

    /** 本轮首个视频帧：相对用户输入 / 相对 LLM 首 token 的开画延迟（每轮一次）。 */
    public void logDigitalHumanFirstFrame(ChatSessionContext ctx) {
        if (ctx.getE2eFirstVideoTime() == 0) {
            return;
        }
        long fromUser = elapsed(ctx.getE2eFirstVideoTime(), ctx.getE2eUserInputTime());
        long fromToken = elapsed(ctx.getE2eFirstVideoTime(), ctx.getE2eLlmFirstTokenTime());
        log.info("═══ [METRICS] DIGITAL-HUMAN | event=firstFrame | fromUserInput={}ms | fromLlmToken={}ms ═══",
                fromUser, fromToken);
    }

    private static boolean needVideo(ChatSessionContext ctx) {
        WebSocketSession museTalk = ctx.getMuseTalkSession();
        return museTalk != null && museTalk.isOpen();
    }

    private static long elapsed(long end, long start) {
        return end > 0 && start > 0 ? end - start : -1;
    }
}
