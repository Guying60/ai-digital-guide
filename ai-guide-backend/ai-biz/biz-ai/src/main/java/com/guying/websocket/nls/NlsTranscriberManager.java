package com.guying.websocket.nls;

import com.alibaba.nls.client.protocol.InputFormatEnum;
import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriber;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberListener;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberResponse;
import com.guying.utils.AudioFrameBuffer;
import com.guying.websocket.chat.AiChatService;
import com.guying.websocket.protocol.WsMessageSender;
import com.guying.websocket.session.ChatSessionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Aliyun NLS 实时语音识别管理：
 *  - createTranscriber：建立识别器，注册回调（实时字幕、句末触发 AI、异常清理）
 *  - close            ：关闭识别器并清理音频缓冲
 *  - feed             ：投喂二进制音频帧
 *  - VOCAL_THRESHOLD  ：低能量帧丢弃，遇到突发声音时按需建连并补发缓冲帧
 */
@Component
@Slf4j
public class NlsTranscriberManager {

    private static final double VOCAL_THRESHOLD = 15.0;

    @Autowired
    private NlsClient nlsClient;

    @Autowired
    private WsMessageSender sender;

    @Autowired
    private AiChatService aiChatService;

    @Value("${spring.aliyun.nls.app-key}")
    private String nlsAppKey;

    public void createTranscriber(ChatSessionContext ctx) {
        try {
            SpeechTranscriber transcriber = new SpeechTranscriber(nlsClient, new ListenerImpl(ctx));
            transcriber.setAppKey(nlsAppKey);
            transcriber.setFormat(InputFormatEnum.PCM);
            transcriber.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
            transcriber.setEnableIntermediateResult(true);
            transcriber.setEnablePunctuation(true);
            transcriber.setEnableITN(true);
            ctx.setTranscriber(transcriber);
            transcriber.start();
        } catch (Exception e) {
            log.error("创建 NLS transcriber 失败 sid={}", ctx.getSid(), e);
            sender.sendError(ctx, "语音服务初始化失败");
        }
    }

    /** 关闭 transcriber 并清空缓冲 */
    public void close(ChatSessionContext ctx) {
        if (ctx == null) return;
        SpeechTranscriber transcriber = ctx.getTranscriber();
        ctx.setTranscriber(null);
        ctx.getAudioBuffer().clear();
        if (transcriber != null) {
            try {
                transcriber.close();
            } catch (Exception e) {
                log.warn("关闭 NLS 失败", e);
            }
        }
    }

    /** 关闭已有 transcriber 并新建一个（micOn 时用） */
    public void recreate(ChatSessionContext ctx) {
        SpeechTranscriber old = ctx.getTranscriber();
        if (old != null) {
            try {
                old.close();
            } catch (Exception ignored) {
            }
        }
        createTranscriber(ctx);
    }

    /**
     * 投喂二进制音频帧。
     * 若 transcriber 不存在但音量超过阈值，则触发自动建连并把当前帧入缓冲，
     * 等 onTranscriberStart 回调时再 drain 给 transcriber，避免吞字。
     */
    public void feed(ChatSessionContext ctx, byte[] audio) {
        SpeechTranscriber transcriber = ctx.getTranscriber();
        if (transcriber == null) {
            if (rms(audio) < VOCAL_THRESHOLD) {
                return;
            }
            createTranscriber(ctx);
            ctx.getAudioBuffer().offer(audio);
            return;
        }
        transcriber.send(audio);
    }

    private static double rms(byte[] audio) {
        long sum = 0;
        for (int i = 0; i < audio.length - 1; i += 2) {
            short s = (short) ((audio[i + 1] << 8) | (audio[i] & 0xFF));
            sum += (long) s * s;
        }
        return Math.sqrt((double) sum / ((double) audio.length / 2));
    }

    private class ListenerImpl extends SpeechTranscriberListener {

        private final ChatSessionContext ctx;

        ListenerImpl(ChatSessionContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void onTranscriberStart(SpeechTranscriberResponse r) {
            log.info("NLS 开始识别 sid={}", ctx.getSid());
            AudioFrameBuffer buffer = ctx.getAudioBuffer();
            SpeechTranscriber t = ctx.getTranscriber();
            if (t != null) {
                buffer.drainTo(t::send);
            }
        }

        @Override
        public void onSentenceBegin(SpeechTranscriberResponse r) {
            log.info("NLS 一句话开始 sid={}", ctx.getSid());
            sender.sendJson(ctx, "speechStarted", null);
        }

        @Override
        public void onSentenceEnd(SpeechTranscriberResponse r) {
            String text = r.getTransSentenceText();
            log.info("一句话结束，文本：{}", text);
            if (text != null && !text.isEmpty()) {
                sender.sendJson(ctx, "userInput", text);
                aiChatService.invoke(ctx, text);
            }
        }

        @Override
        public void onTranscriptionResultChange(SpeechTranscriberResponse r) {
            sender.sendJson(ctx, "interimText", r.getTransSentenceText());
        }

        @Override
        public void onTranscriptionComplete(SpeechTranscriberResponse r) {
            log.info("NLS 识别完成 sid={}", ctx.getSid());
        }

        @Override
        public void onFail(SpeechTranscriberResponse r) {
            log.warn("NLS 连接断开 sid={} status={} msg={}",
                    ctx.getSid(), r.getStatus(), r.getStatusText());
            ctx.setTranscriber(null);
        }
    }
}
