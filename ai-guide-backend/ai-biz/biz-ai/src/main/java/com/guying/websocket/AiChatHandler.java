package com.guying.websocket;

import com.alibaba.nls.client.protocol.InputFormatEnum;
import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriber;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberListener;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberResponse;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.guying.attractions.service.UserAttractionsInternalService;
import com.guying.common.constants.MqConstants;
import com.guying.exception.ServiceException;
import com.guying.message.UserTourHistoryMessage;
import com.guying.common.constants.RedisConstants;
import com.guying.prompt.AiSystemConstants;
import com.guying.rag.VectorSearchService;
import com.guying.service.ExperienceAnalysisService;
import com.guying.user.service.UserInternalService;
import com.guying.utils.AudioFrameBuffer;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.guying.common.constants.RedisConstants.*;

@Component
@Slf4j
public class AiChatHandler extends AbstractWebSocketHandler {


    @Value("${spring.museTalk.ws-url}")
    private String pythonWsUrl;
    @Autowired
    @Qualifier("vlGuideChatClient")
    private ChatClient vlGuideChatClient;

    @Autowired
    @Qualifier("dsGuideChatClient")
    private ChatClient dsGuideChatClient;
    @Autowired
    private UserInternalService userService;

    @Autowired
    private UserAttractionsInternalService userAttractionsInternalService;

    @Autowired
    ExperienceAnalysisService experienceAnalysisService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private VectorSearchService vectorSearchService;

    @Autowired
    private NlsClient nlsClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Value("${spring.aliyun.nls.app-key}")
    private String nlsAppKey;

    @Value("${spring.cosyVoice.ws-url}")
    private String cosyVoiceWsUrl;


    //用户会话
    private  static final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();
    //python数字人会话
    private static final Map<String, WebSocketSession> pythonSessionMap = new ConcurrentHashMap<>();
    //CosyVoice TTS 会话
    private static final Map<String, WebSocketSession> cosyVoiceSessionMap = new ConcurrentHashMap<>();

    //发送消息串行化
    private final ConcurrentHashMap<String, ReentrantLock> sendLocks = new ConcurrentHashMap<>();
    //NLS识别器
    private static final Map<String, SpeechTranscriber> transcriberMap = new ConcurrentHashMap<>();

    //用户最后活跃时间
    private final Map<String, Long> lastActiveTimeMap = new ConcurrentHashMap<>();
    //NLS定时器

    private final Map<String, Long> lastAudioTimeMap = new ConcurrentHashMap<>();

    // 静默帧：16kHz 16bit 单声道，200ms

    // 用于保证每个用户的语音合成按顺序依次执行
    private final Map<String, ExecutorService> ttsExecutorMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    //存放图片
    private static final Map<String, String> pendingImageMap = new ConcurrentHashMap<>();

    private static final double VOCAL_THRESHOLD = 15.0;

    private static final Map<String, AudioFrameBuffer> audioBufferMap = new ConcurrentHashMap<>();




    /**
     * 连接建立后的逻辑
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sid = session.getId();
        lastActiveTimeMap.put(sid, System.currentTimeMillis());
        sendLocks.put(session.getId(), new ReentrantLock());
        sessionMap.put(sid, session);
        Long userId = (Long) session.getAttributes().get("userId");
        Long attractionId = (Long) session.getAttributes().get("attractionId");
        log.info("用户连接成功，sid: {}，userId: {}，attractionId: {}", sid, userId, attractionId);
        //创建对话Id
        String conversationId = attractionId + ":" + userId;
        log.info("客户端连接成功，sid: {}，当前在线人数：{}", sid, sessionMap.size());
        try {
            stringRedisTemplate.opsForValue().set(USER_CONVERSATION_KEY + userId, conversationId, RedisConstants.CONVERSATION_EXPIRE_TIME, TimeUnit.HOURS);
            if (!stringRedisTemplate.hasKey(USER_INFO_KEY + userId)) {
                //查询用户信息
                Map<String, String> userInfo = userService.getUserInfo(userId);
                //将用户设定存入Redis,用于后续拼接提示词
                stringRedisTemplate.opsForHash().putAll(RedisConstants.USER_INFO_KEY +userId,  userInfo);
                stringRedisTemplate.expire(RedisConstants.USER_INFO_KEY +userId, RedisConstants.USER_INFO_EXPIRE_TIME, TimeUnit.HOURS);
            }
            //异步创建userTourHistory
            UserTourHistoryMessage userTourHistoryMessage = new UserTourHistoryMessage();
            userTourHistoryMessage.setUserId(userId);
            userTourHistoryMessage.setAttractionId(attractionId);
            userTourHistoryMessage.setConversationId(conversationId);
            rabbitTemplate.convertAndSend(MqConstants.USER_TOUR_HISTORY_DIRECT, MqConstants.USER_TOUR_HISTORY_ROUTING_KEY,userTourHistoryMessage);
            //连接数字人，加载数字人声音
            connectToPythonMuseTalk(session,attractionId);
            //连接 CosyVoice TTS 服务
            connectToCosyVoice(session, attractionId);
            //为当前用户创建一个专属的单线程排队执行器
            ttsExecutorMap.put(sid, Executors.newSingleThreadExecutor());

        } catch (Exception e) {
            log.error("会话初始化失败 sid={}", sid, e);
            throw new ServiceException("会话初始化失败");
        }
        log.info("所有初始化完成 sid={}", sid);
        sendJson(session,"allDone",null);
    }

    /**
     * 处理文本消息
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String sid = session.getId();
        ObjectNode node = (ObjectNode) objectMapper.readTree(message.getPayload());
        String type = node.get("type").asText();
        lastActiveTimeMap.put(sid, System.currentTimeMillis());
        switch (type) {
            case "micOff":
                closeNLS(sid);
                break;
            case "micOn":
                SpeechTranscriber oldTranscriber = transcriberMap.get(sid);
                if (oldTranscriber != null) {
                    try { oldTranscriber.close(); } catch(Exception e) {}
                }
                createTranscriber(sid, session);
                break;
            case "text":
                String wordText = node.get("text").asText();
                log.info("用户输入文本：{}", wordText);
                callAiWithImageAndText(session, wordText);
                break;
            case "photo":
                log.info("有图片");
                String base64Image = node.get("photo").asText();
                pendingImageMap.put(sid, base64Image);
                break;
            case "camera":
                String status = node.get("status").asText();
                if (status.equals("off")) {
                    pendingImageMap.remove(sid);
                }
                break;
            case "ping":
                try {
                    sendJson(session, "pong", null);
                } catch (Exception e) {
                    log.warn("发送 pong 响应失败 sid={}", sid, e);
                }
                break;
        }
    }

    /**
     * 调用 AI 服务
     * @param session
     * @param userText
     */
    private void callAiWithImageAndText(WebSocketSession session,String userText) {
        log.info("调用 AI 服务，用户输入：{}", userText);
        String sid = session.getId();
        Long userId = (Long) session.getAttributes().get("userId");
        Long attractionId = (Long) session.getAttributes().get("attractionId");
        WebSocketSession pySession = pythonSessionMap.get(sid);

        String conversationId = stringRedisTemplate.opsForValue().get(USER_CONVERSATION_KEY + userId);
        if (conversationId == null){
            log.warn("用户会话不存在，无法调用 AI 服务 sid={}", userId);
            sendErrorMessage(session, "对话记录不存在");
            return;
        }
        //获取动态提示词
        String prompt = getDynamicPrompt(userText, userId, attractionId);
        // 【新增】定义一个状态位，标记是否还在处理第一句话
        AtomicBoolean isFirstChunk = new AtomicBoolean(true);
        StringBuilder buffer = new StringBuilder();
        //动态添加用户提示词
        String base64Image = pendingImageMap.get(sid);
        if (base64Image == null){
            log.info("调用 DS 模型");
            dsGuideChatClient.prompt()
                    .user(u -> u.text(userText))
                    .system(prompt)
                    .advisors(a -> a
                            .param(ChatMemory.CONVERSATION_ID, conversationId))
                    .stream()
                    .content()
                    .subscribe(
                            delta -> {
                                buffer.append(delta);
                                String text = buffer.toString();
                                // 按标点切割，且保留标点（lookbehind）
                                String[] parts = text.split("(?<=[，。！？；,!?;])");

                                // 除最后一段外，全部是完整句子直接发送
                                for (int i = 0; i < parts.length - 1; i++) {
                                    String sentence = parts[i];
                                    sendJson(session, "aiOutput", sentence);

                                    ExecutorService executor = ttsExecutorMap.get(sid);
                                    if (executor != null && !executor.isShutdown()) {
                                        // 把耗时的 TTS 阻塞调用，扔进单线程池里排队执行！
                                        executor.submit(() -> {
                                            // 在这个异步线程里，你可以放心大胆地用 waitForComplete()，随便阻塞！
                                            // 因为它只阻塞当前用户的 TTS 队列，绝对不影响 WebFlux 大模型流，也不影响别人！
                                            synthesizeAndStream(sentence, session, pySession);
                                        });
                                    }
                                    log.info("aiOutput:{}", parts[i]);
                                }

                                // 最后一段：若以标点结尾也发送，否则留在 buffer 等待后续
                                String tail = parts[parts.length - 1];
                                if (tail.matches(".*[，。！？；,!?;]$")) {
                                    sendJson(session, "aiOutput", tail);
                                    ExecutorService executor = ttsExecutorMap.get(sid);
                                    if (executor != null && !executor.isShutdown()) {
                                        // 把耗时的 TTS 阻塞调用，扔进单线程池里排队执行！
                                        executor.submit(() -> {
                                            // 在这个异步线程里，你可以放心大胆地用 waitForComplete()，随便阻塞！
                                            // 因为它只阻塞当前用户的 TTS 队列，绝对不影响 WebFlux 大模型流，也不影响别人！
                                            synthesizeAndStream(tail, session, pySession);
                                        });
                                    }
                                    log.info("aiOutput:{}", tail);
                                    buffer.setLength(0);

                                } else {
                                    buffer.setLength(0);
                                    buffer.append(tail);
                                }
                            },
                            error -> {
                                log.error("调用 AI 服务失败", error);
                                sendErrorMessage(session, "调用 AI 服务失败");
                            },
                            () -> {log.info("调用 AI 服务完成");
                                if (!buffer.isEmpty()) {
                                    sendJson(session, "aiHuman", buffer.toString());
                                    buffer.setLength(0);
                                }
                                sendJson(session, "responseDone", null);
                            }
                    );
        }else{
            log.info("调用 VL 模型");
            pendingImageMap.remove(sid);
            byte[] imageBytes = Base64.getDecoder().decode(base64Image);
            Resource resource = new ByteArrayResource(imageBytes);
            vlGuideChatClient.prompt()
                    .user(u -> u.text(userText).media(MimeTypeUtils.IMAGE_PNG, resource))
                    .system(prompt)
                    .advisors(a -> a
                            .param(ChatMemory.CONVERSATION_ID, conversationId))
                    .stream()
                    .content()
                    .subscribe(
                            delta -> {
                                buffer.append(delta);
                                String text = buffer.toString();
                                // 按标点切割，且保留标点（lookbehind）
                                String[] parts = text.split("(?<=[，。！？；,!?;])");

                                // 除最后一段外，全部是完整句子直接发送
                                for (int i = 0; i < parts.length - 1; i++) {
                                    String sentence = parts[i];
                                    sendJson(session, "aiOutput", sentence);

                                    ExecutorService executor = ttsExecutorMap.get(sid);
                                    if (executor != null && !executor.isShutdown()) {
                                        // 把耗时的 TTS 阻塞调用，扔进单线程池里排队执行！
                                        executor.submit(() -> {
                                            // 在这个异步线程里，你可以放心大胆地用 waitForComplete()，随便阻塞！
                                            // 因为它只阻塞当前用户的 TTS 队列，绝对不影响 WebFlux 大模型流，也不影响别人！
                                            synthesizeAndStream(sentence, session, pySession);
                                        });
                                    }
                                    log.info("aiOutput:{}", parts[i]);
                                }

                                // 最后一段：若以标点结尾也发送，否则留在 buffer 等待后续
                                String tail = parts[parts.length - 1];
                                if (tail.matches(".*[，。！？；,!?;]$")) {
                                    sendJson(session, "aiOutput", tail);
                                    ExecutorService executor = ttsExecutorMap.get(sid);
                                    if (executor != null && !executor.isShutdown()) {
                                        // 把耗时的 TTS 阻塞调用，扔进单线程池里排队执行！
                                        executor.submit(() -> {
                                            // 在这个异步线程里，你可以放心大胆地用 waitForComplete()，随便阻塞！
                                            // 因为它只阻塞当前用户的 TTS 队列，绝对不影响 WebFlux 大模型流，也不影响别人！
                                            synthesizeAndStream(tail, session, pySession);
                                        });
                                    }
                                    log.info("aiOutput:{}", tail);
                                    buffer.setLength(0);

                                } else {
                                    buffer.setLength(0);
                                    buffer.append(tail);
                                }
                            },
                            error -> {
                                log.error("调用 AI 服务失败", error);
                                sendErrorMessage(session, "调用 AI 服务失败");
                            },
                            () -> {log.info("调用 AI 服务完成");
                                if (!buffer.isEmpty()) {
                                    sendJson(session, "aiOutput", buffer.toString());
                                    buffer.setLength(0);
                                }
                                sendJson(session, "responseDone", null);
                            }
                    );
        }

        //异步调用其他AI进行用户情感分析
        experienceAnalysisService.analyze(userText, userId, attractionId);
    }

    private String getDynamicPrompt(String userText, Long userId, Long attractionId) {
        //获取用户信息 TODO获取用户位置信息(经纬度和方向角)
        Map<String, Object> dynamicPrompt = stringRedisTemplate.<String, Object>opsForHash().entries(RedisConstants.USER_INFO_KEY + userId);
        dynamicPrompt.put("locationInfo","");
        dynamicPrompt.put("absoluteFact", "");
        dynamicPrompt.put("context", "");
        //检索热门问答
        List<Document> hotQuestions = vectorSearchService.searchSimilarQuestion(userText, attractionId,0.9);
        if (!hotQuestions.isEmpty()){
            log.info("hotQuestions:{}", hotQuestions);
            Document hitDoc = hotQuestions.getFirst();

            String faqId = hitDoc.getMetadata().get("faqId").toString();
            log.info("检索到的热门问题：{}", hotQuestions);
            //积分热门问题，用于热门问答展示
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String redisKey = HOT_FAQ_KEY + attractionId + ":" + today;
            stringRedisTemplate.opsForZSet().incrementScore(redisKey, faqId, 1);
            stringRedisTemplate.expire(redisKey, 2, TimeUnit.DAYS);
            //拿到热门回答
            String redisAnswer = stringRedisTemplate.opsForValue().get(HOT_ANSWER_KEY+ attractionId +":"+faqId);
            if (redisAnswer != null){
                log.info("redis检索到的绝对事实：{}", redisAnswer);
                dynamicPrompt.put("absoluteFact", redisAnswer);
            }else {
                String mysqlAnswer = userAttractionsInternalService.getAbsoluteFactByQuestionId(faqId);
                log.info("mysql检索到的绝对事实：{}", mysqlAnswer);
                dynamicPrompt.put("absoluteFact", mysqlAnswer);
                stringRedisTemplate.opsForValue().set(HOT_ANSWER_KEY+ attractionId +":"+faqId, mysqlAnswer, RedisConstants.HOT_ANSWER_EXPIRE_TIME, TimeUnit.HOURS);
            }

        }else {
            //检索RAG
            List<Document> documents = vectorSearchService.searchDocByAttraction(userText, attractionId, 5);
            String context = documents.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n"));
            log.info("检索到的文档数量：{}, 内容：{}", documents.size(), context);
            dynamicPrompt.put("context", context);
            Long size = stringRedisTemplate.opsForSet().size(HOT_QUESTION_KEY+ attractionId);
            //如果样本数小于2000且用户输入长度大于2小于100，则将用户输入加入待学习库
            if (size == null ||size < 2000 && userText.length() > 2 && userText.length() <= 100) {
                stringRedisTemplate.opsForSet().add(HOT_QUESTION_KEY+ attractionId, userText);
                stringRedisTemplate.expire(HOT_QUESTION_KEY+ attractionId, 48, TimeUnit.HOURS);
                log.info("已将未命中问题加入待学习库，当前样本数: {}", (size == null ? 1 : size + 1));
            }
        }
        //拼接提示词，增加用户信息，方便个性化推荐或讲解
        PromptTemplate promptTemplate = new PromptTemplate(AiSystemConstants.AI_GUIDE_SYSTEM_PROMPT);
        return promptTemplate.render(dynamicPrompt);
    }

    /**
     * 处理二进制消息
     */
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        String sid = session.getId();
        byte[] audio = message.getPayload().array();
        long sum = 0;
        for (int i = 0; i < audio.length - 1; i += 2) {
            short s = (short) ((audio[i + 1] << 8) | (audio[i] & 0xFF));
            sum += (long) s * s;

        }
        double rms = Math.sqrt((double) sum / ((double) audio.length / 2));
        SpeechTranscriber transcriber = transcriberMap.get(sid);
        if (transcriber == null) {
            // 声音小（环境音），且没有连接，直接丢弃
            if (rms < VOCAL_THRESHOLD) {
                return;
            }
            // 如果音量突然变大，说明用户开始说话了！
            // 立刻重建连接
            createTranscriber(sid, session);
            // 将这段带有有效声音的音频缓冲起来，等 onTranscriberStart 触发后再发进去，防止吞字
            audioBufferMap.computeIfAbsent(sid, k -> new AudioFrameBuffer()).offer(audio);
            return;
        }
        lastAudioTimeMap.put(sid, System.currentTimeMillis());

        transcriber.send(audio);
    }

    /**
     * 连接关闭后的逻辑
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("连接关闭 sid={}, 状态={}", session.getId(), status);
        cleanup(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WebSocket 发生错误，异常断开连接, sid={}", session.getId(), exception);
        cleanup(session.getId());
        sendJson(session, "connectionError", "连接发生错误");
    }

    public void cleanup(String sid) {
        sessionMap.remove(sid);
        pendingImageMap.remove(sid);
        lastActiveTimeMap.remove(sid);
        lastAudioTimeMap.remove(sid);
        WebSocketSession pythonSession = pythonSessionMap.remove(sid);
        if (pythonSession != null && pythonSession.isOpen()) {
            try { pythonSession.close(); } catch (IOException e) { log.warn("关闭 MuseTalk session 失败 sid={}", sid, e); }
        }
        WebSocketSession cosySession = cosyVoiceSessionMap.remove(sid);
        if (cosySession != null && cosySession.isOpen()) {
            try { cosySession.close(); } catch (IOException e) { log.warn("关闭 CosyVoice session 失败 sid={}", sid, e); }
        }
        sendLocks.remove(sid);
        closeNLS(sid);
        log.info("连接关闭 sid={}, 剩余在线={}", sid, sessionMap.size());
    }


    private void closeNLS(String sid) {
        SpeechTranscriber transcriber = transcriberMap.remove(sid);
        audioBufferMap.remove(sid);
        //关闭TTS专属线程池
        ExecutorService ttsExecutor = ttsExecutorMap.remove(sid);
        if (ttsExecutor != null && !ttsExecutor.isShutdown()) {
            ttsExecutor.shutdownNow();
        }
        // 关闭 NLS 保活器
        if (transcriber != null) {
            try { transcriber.close(); } catch (Exception e) { log.warn("关闭 NLS 失败", e); }
        }
    }

    private void createTranscriber(String sid , WebSocketSession session) {

        try {
            SpeechTranscriber transcriber = new SpeechTranscriber(nlsClient, new SpeechTranscriberListener() {
                @Override
                public void onTranscriberStart(SpeechTranscriberResponse speechTranscriberResponse) {
                    log.info("NLS 开始识别 sid={}", sid);
                    AudioFrameBuffer buffer = audioBufferMap.remove(sid);
                    if (buffer != null) {
                        SpeechTranscriber t = transcriberMap.get(sid);
                        if (t != null) {
                            buffer.drainTo(t::send);
                        }
                    }
                }

                @Override
                public void onSentenceBegin(SpeechTranscriberResponse speechTranscriberResponse) {
                    log.info("NLS 一句话开始 sid={}", sid);
                    sendJson(session, "speechStarted", null);
                }

                //一句话结束
                @Override
                public void onSentenceEnd(SpeechTranscriberResponse speechTranscriberResponse) {
                    String text = speechTranscriberResponse.getTransSentenceText();
                    log.info("一句话结束，文本：{}", text);
                    if (text != null && !text.isEmpty()) {
                        sendJson(session, "userInput", text);
                        callAiWithImageAndText(session, text);
                    }
                }

                //实时语音转文字
                @Override
                public void onTranscriptionResultChange(SpeechTranscriberResponse speechTranscriberResponse) {
                    sendJson(session, "interimText", speechTranscriberResponse.getTransSentenceText());
                }

                @Override
                public void onTranscriptionComplete(SpeechTranscriberResponse speechTranscriberResponse) {
                    log.info("NLS 识别完成 sid={}", sid);
                }

                @Override
                public void onFail(SpeechTranscriberResponse speechTranscriberResponse) {
                    log.warn("NLS 连接断开 sid={} status={} msg={}",
                            sid, speechTranscriberResponse.getStatus(), speechTranscriberResponse.getStatusText());
                    transcriberMap.remove(sid);

                }
            });
            transcriber.setAppKey(nlsAppKey);
            transcriber.setFormat(InputFormatEnum.PCM);
            transcriber.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
            transcriber.setEnableIntermediateResult(true);  // 开启中间结果（实时字幕用）
            transcriber.setEnablePunctuation(true);          // 开启标点
            transcriber.setEnableITN(true);                  // 数字规范化
            transcriberMap.put(sid, transcriber);
            transcriber.start(); // 开始识别，等待音频流
        } catch (Exception e) {
            log.error("创建 NLS transcriber 失败 sid={}", sid, e);
            sendErrorMessage(session, "语音服务初始化失败");
        }

    }

    /**
     * 把一句话直接扔给 CosyVoice TTS 服务，不等回包；
     * Python 内部按队列顺序合成，PCM 流由 connectToCosyVoice 注册的 handler
     * 转发给 Android（加 0x01 头）和 MuseTalk（原始 PCM + audio_end）。
     *
     * @param text          大模型生成的单句文本
     * @param androidSession 安卓端的 WebSocket 连接（用于定位 sid）
     * @param pythonSession  Python端的 WebSocket 连接（保留参数，签名兼容）
     */
    public void synthesizeAndStream(String text, WebSocketSession androidSession, WebSocketSession pythonSession) {
        if (text == null || text.isBlank()) {
            return;
        }
        String sid = androidSession.getId();
        WebSocketSession cosySession = cosyVoiceSessionMap.get(sid);
        if (cosySession == null || !cosySession.isOpen()) {
            log.warn("CosyVoice session not ready sid={}", sid);
            return;
        }
        Long attractionId = (Long) androidSession.getAttributes().get("attractionId");
        ObjectNode req = objectMapper.createObjectNode();
        req.put("type", "synthesize");
        req.put("text", text);
        if (attractionId != null) {
            req.put("attraction_id", attractionId.toString());
        }
        req.put("session_id", sid);
        try {
            cosySession.sendMessage(new TextMessage(req.toString()));
        } catch (IOException e) {
            log.error("发送 synthesize 请求到 CosyVoice 失败 sid={}", sid, e);
        }
    }

    /**
     * 连接 CosyVoice TTS 服务。和 connectToPythonMuseTalk 同模式：
     * - afterConnectionEstablished 发 init 握手并把 session 存入 cosyVoiceSessionMap
     * - handleBinaryMessage：PCM chunk 加 0x01 头转 Android，同时 append 到当前句的 pcmBuffer
     * - handleTextMessage：chunk_end 时把 pcmBuffer 全量 PCM 转给 MuseTalk，再发 audio_end，清空 buffer
     */
    public void connectToCosyVoice(WebSocketSession androidSession, Long attractionId) {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxBinaryMessageBufferSize(1024 * 1024);
        container.setDefaultMaxTextMessageBufferSize(1024 * 1024);

        StandardWebSocketClient client = new StandardWebSocketClient(container);
        String sid = androidSession.getId();
        // 当前句的 PCM 缓冲区，chunk_end 时整体发给 MuseTalk
        ByteArrayOutputStream pcmBuffer = new ByteArrayOutputStream();

        try {
            WebSocketSession cosySession = client.execute(new AbstractWebSocketHandler() {

                @Override
                public void afterConnectionEstablished(WebSocketSession cosySession) throws Exception {
                    log.info("[CosyVoice WS] 已连接 sid={} attractionId={}", sid, attractionId);
                    String initJson = objectMapper.createObjectNode()
                            .put("type", "init")
                            .put("attraction_id", attractionId.toString())
                            .put("session_id", sid)
                            .toString();
                    cosySession.sendMessage(new TextMessage(initJson));
                }

                @Override
                protected void handleTextMessage(WebSocketSession cosySession, TextMessage message) throws Exception {
                    ObjectNode node = (ObjectNode) objectMapper.readTree(message.getPayload());
                    String type = node.has("type") ? node.get("type").asText() : "";
                    switch (type) {
                        case "ping":
                            cosySession.sendMessage(new TextMessage(
                                    objectMapper.createObjectNode().put("type", "pong").toString()));
                            log.debug("[CosyVoice WS] ← ping，已回 pong");
                            break;

                        case "chunk_end":
                            byte[] sentencePcm;
                            synchronized (pcmBuffer) {
                                sentencePcm = pcmBuffer.toByteArray();
                                pcmBuffer.reset();
                            }
                            WebSocketSession pythonSession = pythonSessionMap.get(sid);
                            if (pythonSession != null && pythonSession.isOpen()) {
                                if (sentencePcm.length > 0) {
                                    try {
                                        pythonSession.sendMessage(new BinaryMessage(sentencePcm));
                                    } catch (IOException e) {
                                        log.error("发送 PCM 到 MuseTalk 失败 sid={}", sid, e);
                                    }
                                }
                                try {
                                    pythonSession.sendMessage(new TextMessage(
                                            objectMapper.createObjectNode().put("type", "audio_end").toString()));
                                } catch (IOException e) {
                                    log.error("发送 audio_end 到 MuseTalk 失败 sid={}", sid, e);
                                }
                            }
                            break;

                        case "error":
                            String errMsg = node.has("message") ? node.get("message").asText() : "unknown";
                            log.error("[CosyVoice WS] 报错：{}", errMsg);
                            break;

                        default:
                            log.warn("[CosyVoice WS] 收到未知类型消息: {}", type);
                    }
                }

                @Override
                protected void handleBinaryMessage(WebSocketSession cosySession, BinaryMessage message) throws Exception {
                    byte[] rawPcm = message.getPayload().array();
                    // 累积当前句 PCM，等 chunk_end 整段发给 MuseTalk
                    synchronized (pcmBuffer) {
                        pcmBuffer.write(rawPcm);
                    }
                    // 实时加 0x01 头转给 Android
                    if (androidSession.isOpen()) {
                        byte[] androidPayload = new byte[rawPcm.length + 1];
                        androidPayload[0] = 0x01;
                        System.arraycopy(rawPcm, 0, androidPayload, 1, rawPcm.length);
                        safeSend(androidSession, new BinaryMessage(androidPayload));
                    }
                }

            }, cosyVoiceWsUrl).get();
            cosyVoiceSessionMap.put(sid, cosySession);
        } catch (Exception e) {
            log.error("连接 CosyVoice TTS 服务失败！url={}", cosyVoiceWsUrl, e);
        }
    }

    /**
     * 连接 Python 炼丹炉，进行数字人生成
     * @param androidSession
     * @param attractionId
     */
    public void connectToPythonMuseTalk(WebSocketSession androidSession, Long attractionId) {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        // 2. 把二进制和文本缓冲区的最大限制调到 512KB（足够放极高画质的图片了）
        container.setDefaultMaxBinaryMessageBufferSize(1024 * 1024);
        container.setDefaultMaxTextMessageBufferSize(1024 * 1024);


        StandardWebSocketClient client = new StandardWebSocketClient(container);

        String sid = androidSession.getId();

        try {
            // 主动拨打 Python 的电话
            WebSocketSession pythonSession = client.execute(new AbstractWebSocketHandler() {

                // 1. 电话刚接通，赶紧发送 init 握手协议
                @Override
                public void afterConnectionEstablished(WebSocketSession pythonSession) throws Exception {
                    log.info("建立连接{}",attractionId);
                    String initJson = objectMapper.createObjectNode()
                            .put("type", "init")
                            .put("attraction_id", attractionId.toString())
                            .toString();
                    pythonSession.sendMessage(new TextMessage(initJson));
                }

                // 2. 接收 Python 发来的文字消息 (比如 {"type": "ready"} 或 {"type": "done"})
                @Override
                protected void handleTextMessage(WebSocketSession pythonSession, TextMessage message) throws Exception {
                    ObjectNode node = (ObjectNode) objectMapper.readTree(message.getPayload());
                    String type = node.get("type").asText();

                    switch (type) {
                        case "ping":
                            // 回 pong，保持和 Python 的心跳
                            pythonSession.sendMessage(new TextMessage(
                                    objectMapper.createObjectNode().put("type", "pong").toString()));
                            log.debug("[Python WS] ← ping，已回 pong");
                            break;

                        case "ready":
                            log.info("[Python WS] 准备好了，通知 Android");
                            sendJson(androidSession, "ready", null);
                            break;

                        case "done":
                            log.info("[Python WS] 这句话生成完毕，通知 Android");
                            sendJson(androidSession, "done", null);
                            break;

                        case "error":
                            String errMsg = node.get("message").asText();
                            log.error("[Python WS] 报错：{}", errMsg);
                            break;

                        default:
                            log.warn("[Python WS] 收到未知类型消息: {}", node.get("type").asText());
                    }
                }

                // 3. 【核心透传逻辑】接收 Python 发来的纯二进制视频帧！
                @Override
                protected void handleBinaryMessage(WebSocketSession pythonSession, BinaryMessage message) throws Exception {
                    if (androidSession.isOpen()) {

                        // 1. 获取 Python 传过来的原始 JPEG 视频帧数据
                        byte[] rawVideoBytes = message.getPayload().array();

                        // ==========================================
                        // 【新增包装逻辑】为 Android 专门打造带 0x02 标识的包
                        // ==========================================
                        byte[] androidPayload = new byte[rawVideoBytes.length + 1];
                        // 强制把第 0 个字节设置为 0x02（代表视频帧）
                        androidPayload[0] = 0x02;
                        // 把原始视频数据拼接在后面
                        System.arraycopy(rawVideoBytes, 0, androidPayload, 1, rawVideoBytes.length);
                        // 发送给 Android
                        safeSend(androidSession, new BinaryMessage(androidPayload));
                    }
                }

            }, pythonWsUrl).get(); // .get() 会阻塞直到连接成功
            pythonSessionMap.put(sid, pythonSession);
        } catch (Exception e) {
            log.error("连接 Python 炼丹炉失败！", e);
        }
    }



    private void sendJson(WebSocketSession session, String type, String text) {
        try {
            if (session.isOpen()) {
                ObjectNode resp = objectMapper.createObjectNode();
                resp.put("type", type);
                if (text != null) {
                    resp.put("text", text);
                }
                safeSend(session, new TextMessage(resp.toString()));
            }
        } catch (Exception e) {
            log.error("发送消息失败", e);
        }
    }

    private void safeSend(WebSocketSession session, WebSocketMessage<?> message) {
        ReentrantLock lock = sendLocks.get(session.getId());
        if (lock == null || !session.isOpen()) return;
        lock.lock();
        try {
            if (session.isOpen()) {
                session.sendMessage(message);
            }
        } catch (IOException e) {
            log.error("WebSocket发送失败 sessionId={}", session.getId(), e);
        } finally {
            lock.unlock();
        }
    }


    private void sendErrorMessage(WebSocketSession session, String msg) {
        ObjectNode error = objectMapper.createObjectNode();
        error.put("type", "error");
        error.put("text", msg);
        try {
            session.sendMessage(new TextMessage(error.toString()));
        } catch (IOException ignored) {}
    }

    public Map<String, WebSocketSession> getSessionMap() { return sessionMap; }
    public Map<String, Long> getLastActiveTimeMap() { return lastActiveTimeMap; }


}