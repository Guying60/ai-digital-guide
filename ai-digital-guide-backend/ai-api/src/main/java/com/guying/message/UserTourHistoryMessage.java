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
    /** 会话状态：0-进行中 1-已结束 2-已评价 */
    private Integer tourStatus;
}
