package com.guying.config;

import com.guying.prompt.AiSystemConstants;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;


@Configuration
public class AiConfiguration {
    @Autowired
    JdbcChatMemoryRepository chatMemoryRepository;

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(10)
                .build();
    }


    @Bean("vlGuideChatClient")
    public ChatClient vlGuideChatClient(OpenAiChatModel openAiChatModel, ChatMemory chatMemory, ToolCallbackProvider toolCallbackProvider) {
        return ChatClient.builder(openAiChatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultToolCallbacks(toolCallbackProvider.getToolCallbacks())
                .defaultOptions(OpenAiChatOptions.builder()
                        .extraBody(Map.of("enable_thinking", false))
                        .build())
                .build();
    }

    /**
     * 文本处理 ChatClient：FAQ 标准问生成、聚类分类等非结构化文本处理任务。
     * 模型：qwen3.7-max（质量优先，低温度保证输出稳定性）。
     */
    @Bean("etlChatClient")
    public ChatClient etlChatClient(ChatClient.Builder builder) {
        return builder
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("qwen3.7-max")
                        .temperature(0.1)
                        .build())
                .build();
    }

    /**
     * 结构化输出 ChatClient：路线推荐、体验/情感分析、FAQ 聚类评分等需要稳定 JSON 结构的任务。
     * 模型：qwen3.7-max（强指令遵循，低温度配合 BeanOutputConverter 输出稳定 JSON）。
     */
    @Bean("expertChatClient")
    public ChatClient expertChatClient(ChatClient.Builder builder) {
        return builder
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("qwen3.7-max")
                        .temperature(0.1)
                        .build())
                .build();
    }

    /**
     * 面部表情情感分类专用视觉 ChatClient：
     *  - 复用 DashScope（OpenAI 兼容）的 OpenAiChatModel，通过 model 指定千问全模态模型（支持图像输入）；
     *  - 无状态分类：不挂 chat memory、不挂 tool callbacks，避免污染对话记忆 / 误触 MCP 工具；
     *  - 低温度 0.2，关闭 thinking，配合 BeanOutputConverter 稳定输出 JSON。
     * 模型名由 spring.ai.emotion.vision-model 配置，默认 qwen3.7-plus。
     */
    @Bean("emotionVisionChatClient")
    public ChatClient emotionVisionChatClient(ChatClient.Builder builder,
                                              @Value("${spring.ai.emotion.vision-model:qwen3.7-plus}") String visionModel) {
        return builder
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(visionModel)
                        .temperature(0.2)
                        .extraBody(Map.of("enable_thinking", false))
                        .build())
                .build();
    }

    /**
     * WebSocket 主线对话 ChatClient：AI 讲解员实时文本问答。
     * 模型：qwen3.6-flash（低延迟优先，配合 sentence-by-sentence 流式输出）。
     */
    @Bean("llmGuideChatClient")
    public ChatClient llmGuideChatClient(ChatClient.Builder builder, ChatMemory chatMemory,ToolCallbackProvider toolCallbackProvider) {
        return builder
                .defaultToolCallbacks(toolCallbackProvider.getToolCallbacks())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("qwen3.6-flash")
                        .temperature(0.8)
                        .extraBody(Map.of("enable_thinking", false))
                        .build())
                .build();
    }


}
