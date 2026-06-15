package com.example.digitaltourguide.model.user;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * 1.13 获取我的评价列表 响应实体
 */
public class UserReviewItem {

    @SerializedName("id")
    private String id;

    @SerializedName("attractionId")
    private String attractionId;

    @SerializedName("attractionName")
    private String attractionName;

    @SerializedName("coverUrl")
    private String coverUrl;

    @SerializedName("rating")
    private Double rating;

    @SerializedName("content")
    private String content;

    @SerializedName("tags")
    private List<String> tags;

    @SerializedName("status")
    private int status; // 0=待评价, 1=已评价

    @SerializedName("createTime")
    private String createTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAttractionId() { return attractionId; }
    public void setAttractionId(String attractionId) { this.attractionId = attractionId; }

    public String getAttractionName() { return attractionName; }
    public void setAttractionName(String attractionName) { this.attractionName = attractionName; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }

    /** 是否已评价 */
    public boolean isReviewed() { return status == 1; }
}
