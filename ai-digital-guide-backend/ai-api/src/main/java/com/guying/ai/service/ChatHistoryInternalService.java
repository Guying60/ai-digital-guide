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

    /**
     * 查询指定会话在 WebSocket 存活期间的用户提问次数。
     * 该值由内存中的会话上下文实时维护，是判定「是否有真实交互」的权威信号，
     * 不依赖聊天记录表的异步落库，避免与前端 fire-and-forget 的结束调用产生竞态。
     *
     * @param conversationId 会话ID
     * @return 存活会话的提问次数；若无对应的存活会话则返回 null
     */
    Integer getLiveQuestionCount(String conversationId);

    /**
     * 结束并关闭指定会话仍存活的 WebSocket 连接（如返回主页时被保活的会话）。
     * 供 HTTP /end 结束对话时同步终止后台会话；无存活会话则无操作。
     *
     * @param conversationId    会话ID
     * @param tourHistoryDeleted /end 是否已按零交互删除了该会话的游览历史；
     *                           为 true 时标记会话，令 WS 断开的 cleanup 跳过持久化，避免重建已删记录
     */
    void endLiveSession(String conversationId, boolean tourHistoryDeleted);
}
