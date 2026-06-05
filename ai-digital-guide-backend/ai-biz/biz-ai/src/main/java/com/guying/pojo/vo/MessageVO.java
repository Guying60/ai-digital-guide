package com.guying.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.messages.Message;

@Data
@NoArgsConstructor
public class MessageVO {
    private String role;
    private String content;

    public MessageVO(Message message) {
        this.role = message.getMessageType().getValue(); // "user" / "assistant" / "system"
        this.content = message.getText();
    }
}
