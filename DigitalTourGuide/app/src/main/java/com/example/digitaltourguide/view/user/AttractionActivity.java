package com.example.digitaltourguide.view.user;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.digitaltourguide.R;
import com.example.digitaltourguide.adapter.AttractionAdapter;
import com.example.digitaltourguide.model.BaseResponse;
import com.example.digitaltourguide.model.LocationManager;
import com.example.digitaltourguide.model.user.AttractionPage;
import com.example.digitaltourguide.model.user.ScenicSpot;
import com.example.digitaltourguide.network.RetrofitClient;
import com.example.digitaltourguide.utils.SpUtils;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AttractionActivity extends AppCompatActivity {

    private static final int REQ_LOCATION = 3001;

    private RecyclerView rvAttractions;
    private AttractionAdapter adapter;
    private final List<ScenicSpot> spotList = new ArrayList<>();

    private String currentKeyword = "";
    private String nextLastId;
    private boolean isLoading;
    private boolean hasMore = true;
    private static final int PAGE_SIZE = 10;

    // 定位相关
    private LocationManager locationManager;
    private String userCity;
    private double userLng, userLat;
    private boolean locationReady = false;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private LinearLayout llLoading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attraction_picker);

        SpUtils.init(this);

        // Toolbar
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Loading views
        llLoading = findViewById(R.id.ll_loading);
        progressBar = findViewById(R.id.progress_bar);
        tvStatus = findViewById(R.id.tv_status);

        // 搜索框
        androidx.appcompat.widget.AppCompatEditText etSearch = findViewById(R.id.et_search);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                currentKeyword = s.toString();
                if (locationReady) {
                    resetAndLoad();
                }
            }
        });

        // RecyclerView
        rvAttractions = findViewById(R.id.rv_attractions);
        rvAttractions.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AttractionAdapter(this, spotList);
        adapter.setOnItemClickListener(spot -> {
            Intent intent = new Intent(AttractionActivity.this, ChatActivity.class);
            intent.putExtra("attractionId", spot.getId());
            startActivity(intent);
        });
        rvAttractions.setAdapter(adapter);

        // 滚动加载更多
        rvAttractions.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                int total = adapter.getItemCount();
                int lastVisible = lm.findLastVisibleItemPosition();
                if (!isLoading && hasMore && lastVisible >= total - 2) {
                    loadMore();
                }
            }
        });

        // 检查定位权限
        if (hasLocationPermission()) {
            startLocationAndLoad();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
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
        if (requestCode == REQ_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationAndLoad();
            } else {
                // 用户拒绝权限，用默认城市兜底
                tvStatus.setText("未授予定位权限，显示默认城市（北京市）");
                progressBar.setVisibility(View.GONE);
                userCity = "北京市";
                userLng = 116.4074;
                userLat = 39.9042;
                locationReady = true;
                new android.os.Handler().postDelayed(() -> {
                    llLoading.setVisibility(View.GONE);
                    rvAttractions.setVisibility(View.VISIBLE);
                    loadFirstPage();
                }, 1500);
            }
        }
    }

    private void startLocationAndLoad() {
        llLoading.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setText("正在获取位置...");
        rvAttractions.setVisibility(View.GONE);

        locationManager = new LocationManager();
        try {
            locationManager.startDetailLocation(this, new LocationManager.OnDetailLocationListener() {
                @Override
                public void onLocationSuccess(double latitude, double longitude,
                                              String province, String city, String district,
                                              String adcode, String address) {
                    userCity = (city != null && !city.isEmpty()) ? city : "北京市";
                    userLng = longitude;
                    userLat = latitude;
                    locationReady = true;

                    Log.d("AttractionActivity", "定位成功: city=" + userCity + " (原始=" + city + "), lng=" + longitude + ", lat=" + latitude);

                    runOnUiThread(() -> {
                        llLoading.setVisibility(View.GONE);
                        rvAttractions.setVisibility(View.VISIBLE);
                        loadFirstPage();
                    });
                }

                @Override
                public void onLocationError(String error) {
                    Log.e("AttractionActivity", "定位失败: " + error);
                    runOnUiThread(() -> {
                        userCity = "北京市";
                        userLng = 116.4074;
                        userLat = 39.9042;
                        locationReady = true;

                        tvStatus.setText("定位失败，显示默认城市（北京市）\n请检查GPS是否已开启");
                        progressBar.setVisibility(View.GONE);

                        new android.os.Handler().postDelayed(() -> {
                            llLoading.setVisibility(View.GONE);
                            rvAttractions.setVisibility(View.VISIBLE);
                            loadFirstPage();
                        }, 2000);
                    });
                }
            });
        } catch (Exception e) {
            Log.e("AttractionActivity", "定位启动异常: " + e.getMessage());
            userCity = "北京市";
            userLng = 116.4074;
            userLat = 39.9042;
            locationReady = true;
            llLoading.setVisibility(View.GONE);
            rvAttractions.setVisibility(View.VISIBLE);
            loadFirstPage();
        }
    }

    private void resetAndLoad() {
        nextLastId = null;
        hasMore = true;
        spotList.clear();
        adapter.notifyDataSetChanged();
        loadFirstPage();
    }

    private void loadFirstPage() {
        loadData(null);
    }

    private void loadMore() {
        if (nextLastId != null && !isLoading) {
            loadData(nextLastId);
        }
    }

    private void loadData(String lastId) {
        if (isLoading) return;
        isLoading = true;

        String token = SpUtils.getUserToken(this);
        RetrofitClient.getApiService()
                .getAttractions(userCity, userLng, userLat, currentKeyword, null, lastId, PAGE_SIZE)
                .enqueue(new Callback<BaseResponse<AttractionPage>>() {
                    @Override
                    public void onResponse(Call<BaseResponse<AttractionPage>> call, Response<BaseResponse<AttractionPage>> response) {
                        isLoading = false;
                        if (!response.isSuccessful()) {
                            Toast.makeText(AttractionActivity.this,
                                    "网络错误: HTTP " + response.code(), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        BaseResponse<AttractionPage> body = response.body();
                        if (body == null) {
                            Toast.makeText(AttractionActivity.this,
                                    "服务器返回空数据", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Log.d("AttractionActivity", "业务code=" + body.getCode() + ", msg=" + body.getMsg());
                        if (body.getCode() != 1 || body.getData() == null) {
                            String serverMsg = body.getMsg() != null && !body.getMsg().isEmpty()
                                    ? body.getMsg() : "暂无景点数据";
                            Toast.makeText(AttractionActivity.this, serverMsg, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        AttractionPage page = body.getData();
                        List<ScenicSpot> newSpots = page.getList();
                        if (newSpots == null) newSpots = new ArrayList<>();

                        if (lastId == null) {
                            spotList.clear();
                            spotList.addAll(newSpots);
                        } else {
                            spotList.addAll(newSpots);
                        }
                        adapter.notifyDataSetChanged();
                        nextLastId = page.getNextLastId();
                        hasMore = page.isHasMore();
                    }

                    @Override
                    public void onFailure(Call<BaseResponse<AttractionPage>> call, Throwable t) {
                        isLoading = false;
                        Toast.makeText(AttractionActivity.this,
                                "网络错误：" + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
