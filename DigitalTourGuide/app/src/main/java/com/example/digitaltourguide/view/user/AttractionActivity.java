package com.example.digitaltourguide.view.user;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.digitaltourguide.R;
import com.example.digitaltourguide.adapter.AttractionAdapter;
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

    private RecyclerView rvAttractions;
    private AttractionAdapter adapter;
    private final List<ScenicSpot> spotList = new ArrayList<>();

    private String currentKeyword = "";
    private String nextLastId;
    private boolean isLoading;
    private boolean hasMore = true;
    private static final int PAGE_SIZE = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attraction_picker);

        SpUtils.init(this);

        // Toolbar
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());

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
                resetAndLoad();
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

        // 加载第一页
        loadFirstPage();
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
                .getAttractions(currentKeyword, lastId, PAGE_SIZE)
                .enqueue(new Callback<AttractionPage>() {
                    @Override
                    public void onResponse(Call<AttractionPage> call, Response<AttractionPage> response) {
                        isLoading = false;
                        if (response.isSuccessful() && response.body() != null) {
                            AttractionPage page = response.body();
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
                        } else {
                            Toast.makeText(AttractionActivity.this,
                                    "加载失败: HTTP " + response.code(), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<AttractionPage> call, Throwable t) {
                        isLoading = false;
                        Toast.makeText(AttractionActivity.this,
                                "网络错误：" + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }
}