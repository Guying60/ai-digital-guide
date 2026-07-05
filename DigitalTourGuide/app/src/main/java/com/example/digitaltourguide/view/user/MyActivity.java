package com.example.digitaltourguide.view.user;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

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

        // 状态栏透明，让顶部渐变延伸到状态栏后方
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);

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
        // ── 头部：点击进入个人信息编辑（真功能） ──
        findViewById(R.id.header_container).setOnClickListener(v ->
                startActivityForResult(new Intent(this, ProfileInfoActivity.class), REQ_PROFILE)
        );

        // ── 右上角设置（假按钮） ──
        findViewById(R.id.btn_settings).setOnClickListener(v ->
                Toast.makeText(this, "功能开发中", Toast.LENGTH_SHORT).show()
        );

        // ── 我的服务 — 我的评价（真功能 → MyJudgeActivity） ──
        findViewById(R.id.btn_my_reviews).setOnClickListener(v ->
                startActivity(new Intent(this, MyJudgeActivity.class))
        );

        // ── 我的服务 — 偏好设置（真功能 → MyPerActivity） ──
        findViewById(R.id.card_preferences).setOnClickListener(v ->
                startActivity(new Intent(this, MyPerActivity.class))
        );

        // ── 设置列表 — 景点地图（真功能 → NearbyMapActivity） ──
        findViewById(R.id.btn_nearby_map).setOnClickListener(v ->
                startActivity(new Intent(this, NearbyMapActivity.class))
        );

        // ── 设置列表 — 账号管理（真功能 → ProfileInfoActivity） ──
        findViewById(R.id.btn_account_mgmt).setOnClickListener(v ->
                startActivityForResult(new Intent(this, ProfileInfoActivity.class), REQ_PROFILE)
        );

        // ── 设置列表 — 清除缓存（假按钮） ──
        findViewById(R.id.btn_cache).setOnClickListener(v ->
                Toast.makeText(this, "已清除缓存", Toast.LENGTH_SHORT).show());

        // ── 设置列表 — 隐私政策（假按钮） ──
        findViewById(R.id.btn_privacy).setOnClickListener(v ->
                Toast.makeText(this, "功能开发中", Toast.LENGTH_SHORT).show());

        // ── 设置列表 — 关于我们（真功能） ──
        findViewById(R.id.btn_about).setOnClickListener(v -> showAboutDialog());

        // ── 设置列表 — 退出登录（真功能） ──
        findViewById(R.id.btn_logout).setOnClickListener(v -> showLogoutDialog());

        // ── 底部导航（真功能） ──
        tvHistory.setOnClickListener(v -> {
            Intent intent = new Intent(this, HistoryActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        });
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
                    bindUserInfo(resp.getData());
                }
            }

            @Override
            public void onFailure(Call<BaseResponse<UpdateUserRequest>> call, Throwable t) {
                Log.e(TAG, "加载用户信息失败", t);
            }
        });
    }

    private void bindUserInfo(UpdateUserRequest data) {
        if (data.getNickname() != null && !data.getNickname().isEmpty()) {
            tvNickname.setText(data.getNickname());
        }

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
                Glide.with(this).load(cached).circleCrop().into(ivAvatar);
            }
        }

        if (data.getGender() != null) {
            chipGender.setVisibility(View.VISIBLE);
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
                    chipGender.setVisibility(View.GONE);
                    break;
            }
            chipGender.setText(genderStr);
        } else {
            chipGender.setVisibility(View.GONE);
        }

        String userId = SpUtils.getUserId(this);
        if (userId != null && !userId.isEmpty()) {
            tvUserId.setText("ID: " + userId);
        }
    }
}