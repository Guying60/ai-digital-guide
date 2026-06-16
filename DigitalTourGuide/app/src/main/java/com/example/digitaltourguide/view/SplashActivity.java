package com.example.digitaltourguide.view;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.digitaltourguide.R;
import com.example.digitaltourguide.view.admin.PointManagerActivity;
import com.example.digitaltourguide.view.user.HistoryActivity;
import com.example.digitaltourguide.view.user.UserLoginActivity;
import com.example.digitaltourguide.utils.SpUtils;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // 启动入场动画
        startEntryAnimations();

        // 延迟后跳转
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            String lastLoginType = SpUtils.getLastLoginType(SplashActivity.this);
            boolean isAdminValid = SpUtils.isAdminTokenValid(SplashActivity.this);
            boolean isUserValid = SpUtils.isUserTokenValid(SplashActivity.this);

            Intent intent;
            if ("admin".equals(lastLoginType) && isAdminValid) {
                intent = new Intent(SplashActivity.this, PointManagerActivity.class);
            } else if ("user".equals(lastLoginType) && isUserValid) {
                intent = new Intent(SplashActivity.this, HistoryActivity.class);
            } else if (isAdminValid) {
                intent = new Intent(SplashActivity.this, PointManagerActivity.class);
            } else if (isUserValid) {
                intent = new Intent(SplashActivity.this, HistoryActivity.class);
            } else {
                intent = new Intent(SplashActivity.this, UserLoginActivity.class);
            }

            startActivity(intent);
            // Splash 专用过渡：目标页淡入+放大、Splash 淡出+放大
            overridePendingTransition(R.anim.activity_fade_in, R.anim.splash_fade_out);
            finish();
        }, 2800); // 2.8秒展示启动页，让动画完整播放
    }

    private void startEntryAnimations() {
        DecelerateInterpolator decelerate = new DecelerateInterpolator(1.4f);

        // ---- 中央 Logo + 文字：轻柔淡入 + 上移 ----
        View titleBlock = findViewById(R.id.title_block);
        if (titleBlock != null) {
            titleBlock.setAlpha(0f);
            titleBlock.setTranslationY(28f);
            titleBlock.animate()
                    .translationY(0)
                    .alpha(1f)
                    .setDuration(900)
                    .setStartDelay(120)
                    .setInterpolator(decelerate)
                    .start();
        }

        // ---- 底部加载区：稍后淡入 ----
        View bottomSection = findViewById(R.id.bottom_section);
        if (bottomSection != null) {
            bottomSection.setAlpha(0f);
            bottomSection.animate()
                    .alpha(1f)
                    .setDuration(800)
                    .setStartDelay(600)
                    .setInterpolator(decelerate)
                    .start();
        }
    }
}