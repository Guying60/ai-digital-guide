package com.guying.amap;

/**
 * 高德 POI 解析结果（坐标系：GCJ-02 火星坐标）。
 */
public record AmapPoi(
        String poiId,
        String name,
        String address,
        Double longitude,
        Double latitude,
        String adcode,
        String cityName
) {
}
