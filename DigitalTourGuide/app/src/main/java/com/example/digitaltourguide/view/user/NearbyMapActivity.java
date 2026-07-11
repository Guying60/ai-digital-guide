package com.example.digitaltourguide.view.user;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.amap.api.maps.AMap;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.example.digitaltourguide.R;
import com.example.digitaltourguide.model.LocationManager;
import com.example.digitaltourguide.model.user.AttractionListResponse;
import com.example.digitaltourguide.model.user.AttractionPage;
import com.example.digitaltourguide.model.user.ScenicSpot;
import com.example.digitaltourguide.network.ApiService;
import com.example.digitaltourguide.network.RetrofitClient;
import com.example.digitaltourguide.utils.MapUtil;
import com.example.digitaltourguide.utils.SpUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NearbyMapActivity extends AppCompatActivity {

    private static final String TAG = "NearbyMapActivity";
    private static final int REQ_LOCATION = 4001;

    private MapView mapView;
    private AMap aMap;
    private LocationManager locationManager;
    private final List<ScenicSpot> spotList = new ArrayList<>();
    private final Map<Marker, ScenicSpot> markerMap = new HashMap<>();
    private String userProvince, userCity, userDistrict, userAdcode;
    private double userLat, userLng;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nearby_map);

        SpUtils.init(this);

        mapView = findViewById(R.id.map_view);
        MapUtil.mapCreate(mapView, savedInstanceState);

        // Toolbar 返回
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // FAB: 回到我的位置
        findViewById(R.id.fab_my_location).setOnClickListener(v -> {
            if (aMap != null) {
                MapUtil.moveCamera(aMap, userLat, userLng, 16f);
            }
        });

        // 获取地图实例
        aMap = mapView.getMap();
        if (aMap != null) {
            MapUtil.setupBlueDot(aMap);
            aMap.setOnMarkerClickListener(marker -> {
                ScenicSpot spot = markerMap.get(marker);
                if (spot != null) {
                    Intent intent = new Intent(NearbyMapActivity.this, ChatActivity.class);
                    intent.putExtra("attractionId", spot.getId());
                    startActivity(intent);
                }
                return true;
            });

            // 请求定位权限并加载数据
            if (hasLocationPermission()) {
                startLocationAndLoad();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
            }
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // 重新开启蓝点并加载
            if (aMap != null) {
                MapUtil.setupBlueDot(aMap);
            }
            startLocationAndLoad();
        }
    }

    private void startLocationAndLoad() {
        locationManager = new LocationManager();
        try {
            locationManager.startDetailLocation(this, new LocationManager.OnDetailLocationListener() {
                @Override
                public void onLocationSuccess(double latitude, double longitude,
                                              String province, String city, String district,
                                              String adcode, String address) {
                    userLat = latitude;
                    userLng = longitude;
                    userProvince = province;
                    userCity = city;
                    userDistrict = district;
                    userAdcode = adcode;

                    Log.d(TAG, "定位成功: lat=" + latitude + ", lng=" + longitude + ", city=" + city);

                    // 移动地图到用户位置
                    if (aMap != null) {
                        MapUtil.moveCamera(aMap, latitude, longitude, 15f);
                    }

                    // 加载附近景点
                    loadNearbyAttractions(city, longitude, latitude);
                }

                @Override
                public void onLocationError(String error) {
                    Log.e(TAG, "定位失败: " + error);
                    Toast.makeText(NearbyMapActivity.this, "定位失败: " + error, Toast.LENGTH_SHORT).show();
                    // 定位失败也尝试加载（用北京中心兜底）
                    userLat = 39.9042;
                    userLng = 116.4074;
                    userCity = "北京市";
                    if (aMap != null) {
                        MapUtil.moveCamera(aMap, userLat, userLng, 12f);
                    }
                    loadNearbyAttractions(userCity, userLng, userLat);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "定位异常", e);
            Toast.makeText(this, "定位失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadNearbyAttractions(String city, Double userLongitude, Double userLatitude) {
        ApiService api = RetrofitClient.getApiService();
        api.getAttractions(city, userLongitude, userLatitude, null, null, null, 50)
                .enqueue(new Callback<AttractionListResponse>() {
                    @Override
                    public void onResponse(Call<AttractionListResponse> call, Response<AttractionListResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                            List<ScenicSpot> list = response.body().getData().getList();
                            if (list != null && !list.isEmpty()) {
                                spotList.clear();
                                spotList.addAll(list);
                                addMarkers(spotList);
                            } else {
                                Log.d(TAG, "附近景点为空");
                            }
                        } else {
                            Log.e(TAG, "加载景点失败: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<AttractionListResponse> call, Throwable t) {
                        Log.e(TAG, "网络错误: " + t.getMessage(), t);
                    }
                });
    }

    private void addMarkers(List<ScenicSpot> spots) {
        if (aMap == null) return;
        for (ScenicSpot spot : spots) {
            // ScenicSpot 目前没有经纬度，预留支持
            // 如果有坐标则标注
            LatLng pos = MapUtil.safeLatLng(spot.getLatitude(), spot.getLongitude());
            if (pos != null) {
                MarkerOptions options = new MarkerOptions()
                        .position(pos)
                        .title(spot.getTitle())
                        .snippet(spot.getDistance() != null
                                ? String.format("距离 %.0f 米", spot.getDistance()) : "");
                Marker marker = aMap.addMarker(options);
                markerMap.put(marker, spot);
            }
        }
        Log.d(TAG, "已添加 " + markerMap.size() + " 个景点 marker");
    }

    // ── 生命周期转发 ──

    @Override
    protected void onResume() {
        super.onResume();
        MapUtil.mapResume(mapView);
    }

    @Override
    protected void onPause() {
        super.onPause();
        MapUtil.mapPause(mapView);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationManager != null) {
            locationManager.stopLocation();
        }
        MapUtil.mapDestroy(mapView);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        MapUtil.mapSaveInstanceState(mapView, outState);
    }
}
