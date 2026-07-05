package com.example.digitaltourguide.view.user;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.view.WindowCompat;
import androidx.core.widget.ImageViewCompat;

import com.bumptech.glide.Glide;
import com.example.digitaltourguide.R;
import com.example.digitaltourguide.model.BaseResponse;
import com.example.digitaltourguide.model.user.UpdateUserRequest;
import com.example.digitaltourguide.network.ApiService;
import com.example.digitaltourguide.network.RetrofitClient;
import com.example.digitaltourguide.utils.SpUtils;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MyActivity extends AppCompatActivity {

    private static final String TAG = "MyActivity";
    private static final int REQ_PROFILE = 2001;

    private static final String KEY_TITLE = "title";
    private static final String KEY_ICON = "icon";
    private static final int TYPE_NORMAL = 0;
    private static final int TYPE_NIGHT_SWITCH = 1;
    private static final String KEY_TYPE = "type";

    private ApiService apiService;
    private ImageView ivAvatar;
    private TextView tvNickname, tvUserId;
    private LinearLayout tvHistory, tvMine;
    private Chip chipGender;
    private SwitchCompat switchNight;

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
        buildExtraGrid();
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

        // ── 核心指标卡：收藏/积分/VIP（假数据入口） ──
        findViewById(R.id.metric_favorite).setOnClickListener(v ->
                Toast.makeText(this, "功能开发中", Toast.LENGTH_SHORT).show());
        findViewById(R.id.metric_points).setOnClickListener(v ->
                Toast.makeText(this, "功能开发中", Toast.LENGTH_SHORT).show());
        findViewById(R.id.metric_vip).setOnClickListener(v ->
                Toast.makeText(this, "功能开发中", Toast.LENGTH_SHORT).show());

        // ── 我的服务 — 我的评价（真功能 → MyJudgeActivity） ──
        findViewById(R.id.btn_my_reviews).setOnClickListener(v ->
                startActivity(new Intent(this, MyJudgeActivity.class))
        );

        // ── 我的服务 — 偏好设置（真功能 → MyPerActivity） ──
        findViewById(R.id.card_preferences).setOnClickListener(v ->
                startActivity(new Intent(this, MyPerActivity.class))
        );

        // ── 设置列表（真功能） ──
        findViewById(R.id.btn_about).setOnClickListener(v -> showAboutDialog());
        findViewById(R.id.btn_logout).setOnClickListener(v -> showLogoutDialog());

        // ── 设置列表（假按钮） ──
        findViewById(R.id.btn_cache).setOnClickListener(v ->
                Toast.makeText(this, "已清除缓存", Toast.LENGTH_SHORT).show());
        findViewById(R.id.btn_privacy).setOnClickListener(v ->
                Toast.makeText(this, "功能开发中", Toast.LENGTH_SHORT).show());

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

    // ════════════════════════════════════════════════════════════════
    //  第四段：更多功能网格（1 行 × 4 列）— 真功能 + 假入口混合
    // ════════════════════════════════════════════════════════════════
    private List<Map<String, Object>> buildExtraGridData() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(extraItem("景点地图", R.drawable.ic_location_pin, TYPE_NORMAL)); // 真
        list.add(extraItem("账号管理", R.drawable.ic_person_outline, TYPE_NORMAL)); // 真
        list.add(extraItem("我的订单", R.drawable.ic_order, TYPE_NORMAL));          // 假
        list.add(extraItem("夜间模式", R.drawable.ic_night, TYPE_NIGHT_SWITCH));    // 假（带开关）
        return list;
    }

    private Map<String, Object> extraItem(String title, int icon, int type) {
        Map<String, Object> m = new HashMap<>();
        m.put(KEY_TITLE, title);
        m.put(KEY_ICON, icon);
        m.put(KEY_TYPE, type);
        return m;
    }

    private void buildExtraGrid() {
        LinearLayout grid = findViewById(R.id.grid_functions);
        grid.removeAllViews();
        List<Map<String, Object>> data = buildExtraGridData();

        LayoutInflater inflater = LayoutInflater.from(this);
        LinearLayout row = new LinearLayout(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setWeightSum(data.size());

        for (Map<String, Object> item : data) {
            View cell = inflater.inflate(R.layout.item_function_grid, row, false);
            ImageView icon = cell.findViewById(R.id.iv_func_icon);
            TextView name = cell.findViewById(R.id.tv_func_name);
            icon.setImageResource((int) item.get(KEY_ICON));
            ImageViewCompat.setImageTintList(icon,
                    ColorStateList.valueOf(getColor(R.color.profile_primary)));
            name.setText((String) item.get(KEY_TITLE));

            String title = (String) item.get(KEY_TITLE);
            int type = (int) item.get(KEY_TYPE);

            if (type == TYPE_NIGHT_SWITCH) {
                setupNightModeCell(cell);
            } else {
                cell.setOnClickListener(v -> handleExtraClick(title));
            }
            row.addView(cell);
        }
        grid.addView(row);
    }

    private void handleExtraClick(String title) {
        switch (title) {
            case "景点地图":
                startActivity(new Intent(this, NearbyMapActivity.class));
                break;
            case "账号管理":
                startActivityForResult(new Intent(this, ProfileInfoActivity.class), REQ_PROFILE);
                break;
            default:
                Toast.makeText(this, "功能开发中", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    /** 夜间模式格子：SwitchCompat + 切换主题 */
    private void setupNightModeCell(View cell) {
        ViewGroup parent = (ViewGroup) cell;
        ImageView icon = cell.findViewById(R.id.iv_func_icon);

        SwitchCompat sw = new SwitchCompat(this);
        sw.setId(View.generateViewId());
        sw.setClickable(false);
        sw.setFocusable(false);
        sw.setThumbTintList(ColorStateList.valueOf(getColor(R.color.profile_primary)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sw.setLayoutParams(lp);

        int idx = parent.indexOfChild(icon);
        parent.removeView(icon);
        parent.addView(sw, idx);
        switchNight = sw;

        cell.setOnClickListener(v -> {
            sw.setChecked(!sw.isChecked());
            AppCompatDelegate.setDefaultNightMode(
                    sw.isChecked() ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            Toast.makeText(this, sw.isChecked() ? "已切换至夜间模式" : "已切换至日间模式",
                    Toast.LENGTH_SHORT).show();
        });
    }

    // ════════════════════════════════════════════════════════════════
    //  对话框
    // ════════════════════════════════════════════════════════════════
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

    // ════════════════════════════════════════════════════════════════
    //  网络请求 & 数据绑定
    // ════════════════════════════════════════════════════════════════
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
        // 昵称
        if (data.getNickname() != null && !data.getNickname().isEmpty()) {
            tvNickname.setText(data.getNickname());
        }

        // 头像
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

        // 性别（头部渐变上显示）
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
                    // 头像区白色描边已突出，chip 用半透白底深色字
                    chipGender.setChipBackgroundColorResource(R.color.profile_glass_white);
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

        // 账号 ID（签名下方可见）
        String userId = SpUtils.getUserId(this);
        if (userId != null && !userId.isEmpty()) {
            tvUserId.setText("ID: " + userId);
        }
    }
}