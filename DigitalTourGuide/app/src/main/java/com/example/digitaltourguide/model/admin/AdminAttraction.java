package com.example.digitaltourguide.model.admin;

import com.google.gson.annotations.SerializedName;

/**
 * 景点响应模型 — 用于 2.3 新增、2.4 更新、2.7 回显
 */
public class AdminAttraction {

    @SerializedName("id")
    private String id;

    @SerializedName("coverUrl")
    private String coverUrl;

    @SerializedName("attractionName")
    private String attractionName;

    @SerializedName("type")
    private Integer type;

    @SerializedName("attractionContent")
    private String attractionContent;

    // ===== 2.4 / 2.7 回显字段 =====
    @SerializedName("longitude")
    private Double longitude;

    @SerializedName("latitude")
    private Double latitude;

    @SerializedName("province")
    private String province;

    @SerializedName("city")
    private String city;

    @SerializedName("district")
    private String district;

    @SerializedName("adcode")
    private String adcode;

    // ====================== getters & setters ======================

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public String getAttractionName() { return attractionName; }
    public void setAttractionName(String attractionName) { this.attractionName = attractionName; }

    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }

    public String getAttractionContent() { return attractionContent; }
    public void setAttractionContent(String attractionContent) { this.attractionContent = attractionContent; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public String getAdcode() { return adcode; }
    public void setAdcode(String adcode) { this.adcode = adcode; }
}
