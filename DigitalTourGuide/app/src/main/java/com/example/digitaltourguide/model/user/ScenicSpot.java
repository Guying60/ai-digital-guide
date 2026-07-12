package com.example.digitaltourguide.model.user;

import com.google.gson.annotations.SerializedName;

public class ScenicSpot {
    //用户端
    @SerializedName("id")
    private String id;
    @SerializedName("attractionName")
   private String title;
    @SerializedName("coverUrl")
    private String coverUrl;
    @SerializedName("conversationId")
    private String conversationId;
    @SerializedName("distance")
    private Double distance;

    @SerializedName("city")
    private String city;

    @SerializedName("longitude")
    private Double longitude;

    @SerializedName("latitude")
    private Double latitude;

    @SerializedName("ScenicSpot")
    private boolean ended;

    @SerializedName("rating")
    private Double rating;

    @SerializedName("reviewCount")
    private Integer reviewCount;

    @SerializedName("messageCount")
    private Integer messageCount;

    /** 上次对话时间，格式 yyyy-MM-dd'T'HH:mm:ss */
    @SerializedName("lastChatTime")
    private String lastChatTime;


    public ScenicSpot() {}

    public Double getDistance() { return distance; }
    public void setDistance(Double distance) { this.distance = distance; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public boolean isEnded() {
        return ended;
    }

    public void setEnded(boolean ended) {
        this.ended = ended;
    }

    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }

    public Integer getReviewCount() { return reviewCount; }
    public void setReviewCount(Integer reviewCount) { this.reviewCount = reviewCount; }

    public Integer getMessageCount() { return messageCount; }
    public void setMessageCount(Integer messageCount) { this.messageCount = messageCount; }

    public String getLastChatTime() { return lastChatTime; }
    public void setLastChatTime(String lastChatTime) { this.lastChatTime = lastChatTime; }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ScenicSpot(String title, String coverUrl) {
        this.title = title;
        this.coverUrl = coverUrl;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

}
