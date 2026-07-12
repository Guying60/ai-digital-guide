package com.guying.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserTourHistoryPageVO {
    private Long id;
    private String attractionName;
    private String coverUrl;
    private String conversationId;
    private String city;
    /** 对话消息条数（用户提问 + AI 回复） */
    private Integer messageCount;
    /** 上次对话时间（会话结束落库时间） */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastChatTime;
}
