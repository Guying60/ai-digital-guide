package com.example.digitaltourguide.model;

import android.content.Context;

import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;

public class LocationManager {
    private AMapLocationClient locationClient;
    private OnLocationListener listener;

    public interface OnLocationListener{
        void onLocationSuccess(double latitude,double longitude,String address);
        void onLocationError(String error);
    }

    public void startLocation(Context context,OnLocationListener listener) throws Exception {
        this.listener=listener;

        //初始化定位
        locationClient=new AMapLocationClient(context);

        // 设置定位参数
        AMapLocationClientOption option = new AMapLocationClientOption();
        option.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        option.setOnceLocation(true);          // 单次定位
        option.setNeedAddress(true);           // 返回地址信息

        locationClient.setLocationOption(option);

        //设置定位回调
        locationClient.setLocationListener(location->{
            if(location!=null && location.getErrorCode()==0){
                //定位成功
                double lat=location.getLatitude();
                double lng=location.getLongitude();
                String address=location.getAddress();
                listener.onLocationSuccess(lat,lng,address);
            }else{
                // 定位失败
                String error = location != null ? location.getErrorInfo() : "未知错误";
                listener.onLocationError(error);
            }
        });
            //启动定位
            locationClient.startLocation();
    }

    /**
     * 连续定位 — 用于路线到达判定，每 2 秒回调一次位置
     */
    public void startContinuousLocation(Context context, OnLocationListener listener) throws Exception {
        if (locationClient != null) {
            stopLocation();
        }
        this.listener = listener;
        locationClient = new AMapLocationClient(context);

        AMapLocationClientOption option = new AMapLocationClientOption();
        option.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        option.setOnceLocation(false);            // 连续定位
        option.setInterval(2000);                 // 每 2 秒回调一次
        option.setNeedAddress(false);             // 到达判定不需要地址

        locationClient.setLocationOption(option);
        locationClient.setLocationListener(location -> {
            if (location != null && location.getErrorCode() == 0) {
                listener.onLocationSuccess(
                        location.getLatitude(),
                        location.getLongitude(),
                        location.getAddress());
            }
        });
        locationClient.startLocation();
    }

    public void stopLocation(){
        if(locationClient!=null){
            locationClient.stopLocation();
            locationClient.onDestroy();
            locationClient=null;
        }
    }
}
