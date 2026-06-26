package com.example.digitaltourguide.view.user;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.digitaltourguide.R;
import com.example.digitaltourguide.model.BaseResponse;
import com.example.digitaltourguide.model.user.UpdateUserRequest;
import com.example.digitaltourguide.network.ApiService;
import com.example.digitaltourguide.network.RetrofitClient;
import com.example.digitaltourguide.utils.SpUtils;
import com.google.android.material.chip.Chip;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MyActivity extends AppCompatActivity {

    private static final String TAG = "MyActivity";

    private ApiService apiService;
    private ImageView ivAvatar;
    private TextView tvNickname, tvUserId;
    private LinearLayout tvHistory;
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

    private void initViews() {
        ivAvatar = findViewById(R.id.iv_avatar);
        tvNickname = findViewById(R.id.tv_nickname);
        tvUserId = findViewById(R.id.tv_user_id);
        chipGender = findViewById(R.id.chip_gender);
        tvHistory = findViewById(R.id.tv_history);
    }

    private void initClickListeners() {
        // 顶部用户信息卡片 → ProfileInfoActivity
        findViewById(R.id.card_user_info).setOnClickListener(v ->
                startActivity(new Intent(this, ProfileInfoActivity.class))
        );

        // 我的评价 → MyJudgeActivity
        findViewById(R.id.btn_my_reviews).setOnClickListener(v ->
                startActivity(new Intent(this, MyJudgeActivity.class))
        );

        // 偏好设置 → MyPerActivity
        findViewById(R.id.card_preferences).setOnClickListener(v ->
                startActivity(new Intent(this, MyPerActivity.class))
        );

        tvHistory.setOnClickListener(v->
            startActivity(new Intent(this, HistoryActivity.class))
        );
    }

    /**
     * 加载用户信息（1.3 GET /users/userInfo）
     * AuthInterceptor 自动注入用户 Token，无需手动加 Header
     */
    private void loadUserInfo() {
        if (apiService == null) {
            apiService = RetrofitClient.getInstance().create(ApiService.class);
        }

        apiService.getUserInfo().enqueue(new Callback<BaseResponse<UpdateUserRequest>>() {
            @Override
            public void onResponse(Call<BaseResponse<UpdateUserRequest>> call,
                                   Response<BaseResponse<UpdateUserRequest>> response) {
                if (!response.isSuccessful() || response.body() == null) return;
                BaseResponse<UpdateUserRequest> resp = response.body();
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
        // 昵称
        if (data.getNickname() != null && !data.getNickname().isEmpty()) {
            tvNickname.setText(data.getNickname());
        }

        // 头像
        if (data.getAvatarUrl() != null && !data.getAvatarUrl().isEmpty()) {
            Glide.with(this)
                    .load(data.getAvatarUrl())
                    .circleCrop()
                    .placeholder(R.drawable.ic_person_filled)
                    .into(ivAvatar);
        } else {
            // 服务端返回 null，用本地缓存的头像兜底
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

        // 账号 ID（从 SpUtils 读取）
        String userId = SpUtils.getUserId(this);
        if (userId != null && !userId.isEmpty()) {
            tvUserId.setText("ID: " + userId);
        }
    }
}