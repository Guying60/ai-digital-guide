package com.example.digitaltourguide.model.admin;

public class AddAttractionRequest {
    private String id;
    private String coverUrl;
    private String attractionName;
    private int type;
    private Double rating;
    private Integer reviewCount;
    private String openHours;

    // 构造函数（新增用，不带id）
    public AddAttractionRequest(String coverUrl, String attractionName, int type) {
        this.coverUrl = coverUrl;
        this.attractionName = attractionName;
        this.type = type;
    }

    public String getCoverUrl() {
        return coverUrl;
    }
    // 构造函数（更新用，带id）
    public AddAttractionRequest(String id, String coverUrl, String attractionName, int type) {
        this.id = id;
        this.coverUrl = coverUrl;
        this.attractionName = attractionName;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public String getAttractionName() {
        return attractionName;
    }

    public void setAttractionName(String attractionName) {
        this.attractionName = attractionName;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public Integer getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(Integer reviewCount) {
        this.reviewCount = reviewCount;
    }

    public String getOpenHours() {
        return openHours;
    }

    public void setOpenHours(String openHours) {
        this.openHours = openHours;
    }
}
