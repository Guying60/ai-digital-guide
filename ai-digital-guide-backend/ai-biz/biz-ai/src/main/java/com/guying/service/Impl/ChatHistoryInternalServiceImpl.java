package com.guying.service.Impl;

import com.guying.ai.service.ChatHistoryInternalService;
import com.guying.websocket.session.ChatSessionContext;
import com.guying.websocket.session.ChatSessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

/**
 * ChatHistoryInternalService 实现：转发到 JdbcChatMemoryRepository。
 * 供 biz-user 等外部模块查询/清理对话记录，参照 FaceEmotionInternalServiceImpl。
 */
@Service
@Slf4j
public class ChatHistoryInternalServiceImpl implements ChatHistoryInternalService {

    @Autowired
    private JdbcChatMemoryRepository chatMemoryRepository;

    @Autowired
    private ChatSessionRegistry chatSessionRegistry;

    @Override
    public boolean hasMessages(String conversationId) {
        return !chatMemoryRepository.findByConversationId(conversationId).isEmpty();
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        chatMemoryRepository.deleteByConversationId(conversationId);
    }

    @Override
    public Integer getLiveQuestionCount(String conversationId) {
        ChatSessionContext ctx = chatSessionRegistry.getByConversationId(conversationId);
        return ctx == null ? null : ctx.getQuestionCount();
    }

    @Override
    public void endLiveSession(String conversationId, boolean tourHistoryDeleted) {
        ChatSessionContext ctx = chatSessionRegistry.getByConversationId(conversationId);
        if (ctx == null) {
            return;
        }
        if (tourHistoryDeleted) {
            ctx.markTourHistoryDeleted();
        }
        WebSocketSession session = ctx.getUserSession();
        if (session != null && session.isOpen()) {
            try {
                // 关闭后触发 AiChatHandler.afterConnectionClosed → cleanup，完成资源清理
                session.close(CloseStatus.NORMAL);
            } catch (IOException e) {
                log.warn("结束对话时关闭 WebSocket 失败 conversationId={}", conversationId, e);
            }
        }
    }
}
