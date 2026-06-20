package com.example.digitaltourguide.model;

import android.app.Application;

import com.example.digitaltourguide.network.RetrofitClient;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        RetrofitClient.init(this);

        // 高德地图 SDK 隐私合规（必须！否则会崩溃）
        // 自 2024年起，高德强制要求在初始化 SDK 之前调用此方法
        com.amap.api.maps.MapsInitializer.updatePrivacyShow(this, true, true);
        com.amap.api.maps.MapsInitializer.updatePrivacyAgree(this, true);

        // 同时定位也需要同意隐私
        com.amap.api.location.AMapLocationClient.updatePrivacyShow(this, true, true);
        com.amap.api.location.AMapLocationClient.updatePrivacyAgree(this, true);
    }
}
