package com.guying.message;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserTourHistoryMessage {

    private Long userId;
    private Long attractionId;
    private String conversationId;
    /** 对话消息条数（用户提问 + AI 回复） */
    private Integer messageCount;
}
