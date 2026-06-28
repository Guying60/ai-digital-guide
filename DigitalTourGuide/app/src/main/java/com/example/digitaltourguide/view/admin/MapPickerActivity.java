package com.example.digitaltourguide.view.admin;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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
    // Web服务 Key（与 Android SDK key 分开）
    private static final String AMAP_KEY = "c578d1916e1b30af9d2c9f1b49564a00";
    private static final String GEO_URL = "https://restapi.amap.com/v3/geocode/regeo";
    private static final int GEO_DEBOUNCE_MS = 600;

    private MapView mapView;
    private AMap aMap;
    private TextView tvAddress;
    private double selectedLat, selectedLng;
    private String selectedProvince, selectedCity, selectedDistrict, selectedAdcode, selectedAddress;
    private boolean confirming;
    private OkHttpClient httpClient;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable geocodeRunnable = this::doReverseGeocode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_picker);

        httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        mapView = findViewById(R.id.map_view);
        MapUtil.mapCreate(mapView, savedInstanceState);

        tvAddress = findViewById(R.id.tv_selected_address);

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            confirming = true;
            v.setEnabled(false);
            tvAddress.setText("正在获取地址...");
            doReverseGeocode();
        });

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
            handler.postDelayed(geocodeRunnable, 300);

            aMap.setOnCameraChangeListener(new AMap.OnCameraChangeListener() {
                @Override
                public void onCameraChange(com.amap.api.maps.model.CameraPosition p) {
                    handler.removeCallbacks(geocodeRunnable);
                }

                @Override
                public void onCameraChangeFinish(com.amap.api.maps.model.CameraPosition p) {
                    selectedLat = p.target.latitude;
                    selectedLng = p.target.longitude;
                    handler.removeCallbacks(geocodeRunnable);
                    handler.postDelayed(geocodeRunnable, GEO_DEBOUNCE_MS);
                }
            });
        }
    }

    private void doReverseGeocode() {
        if (!confirming) {
            tvAddress.setText(String.format("(%.6f, %.6f) 获取地址中...", selectedLat, selectedLng));
        }
        String url = GEO_URL + "?key=" + AMAP_KEY
                + "&location=" + selectedLng + "," + selectedLat + "&extensions=base";
        httpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "逆地理编码失败: " + e.getMessage());
                handleResult();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject json = new JSONObject(response.body().string());
                        if (json.getInt("status") == 1) {
                            JSONObject rg = json.getJSONObject("regeocode");
                            selectedAddress = rg.optString("formatted_address", "");
                            JSONObject ac = rg.optJSONObject("addressComponent");
                            if (ac != null) {
                                selectedProvince = ac.optString("province", "");
                                selectedCity = ac.optString("city", "");
                                if (selectedCity == null || selectedCity.isEmpty())
                                    selectedCity = selectedProvince;
                                selectedDistrict = ac.optString("district", "");
                                selectedAdcode = ac.optString("adcode", "");
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "解析失败", e);
                }
                handleResult();
            }
        });
    }

    private void handleResult() {
        if (confirming) {
            runOnUiThread(this::finishWithResult);
        } else {
            runOnUiThread(() -> tvAddress.setText(
                    selectedAddress != null ? selectedAddress
                            : String.format("(%.6f, %.6f)", selectedLat, selectedLng)));
        }
    }

    private void finishWithResult() {
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
