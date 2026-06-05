package com.guying.controller;

import com.guying.common.result.Result;
import com.guying.pojo.vo.MessageVO;
import com.guying.service.AiChatHistoryService;
import com.guying.task.FaqEvolutionTask;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Slf4j
@Tag(name = "聊天历史管理")
@RequestMapping("/v1/users/chat-history")
public class AiChatHistoryController {
    @Autowired
    private AiChatHistoryService aiChatHistoryService;

    @Autowired
    private FaqEvolutionTask faqEvolutionTask;

    @Operation(summary = "获取聊天历史")
    @GetMapping("/{conversationId}")
    public Result<List<MessageVO>> getChatHistory(@PathVariable String conversationId){
        return Result.success(aiChatHistoryService.getChatHistory(conversationId));
    }

    @Operation(summary = "删除聊天历史")
    @DeleteMapping("/{conversationId}")
    public Result deleteChatHistory(@PathVariable String conversationId){

        aiChatHistoryService.deleteChatHistory(conversationId);
        return Result.success();
    }

}
