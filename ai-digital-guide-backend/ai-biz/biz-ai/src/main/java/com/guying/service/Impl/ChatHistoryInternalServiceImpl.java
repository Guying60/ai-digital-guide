package com.guying.service.Impl;

import com.guying.ai.service.ChatHistoryInternalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * ChatHistoryInternalService 实现：转发到 JdbcChatMemoryRepository。
 * 供 biz-user 等外部模块查询/清理对话记录，参照 FaceEmotionInternalServiceImpl。
 */
@Service
@Slf4j
public class ChatHistoryInternalServiceImpl implements ChatHistoryInternalService {

    @Autowired
    private JdbcChatMemoryRepository chatMemoryRepository;

    @Override
    public boolean hasMessages(String conversationId) {
        return !chatMemoryRepository.findByConversationId(conversationId).isEmpty();
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        chatMemoryRepository.deleteByConversationId(conversationId);
    }
}
