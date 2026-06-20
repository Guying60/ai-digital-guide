package com.example.digitaltourguide.model.user;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * 1.16.1 路线计划响应 — data 字段
 */
public class RoutePlanVO {

    @SerializedName("routeId")
    private String routeId;

    @SerializedName("title")
    private String title;

    @SerializedName("summary")
    private String summary;

    @SerializedName("attractionId")
    private long attractionId;

    @SerializedName("cityName")
    private String cityName;

    @SerializedName("adcode")
    private String adcode;

    @SerializedName("centerLng")
    private Double centerLng;

    @SerializedName("centerLat")
    private Double centerLat;

    @SerializedName("generatedAt")
    private long generatedAt;

    @SerializedName("stops")
    private List<RouteStopVO> stops;

    // ====================== getters & setters ======================

    public String getRouteId() { return routeId; }
    public void setRouteId(String routeId) { this.routeId = routeId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public long getAttractionId() { return attractionId; }
    public void setAttractionId(long attractionId) { this.attractionId = attractionId; }

    public String getCityName() { return cityName; }
    public void setCityName(String cityName) { this.cityName = cityName; }

    public String getAdcode() { return adcode; }
    public void setAdcode(String adcode) { this.adcode = adcode; }

    public Double getCenterLng() { return centerLng; }
    public void setCenterLng(Double centerLng) { this.centerLng = centerLng; }

    public Double getCenterLat() { return centerLat; }
    public void setCenterLat(Double centerLat) { this.centerLat = centerLat; }

    public long getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(long generatedAt) { this.generatedAt = generatedAt; }

    public List<RouteStopVO> getStops() { return stops; }
    public void setStops(List<RouteStopVO> stops) { this.stops = stops; }

    // ====================== 便捷方法 ======================

    public boolean hasStops() { return stops != null && !stops.isEmpty(); }

    public RouteStopVO getCurrentStop() {
        if (stops == null) return null;
        for (RouteStopVO stop : stops) {
            if (stop.isCurrent()) return stop;
        }
        return null;
    }
}
