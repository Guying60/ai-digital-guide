package com.guying.service.Impl;

import com.guying.pojo.vo.MessageVO;
import com.guying.service.AiChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class AiChatHistoryServiceImpl  implements AiChatHistoryService {
    @Autowired
    private JdbcChatMemoryRepository chatMemoryRepository;

    @Override
    public List<MessageVO> getChatHistory(String conversationId) {
        log.info("Getting chat history for conversationId: {}", conversationId);
        List<Message> messages = chatMemoryRepository.findByConversationId(conversationId);
        return messages.stream().map(MessageVO::new).toList();
    }

    @Override
    public void deleteChatHistory(String conversationId) {
        chatMemoryRepository.deleteByConversationId(conversationId);
    }
}
