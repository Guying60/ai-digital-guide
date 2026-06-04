package com.example.digitaltourguide.model.user;

public class ChatMessage {
    private String content;
    private boolean isUser;

    public ChatMessage(String content, boolean isUser) {
        this.content = content;
        this.isUser = isUser;
    }

    public String getContent() {
        return content;
    }
    public void setContent(String content) { this.content = content; }

    public boolean isUser() {
        return isUser;
    }
}
