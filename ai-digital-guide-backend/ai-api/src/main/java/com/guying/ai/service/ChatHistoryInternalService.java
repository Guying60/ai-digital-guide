package com.guying.ai.service;

/**
 * 聊天历史内部服务接口（依赖倒置层）。
 * biz-ai 实现，biz-user 等外部模块通过本接口查询/清理对话记录，避免 biz 模块间循环依赖。
 * 参照 FaceEmotionInternalService 的设计。
 */
public interface ChatHistoryInternalService {

    /**
     * 判断指定会话是否存在聊天记录
     *
     * @param conversationId 会话ID
     * @return true=有消息，false=无消息（零交互）
     */
    boolean hasMessages(String conversationId);

    /**
     * 按会话ID删除聊天记录
     *
     * @param conversationId 会话ID
     */
    void deleteByConversationId(String conversationId);
}
