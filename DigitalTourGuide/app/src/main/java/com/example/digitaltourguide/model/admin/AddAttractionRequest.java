package com.example.digitaltourguide.model.admin;

import com.google.gson.annotations.SerializedName;

/**
 * 2.3 新增景点 / 2.4 更新景点 请求体
 */
public class AddAttractionRequest {

    @SerializedName("id")
    private String id;

    @SerializedName("coverUrl")
    private String coverUrl;

    @SerializedName("attractionName")
    private String attractionName;

    @SerializedName("type")
    private Integer type;

    // ===== 2.3 / 2.4 新增字段 =====
    @SerializedName("longitude")
    private Double longitude;

    @SerializedName("latitude")
    private Double latitude;

    @SerializedName("city")
    private String city;

    @SerializedName("province")
    private String province;

    @SerializedName("district")
    private String district;

    @SerializedName("adcode")
    private String adcode;

    // ===== 旧字段（暂留兼容） =====
    @SerializedName("rating")
    private Double rating;

    @SerializedName("reviewCount")
    private Integer reviewCount;

    @SerializedName("openHours")
    private String openHours;

    // ---- 构造函数 ----

    /** 新增用（不带 id）：必填字段 */
    public AddAttractionRequest(String coverUrl, String attractionName, Integer type) {
        this.coverUrl = coverUrl;
        this.attractionName = attractionName;
        this.type = type;
    }

    /** 更新用（带 id）：必填字段 */
    public AddAttractionRequest(String id, String coverUrl, String attractionName, Integer type) {
        this.id = id;
        this.coverUrl = coverUrl;
        this.attractionName = attractionName;
        this.type = type;
    }

    // ---- getters & setters ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public String getAttractionName() { return attractionName; }
    public void setAttractionName(String attractionName) { this.attractionName = attractionName; }

    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public String getAdcode() { return adcode; }
    public void setAdcode(String adcode) { this.adcode = adcode; }

    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }

    public Integer getReviewCount() { return reviewCount; }
    public void setReviewCount(Integer reviewCount) { this.reviewCount = reviewCount; }

    public String getOpenHours() { return openHours; }
    public void setOpenHours(String openHours) { this.openHours = openHours; }
}
