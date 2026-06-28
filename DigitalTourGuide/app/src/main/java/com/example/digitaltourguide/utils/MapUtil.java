package com.example.digitaltourguide.utils;

import android.os.Bundle;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.MyLocationStyle;

/**
 * 高德地图工具类 — 封装 MapView 生命周期转发、蓝点定位、Marker 构建等通用逻辑
 */
public class MapUtil {

    // ── 生命周期转发（每个 Activity 必须调用，否则地图不显示/崩溃/内存泄漏）──

    public static void mapCreate(MapView mapView, Bundle savedInstanceState) {
        if (mapView != null) mapView.onCreate(savedInstanceState);
    }

    public static void mapResume(MapView mapView) {
        if (mapView != null) mapView.onResume();
    }

    public static void mapPause(MapView mapView) {
        if (mapView != null) mapView.onPause();
    }

    public static void mapDestroy(MapView mapView) {
        if (mapView != null) mapView.onDestroy();
    }

    public static void mapSaveInstanceState(MapView mapView, Bundle outState) {
        if (mapView != null) mapView.onSaveInstanceState(outState);
    }

    // ── 蓝色定位点 ──

    /**
     * 开启地图蓝点定位（跟随模式，不跟随旋转）
     */
    public static void setupBlueDot(AMap aMap) {
        if (aMap == null) return;
        MyLocationStyle style = new MyLocationStyle();
        style.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER);
        style.interval(2000);
        style.showMyLocation(true);
        aMap.setMyLocationStyle(style);
        // 显示自带的定位按钮
        aMap.getUiSettings().setMyLocationButtonEnabled(true);
        aMap.setMyLocationEnabled(true);
    }

    // ── Marker 构建 ──

    /**
     * 构建一个景点 Marker
     * @param lat   纬度
     * @param lng   经度
     * @param title 景点名称
     * @return MarkerOptions（不带 snippet，后续设置）
     */
    public static MarkerOptions buildMarker(double lat, double lng, String title) {
        return new MarkerOptions()
                .position(new LatLng(lat, lng))
                .title(title)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                .draggable(false);
    }

    /**
     * 安全创建 LatLng
     */
    public static LatLng safeLatLng(Double lat, Double lng) {
        if (lat == null || lng == null) return null;
        return new LatLng(lat, lng);
    }

    /**
     * 移动相机到指定位置
     */
    public static void moveCamera(AMap aMap, double lat, double lng, float zoom) {
        if (aMap != null) {
            aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), zoom));
        }
    }
}
