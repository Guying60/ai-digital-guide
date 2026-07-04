package com.example.digitaltourguide.view.user;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.digitaltourguide.R;
import com.example.digitaltourguide.model.BaseResponse;
import com.example.digitaltourguide.model.user.UpdateUserRequest;
import com.example.digitaltourguide.network.ApiService;
import com.example.digitaltourguide.network.RetrofitClient;
import com.example.digitaltourguide.utils.SpUtils;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MyActivity extends AppCompatActivity {

    private static final String TAG = "MyActivity";
    private static final int REQ_PROFILE = 2001;

    private ApiService apiService;
    private ImageView ivAvatar;
    private TextView tvNickname, tvUserId;
    private LinearLayout tvHistory, tvMine;
    private Chip chipGender;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my);

        SpUtils.init(this);

        initViews();
        initClickListeners();
        loadUserInfo();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PROFILE) {
            // 从 ProfileInfoActivity 返回，强制刷新用户信息
            Log.d(TAG, "从 ProfileInfoActivity 返回，刷新用户信息");
            loadUserInfo();
        }
    }

    private void initViews() {
        ivAvatar = findViewById(R.id.iv_avatar);
        tvNickname = findViewById(R.id.tv_nickname);
        tvUserId = findViewById(R.id.tv_user_id);
        chipGender = findViewById(R.id.chip_gender);
        tvHistory = findViewById(R.id.tv_history);
        tvMine = findViewById(R.id.tv_mine);
    }

    private void initClickListeners() {
        // 用户信息卡片 → 个人信息编辑
        findViewById(R.id.card_user_info).setOnClickListener(v ->
                startActivityForResult(new Intent(this, ProfileInfoActivity.class), REQ_PROFILE)
        );

        // 我的评价
        findViewById(R.id.btn_my_reviews).setOnClickListener(v ->
                startActivity(new Intent(this, MyJudgeActivity.class))
        );

        // 偏好设置
        findViewById(R.id.card_preferences).setOnClickListener(v ->
                startActivity(new Intent(this, MyPerActivity.class))
        );

        // 景点地图
        findViewById(R.id.btn_nearby_map).setOnClickListener(v ->
                startActivity(new Intent(this, NearbyMapActivity.class))
        );

        // 账号管理 → 个人信息编辑
        findViewById(R.id.btn_account_mgmt).setOnClickListener(v ->
                startActivityForResult(new Intent(this, ProfileInfoActivity.class), REQ_PROFILE)
        );

        // 关于我们
        findViewById(R.id.btn_about).setOnClickListener(v -> showAboutDialog());

        // 退出登录
        findViewById(R.id.btn_logout).setOnClickListener(v -> showLogoutDialog());

        // 底部导航：出游记录
        tvHistory.setOnClickListener(v -> {
            Intent intent = new Intent(this, HistoryActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        });

        // 底部导航：个人中心（当前页面）
        tvMine.setOnClickListener(v ->
                Toast.makeText(this, "当前已是个人中心", Toast.LENGTH_SHORT).show()
        );
    }

    private void showAboutDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("关于我们")
                .setMessage("AI 数字导游 v1.0\n\n" +
                        "为您提供智能导览、实时对话、\n" +
                        "路线规划等一站式旅游服务。\n\n" +
                        "让每一次旅行都与众不同。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showLogoutDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("退出登录")
                .setMessage("确定要退出当前账号吗？")
                .setPositiveButton("退出", (dialog, which) -> {
                    SpUtils.clearUserInfo(this);
                    Intent intent = new Intent(this, UserLoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 加载用户信息（GET /users/userInfo）
     * AuthInterceptor 自动注入用户 Token
     */
    private void loadUserInfo() {
        if (apiService == null) {
            apiService = RetrofitClient.getInstance().create(ApiService.class);
        }

        apiService.getUserInfo().enqueue(new Callback<BaseResponse<UpdateUserRequest>>() {
            @Override
            public void onResponse(Call<BaseResponse<UpdateUserRequest>> call,
                                   Response<BaseResponse<UpdateUserRequest>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.w(TAG, "getUserInfo 响应失败: " + response.code());
                    return;
                }
                BaseResponse<UpdateUserRequest> resp = response.body();
                Log.d(TAG, "getUserInfo code=" + resp.getCode() + " msg=" + resp.getMsg());
                if (resp.getCode() == 1 && resp.getData() != null) {
                    UpdateUserRequest data = resp.getData();
                    Log.d(TAG, "nickname=" + data.getNickname()
                            + " gender=" + data.getGender()
                            + " avatar=" + data.getAvatarUrl());
                    bindUserInfo(data);
                }
            }

            @Override
            public void onFailure(Call<BaseResponse<UpdateUserRequest>> call, Throwable t) {
                Log.e(TAG, "加载用户信息失败", t);
            }
        });
    }

    private void bindUserInfo(UpdateUserRequest data) {
        // 昵称
        if (data.getNickname() != null && !data.getNickname().isEmpty()) {
            tvNickname.setText(data.getNickname());
        }

        // 头像（跳过 Glide 磁盘缓存确保刷新）
        if (data.getAvatarUrl() != null && !data.getAvatarUrl().isEmpty()) {
            Glide.with(this)
                    .load(data.getAvatarUrl())
                    .circleCrop()
                    .skipMemoryCache(true)
                    .placeholder(R.drawable.ic_person_filled)
                    .into(ivAvatar);
        } else {
            String cached = SpUtils.getUserAvatar(this);
            if (!cached.isEmpty()) {
                Glide.with(this)
                        .load(cached)
                        .circleCrop()
                        .into(ivAvatar);
            }
        }

        // 性别
        if (data.getGender() != null) {
            chipGender.setVisibility(android.view.View.VISIBLE);
            String genderStr;
            switch (data.getGender()) {
                case 0:
                    genderStr = "♀ 女";
                    chipGender.setChipBackgroundColorResource(R.color.profile_female_pink);
                    chipGender.setTextColor(getColor(R.color.profile_on_female_pink));
                    break;
                case 1:
                    genderStr = "♂ 男";
                    chipGender.setChipBackgroundColorResource(R.color.profile_primary_container);
                    chipGender.setTextColor(getColor(R.color.profile_on_primary_container));
                    break;
                default:
                    genderStr = "⚥ 未知";
                    chipGender.setVisibility(android.view.View.GONE);
                    break;
            }
            chipGender.setText(genderStr);
        } else {
            chipGender.setVisibility(android.view.View.GONE);
        }

        // 账号 ID
        String userId = SpUtils.getUserId(this);
        if (userId != null && !userId.isEmpty()) {
            tvUserId.setText("ID: " + userId);
        }
    }
}