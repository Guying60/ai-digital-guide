package com.example.digitaltourguide.model.user;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * 1.14 提交评价 请求体
 */
public class SubmitReviewRequest {

    @SerializedName("reviewId")
    private String reviewId;

    @SerializedName("rating")
    private double rating;

    @SerializedName("content")
    private String content;

    @SerializedName("tags")
    private List<String> tags;

    public SubmitReviewRequest(String reviewId, double rating, String content, List<String> tags) {
        this.reviewId = reviewId;
        this.rating = rating;
        this.content = content;
        this.tags = tags;
    }

    public String getReviewId() { return reviewId; }
    public void setReviewId(String reviewId) { this.reviewId = reviewId; }

    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}