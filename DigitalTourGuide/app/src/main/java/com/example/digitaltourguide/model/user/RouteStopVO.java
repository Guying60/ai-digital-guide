package com.example.digitaltourguide.model.user;

import com.google.gson.annotations.SerializedName;

/**
 * 1.16.1 路线节点 — stops[i]
 */
public class RouteStopVO {

    @SerializedName("stopIndex")
    private int stopIndex;

    @SerializedName("name")
    private String name;

    @SerializedName("searchKeyword")
    private String searchKeyword;

    @SerializedName("recommendReason")
    private String recommendReason;

    @SerializedName("estimatedMinutes")
    private int estimatedMinutes;

    @SerializedName("longitude")
    private Double longitude;

    @SerializedName("latitude")
    private Double latitude;

    @SerializedName("address")
    private String address;

    @SerializedName("poiId")
    private String poiId;

    @SerializedName("resolved")
    private boolean resolved;

    @SerializedName("status")
    private String status; // UPCOMING / CURRENT / ARRIVED

    // ====================== getters & setters ======================

    public int getStopIndex() { return stopIndex; }
    public void setStopIndex(int stopIndex) { this.stopIndex = stopIndex; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSearchKeyword() { return searchKeyword; }
    public void setSearchKeyword(String searchKeyword) { this.searchKeyword = searchKeyword; }

    public String getRecommendReason() { return recommendReason; }
    public void setRecommendReason(String recommendReason) { this.recommendReason = recommendReason; }

    public int getEstimatedMinutes() { return estimatedMinutes; }
    public void setEstimatedMinutes(int estimatedMinutes) { this.estimatedMinutes = estimatedMinutes; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getPoiId() { return poiId; }
    public void setPoiId(String poiId) { this.poiId = poiId; }

    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    // ====================== 便捷方法 ======================

    public boolean isArrived() { return "ARRIVED".equals(status); }
    public boolean isCurrent() { return "CURRENT".equals(status); }
    public boolean isUpcoming() { return "UPCOMING".equals(status); }
}
