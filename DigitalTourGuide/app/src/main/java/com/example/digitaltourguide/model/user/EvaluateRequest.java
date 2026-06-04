package com.example.digitaltourguide.model.user;

public class EvaluateRequest {
    private String conversationId;
    private int score;
    private String feedbackText;

    public EvaluateRequest(String conversationId, int score, String feedbackText) {
        this.conversationId = conversationId;
        this.score = score;
        this.feedbackText = feedbackText;
    }
    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getFeedbackText() {
        return feedbackText;
    }

    public void setFeedbackText(String feedbackText) {
        this.feedbackText = feedbackText;
    }
}
