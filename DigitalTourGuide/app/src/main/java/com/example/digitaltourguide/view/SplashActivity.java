package com.example.digitaltourguide.view;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
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

            if ("admin".equals(lastLoginType) && isAdminValid) {
                startActivity(new Intent(SplashActivity.this, PointManagerActivity.class));
                finish();
                return;
            }
            if ("user".equals(lastLoginType) && isUserValid) {
                startActivity(new Intent(SplashActivity.this, HistoryActivity.class));
                finish();
                return;
            }
            if (isAdminValid) {
                startActivity(new Intent(SplashActivity.this, PointManagerActivity.class));
                finish();
            } else if (isUserValid) {
                startActivity(new Intent(SplashActivity.this, HistoryActivity.class));
                finish();
            } else {
                startActivity(new Intent(SplashActivity.this, UserLoginActivity.class));
                finish();
            }
        }, 2500); // 2.5秒展示启动页，给动画留足时间
    }

    private void startEntryAnimations() {
        // 顶部标题：先设初始透明偏移，再动画
        View topBranding = findViewById(R.id.top_branding);
        topBranding.setAlpha(0f);
        topBranding.setTranslationY(20f);
        topBranding.animate()
                .translationY(0)
                .alpha(1)
                .setDuration(800)
                .setStartDelay(200)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        // 角色形象：先设初始透明偏移，再动画
        View character = findViewById(R.id.character_image);
        character.setAlpha(0f);
        character.setTranslationY(20f);
        character.animate()
                .translationY(0)
                .alpha(1)
                .setDuration(800)
                .setStartDelay(100)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        // 底部加载区：先设初始透明偏移，再动画
        View bottomLoading = findViewById(R.id.bottom_loading);
        bottomLoading.setAlpha(0f);
        bottomLoading.setTranslationY(20f);
        bottomLoading.animate()
                .translationY(0)
                .alpha(1)
                .setDuration(800)
                .setStartDelay(400)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        // 状态点：脉冲动画（缩放）
        View statusDot = findViewById(R.id.status_dot);
        if (statusDot != null) {
            ObjectAnimator pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
                    statusDot,
                    PropertyValuesHolder.ofFloat("scaleX", 1f, 1.3f, 1f),
                    PropertyValuesHolder.ofFloat("scaleY", 1f, 1.3f, 1f),
                    PropertyValuesHolder.ofFloat("alpha", 0.6f, 1f, 0.6f)
            );
            pulseAnimator.setDuration(1500);
            pulseAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            pulseAnimator.setRepeatMode(ObjectAnimator.REVERSE);
            pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            pulseAnimator.setStartDelay(600);
            pulseAnimator.start();
        }
    }
}