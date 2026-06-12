package com.example.digitaltourguide.model.user;

import java.util.List;

/**
 * 评价页面的一条景点评价数据
 */
public class ReviewItem {

    private String placeName;        // 景点名称
    private String status;           // "已评价" 或 "待评价"
    private String date;             // 游览日期
    private float rating;            // 评分 0-5（已评价才有）
    private String reviewText;       // 评价内容（已评价才有）
    private List<String> tags;       // 评价标签（已评价才有）
    private int placeImageRes;       // 景点图片资源 ID（示例用）
    private boolean reviewed;        // 是否已评价

    public ReviewItem(String placeName, String date, float rating,
                      String reviewText, List<String> tags, int placeImageRes) {
        this.placeName = placeName;
        this.date = date;
        this.rating = rating;
        this.reviewText = reviewText;
        this.tags = tags;
        this.placeImageRes = placeImageRes;
        this.reviewed = true;
        this.status = "已评价";
    }

    public ReviewItem(String placeName, String date, int placeImageRes) {
        this.placeName = placeName;
        this.date = date;
        this.placeImageRes = placeImageRes;
        this.reviewed = false;
        this.status = "待评价";
        this.rating = 0;
    }

    // ── getters ──

    public String getPlaceName() { return placeName; }
    public String getStatus() { return status; }
    public String getDate() { return date; }
    public float getRating() { return rating; }
    public String getReviewText() { return reviewText; }
    public List<String> getTags() { return tags; }
    public int getPlaceImageRes() { return placeImageRes; }
    public boolean isReviewed() { return reviewed; }
}