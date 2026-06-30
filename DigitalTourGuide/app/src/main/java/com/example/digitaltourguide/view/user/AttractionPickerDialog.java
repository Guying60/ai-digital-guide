package com.example.digitaltourguide.view.user;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.digitaltourguide.R;
import com.example.digitaltourguide.adapter.AttractionPickerAdapter;
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

public class AttractionPickerDialog extends DialogFragment {
    private static final int REQ_LOCATION = 5001;

    private EditText etSearch;
    private RecyclerView rvAttractions;
    private AttractionPickerAdapter adapter;
    private String currentKeyword = "";
    private String nextLastId = null;//最后一条数据的id
    private boolean isLoading = false;//表示当前正在加载数据，防止用户快速滑动重复触发加载请求
    private boolean hasMore = true;//是否还有数据可以加载
    private static final int PAGE_SIZE = 10;
    private List<ScenicSpot> spotList=new ArrayList<>();

    // 定位相关
    private LocationManager locationManager;
    private String userCity;
    private double userLng, userLat;
    private boolean locationReady = false;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private LinearLayout llLoading;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_attraction_list, container, false);
        etSearch = view.findViewById(R.id.et_search);
        rvAttractions = view.findViewById(R.id.rv_attractions);
        progressBar = view.findViewById(R.id.progress_bar);
        tvStatus = view.findViewById(R.id.tv_status);
        llLoading = view.findViewById(R.id.ll_loading);

        rvAttractions.setLayoutManager(new GridLayoutManager(getContext(),2));
        adapter = new AttractionPickerAdapter(getContext(),spotList);
        adapter.setOnItemClickListener(spot -> {
            Intent intent = new Intent(getActivity(), ChatActivity.class);
            intent.putExtra("attractionId", spot.getId());
            startActivity(intent);
            dismiss();
        });
        rvAttractions.setAdapter(adapter);

        // 搜索框文本变化监听
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                currentKeyword = s.toString();
                if (locationReady) {
                    resetAndLoad();
                }
            }
        });
        //滚动到底部加载更多
        rvAttractions.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                //该方法为在滚动中持续触发的回调方法
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                int total = adapter.getItemCount();
                int lastVisible = lm.findLastVisibleItemPosition();//最后一个可见条目
                if (!isLoading && hasMore && lastVisible >= total - 2) {
                    //当最后一个可见条目的位置》=总条目数-2就触发加载
                    loadMore();
                }
            }
        });

        // 检查定位权限，再加载数据
        if (hasLocationPermission()) {
            startLocationAndLoad();
        } else {
            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
        }
        return view;
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
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
                userCity = "北京市";
                userLng = 116.4074;
                userLat = 39.9042;
                locationReady = true;

                tvStatus.setText("未授予定位权限，显示默认城市（北京市）");
                progressBar.setVisibility(View.GONE);

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    llLoading.setVisibility(View.GONE);
                    rvAttractions.setVisibility(View.VISIBLE);
                    loadFirstPage();
                }, 1500);
            }
        }
    }

    // 获取用户定位
    private void startLocationAndLoad() {
        llLoading.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setText("正在获取位置...");
        rvAttractions.setVisibility(View.GONE);

        locationManager = new LocationManager();
        try {
            locationManager.startDetailLocation(getContext(), new LocationManager.OnDetailLocationListener() {
                @Override
                public void onLocationSuccess(double latitude, double longitude,
                                              String province, String city, String district,
                                              String adcode, String address) {
                    // 城市名为空时兜底
                    userCity = (city != null && !city.isEmpty()) ? city : "北京市";
                    userLng = longitude;
                    userLat = latitude;
                    locationReady = true;

                    Log.d("AttractionPicker", "定位成功: city=" + userCity + " (原始=" + city + "), lng=" + longitude + ", lat=" + latitude);

                    new Handler(Looper.getMainLooper()).post(() -> {
                        llLoading.setVisibility(View.GONE);
                        rvAttractions.setVisibility(View.VISIBLE);
                        loadFirstPage();
                    });
                }

                @Override
                public void onLocationError(String error) {
                    Log.e("AttractionPicker", "定位失败: " + error);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        // 定位失败用北京市兜底，并提示用户
                        userCity = "北京市";
                        userLng = 116.4074;
                        userLat = 39.9042;
                        locationReady = true;

                        tvStatus.setText("定位失败，显示默认城市（北京市）\n请检查定位权限是否已开启");
                        progressBar.setVisibility(View.GONE);

                        // 2秒后自动加载
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            llLoading.setVisibility(View.GONE);
                            rvAttractions.setVisibility(View.VISIBLE);
                            loadFirstPage();
                        }, 2000);
                    });
                }
            });
        } catch (Exception e) {
            Log.e("AttractionPicker", "定位启动异常: " + e.getMessage());
            userCity = "北京市";
            userLng = 116.4074;
            userLat = 39.9042;
            locationReady = true;
            llLoading.setVisibility(View.GONE);
            rvAttractions.setVisibility(View.VISIBLE);
            loadFirstPage();
        }
    }

    //重置分页状态并重新加载第一页数据
    private void resetAndLoad(){
        nextLastId=null;
        hasMore=true;
        spotList.clear();
        adapter.notifyDataSetChanged();//清空当前数据等待加载
        loadFirstPage();
    }

    //请求第一页数据
    private void loadFirstPage() {
        loadData(null);
    }

    private void loadMore(){
        if(nextLastId!=null && !isLoading){
            loadData(nextLastId);
        }
    }


    private void loadData(String lastId) {
        if(isLoading) return;
        isLoading=true;

        Log.d("AttractionPicker", "请求参数: city=" + userCity + ", lng=" + userLng + ", lat=" + userLat + ", keyword=" + currentKeyword + ", lastId=" + lastId);
        // keyword 为空时传 null，避免空字符串被当作无效参数
        String kw = (currentKeyword != null && !currentKeyword.isEmpty()) ? currentKeyword : null;
        String token= SpUtils.getUserToken(getContext());
         RetrofitClient.getApiService()
                .getAttractions(userCity, userLng, userLat, kw, null, lastId, PAGE_SIZE)
                 .enqueue(new Callback<AttractionPage>() {
                     @Override
                     public void onResponse(Call<AttractionPage> call, Response<AttractionPage> response) {
                         isLoading=false;
                         Log.d("AttractionPicker", "HTTP code: " + response.code());
                         if(!response.isSuccessful()){
                             Toast.makeText(getContext(), "网络错误: HTTP " + response.code(), Toast.LENGTH_LONG).show();
                             return;
                         }
                         AttractionPage page = response.body();
                         if(page == null){
                             Toast.makeText(getContext(), "服务器返回空数据", Toast.LENGTH_LONG).show();
                             return;
                         }
                         List<ScenicSpot> newSpots=page.getList();
                         if(newSpots==null) newSpots=new ArrayList<>();

                         if(lastId==null){
                             spotList.clear();
                             spotList.addAll(newSpots);
                         }else{
                             spotList.addAll(newSpots);
                         }
                         adapter.notifyDataSetChanged();
                         nextLastId= page.getNextLastId();
                         hasMore=page.isHasMore();
                     }
                     @Override
                     public void onFailure(Call<AttractionPage> call, Throwable t) {
                            isLoading=false;
                            Toast.makeText(getContext(),"网络错误："+t.getMessage(),Toast.LENGTH_SHORT).show();
                     }
                 });
    }

    @Override
    public void onResume() {
        super.onResume();
        if(getDialog()!=null && getDialog().getWindow()!=null){
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
}
