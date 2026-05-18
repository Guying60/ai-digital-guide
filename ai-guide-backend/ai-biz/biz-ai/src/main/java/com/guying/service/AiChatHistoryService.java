package com.guying.service;


import com.guying.pojo.vo.MessageVO;

import java.util.List;

public interface AiChatHistoryService  {
    List<MessageVO> getChatHistory(String conversationId);

    void deleteChatHistory(String conversationId);


}
