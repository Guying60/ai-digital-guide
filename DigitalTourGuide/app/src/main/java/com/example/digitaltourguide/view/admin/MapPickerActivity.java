package com.example.digitaltourguide.view.admin;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.LatLng;
import com.example.digitaltourguide.R;
import com.example.digitaltourguide.utils.MapUtil;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MapPickerActivity extends AppCompatActivity {

    private static final String TAG = "MapPickerActivity";
    private static final String AMAP_KEY = "5c1b9fb736b8d2e3142b34ba06d1e0a1";
    private static final String GEO_URL = "https://restapi.amap.com/v3/geocode/regeo";
    private static final int GEO_DEBOUNCE_MS = 800;

    private MapView mapView;
    private AMap aMap;
    private TextView tvCoords, tvAddress;
    private double selectedLat, selectedLng;
    private String selectedProvince, selectedCity, selectedDistrict, selectedAdcode, selectedAddress;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable geocodeRunnable = new Runnable() {
        @Override
        public void run() {
            updateCoordsDisplay();
            reverseGeocode(selectedLng, selectedLat);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_picker);

        mapView = findViewById(R.id.map_view);
        MapUtil.mapCreate(mapView, savedInstanceState);

        tvCoords = findViewById(R.id.tv_selected_coords);
        tvAddress = findViewById(R.id.tv_selected_address);

        // Toolbar
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // 确认按钮
        findViewById(R.id.btn_confirm).setOnClickListener(v -> confirmSelection());

        // 读取初始坐标
        double initLat = getIntent().getDoubleExtra("latitude", 0);
        double initLng = getIntent().getDoubleExtra("longitude", 0);

        aMap = mapView.getMap();
        if (aMap != null) {
            aMap.getUiSettings().setZoomControlsEnabled(false);

            if (initLat != 0 || initLng != 0) {
                selectedLat = initLat;
                selectedLng = initLng;
            } else {
                selectedLat = 39.9042;
                selectedLng = 116.4074;
            }
            aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(selectedLat, selectedLng), initLat != 0 ? 16f : 10f));
            updateCoordsDisplay();
            reverseGeocode(selectedLng, selectedLat);

            // 地图移动时：更新坐标 + 防抖逆地理编码
            aMap.setOnCameraChangeListener(new AMap.OnCameraChangeListener() {
                @Override
                public void onCameraChange(com.amap.api.maps.model.CameraPosition cameraPosition) {
                    selectedLat = cameraPosition.target.latitude;
                    selectedLng = cameraPosition.target.longitude;
                    handler.removeCallbacks(geocodeRunnable);
                    handler.postDelayed(geocodeRunnable, GEO_DEBOUNCE_MS);
                }

                @Override
                public void onCameraChangeFinish(com.amap.api.maps.model.CameraPosition cameraPosition) {
                    // 停止后精准逆地理编码
                    selectedLat = cameraPosition.target.latitude;
                    selectedLng = cameraPosition.target.longitude;
                    handler.removeCallbacks(geocodeRunnable);
                    updateCoordsDisplay();
                    reverseGeocode(selectedLng, selectedLat);
                }
            });
        }
    }

    private void updateCoordsDisplay() {
        tvCoords.setText(String.format("经度: %.6f  纬度: %.6f", selectedLng, selectedLat));
        tvAddress.setVisibility(View.VISIBLE);
        tvCoords.setVisibility(View.VISIBLE);
    }

    private void reverseGeocode(double lng, double lat) {
        tvAddress.setText(String.format("(%.6f, %.6f) 获取地址中...", lat, lng));

        String url = GEO_URL + "?key=" + AMAP_KEY
                + "&location=" + lng + "," + lat
                + "&extensions=base";

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "逆地理编码失败", e);
                runOnUiThread(() -> tvAddress.setText(
                        String.format("(%.6f, %.6f)", selectedLat, selectedLng)));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    runOnUiThread(() -> tvAddress.setText(
                            String.format("(%.6f, %.6f)", selectedLat, selectedLng)));
                    return;
                }
                try {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);
                    if (json.getInt("status") == 1) {
                        JSONObject regeocode = json.getJSONObject("regeocode");
                        String addr = regeocode.optString("formatted_address", "");
                        JSONObject ac = regeocode.optJSONObject("addressComponent");
                        if (ac != null) {
                            selectedProvince = ac.optString("province", "");
                            selectedCity = ac.optString("city", "");
                            if (selectedCity == null || selectedCity.isEmpty()) {
                                selectedCity = selectedProvince;
                            }
                            selectedDistrict = ac.optString("district", "");
                            selectedAdcode = ac.optString("adcode", "");
                        }
                        selectedAddress = addr;
                        runOnUiThread(() -> tvAddress.setText(addr));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "解析逆地理编码失败", e);
                }
            }
        });
    }

    private void confirmSelection() {
        android.content.Intent result = new android.content.Intent();
        result.putExtra("latitude", selectedLat);
        result.putExtra("longitude", selectedLng);
        result.putExtra("province", selectedProvince != null ? selectedProvince : "");
        result.putExtra("city", selectedCity != null ? selectedCity : "");
        result.putExtra("district", selectedDistrict != null ? selectedDistrict : "");
        result.putExtra("adcode", selectedAdcode != null ? selectedAdcode : "");
        result.putExtra("address", selectedAddress != null ? selectedAddress : "");
        setResult(RESULT_OK, result);
        finish();
    }

    // ── 生命周期 ──

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
        handler.removeCallbacks(geocodeRunnable);
        MapUtil.mapDestroy(mapView);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        MapUtil.mapSaveInstanceState(mapView, outState);
    }
}
