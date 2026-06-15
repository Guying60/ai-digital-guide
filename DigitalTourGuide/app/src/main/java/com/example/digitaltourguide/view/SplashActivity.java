package com.example.digitaltourguide.view;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
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

        // 启动装饰元素的持续浮动动画
        startFloatingAnimations();

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
        OvershootInterpolator overshoot = new OvershootInterpolator(0.6f);
        DecelerateInterpolator decelerate = new DecelerateInterpolator(1.2f);

        // ---- 顶部品牌区：弹跳入场 ----
        View topBranding = findViewById(R.id.top_branding);
        topBranding.setAlpha(0f);
        topBranding.setTranslationY(-30f);
        topBranding.animate()
                .translationY(0)
                .alpha(1f)
                .setDuration(700)
                .setStartDelay(150)
                .setInterpolator(overshoot)
                .start();

        // ---- 应用图标：缩小放大弹跳 ----
        View appIcon = findViewById(R.id.app_icon_badge);
        if (appIcon != null) {
            appIcon.setScaleX(0.4f);
            appIcon.setScaleY(0.4f);
            appIcon.setAlpha(0f);
            appIcon.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(600)
                    .setStartDelay(100)
                    .setInterpolator(overshoot)
                    .start();
        }

        // ---- 角色形象卡片：缩放 + 淡入 ----
        View characterFrame = findViewById(R.id.character_frame);
        characterFrame.setAlpha(0f);
        characterFrame.setScaleX(0.7f);
        characterFrame.setScaleY(0.7f);
        characterFrame.setTranslationY(30f);
        characterFrame.animate()
                .translationY(0)
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(800)
                .setStartDelay(250)
                .setInterpolator(overshoot)
                .start();

        // ---- 底部区域：淡入 + 上移 ----
        View bottomSection = findViewById(R.id.bottom_section);
        bottomSection.setAlpha(0f);
        bottomSection.setTranslationY(40f);
        bottomSection.animate()
                .translationY(0)
                .alpha(1f)
                .setDuration(700)
                .setStartDelay(500)
                .setInterpolator(decelerate)
                .start();

        // ---- 底部状态卡片：渐变出现 ----
        View statusBadge = findViewById(R.id.status_badge);
        if (statusBadge != null) {
            statusBadge.setAlpha(0f);
            statusBadge.animate()
                    .alpha(1f)
                    .setDuration(500)
                    .setStartDelay(700)
                    .setInterpolator(decelerate)
                    .start();
        }

        // ---- 状态点：脉冲动画 ----
        View statusDot = findViewById(R.id.status_dot);
        if (statusDot != null) {
            ObjectAnimator pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
                    statusDot,
                    PropertyValuesHolder.ofFloat("scaleX", 1f, 1.4f, 1f),
                    PropertyValuesHolder.ofFloat("scaleY", 1f, 1.4f, 1f),
                    PropertyValuesHolder.ofFloat("alpha", 0.5f, 1f, 0.5f)
            );
            pulseAnimator.setDuration(1600);
            pulseAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            pulseAnimator.setRepeatMode(ObjectAnimator.REVERSE);
            pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            pulseAnimator.setStartDelay(900);
            pulseAnimator.start();
        }
    }

    /**
     * 装饰元素的持续浮动动画，增加画面灵动感
     */
    private void startFloatingAnimations() {
        // 顶部右侧圆环：缓慢旋转
        View decoRing1 = findViewById(R.id.deco_ring_1);
        if (decoRing1 != null) {
            ObjectAnimator ringRotator = ObjectAnimator.ofFloat(decoRing1, "rotation", 0f, 360f);
            ringRotator.setDuration(8000);
            ringRotator.setRepeatCount(ObjectAnimator.INFINITE);
            ringRotator.setInterpolator(new AccelerateDecelerateInterpolator());
            ringRotator.start();
        }

        // 顶部左侧菱形：缓慢浮动
        View decoDiamond1 = findViewById(R.id.deco_diamond_1);
        if (decoDiamond1 != null) {
            ObjectAnimator diamondFloat = ObjectAnimator.ofFloat(decoDiamond1, "translationY", 0f, -12f, 0f);
            diamondFloat.setDuration(3000);
            diamondFloat.setRepeatCount(ObjectAnimator.INFINITE);
            diamondFloat.setRepeatMode(ObjectAnimator.REVERSE);
            diamondFloat.setInterpolator(new AccelerateDecelerateInterpolator());
            diamondFloat.setStartDelay(200);
            diamondFloat.start();

            ObjectAnimator diamondRotate = ObjectAnimator.ofFloat(decoDiamond1, "rotation", 15f, 30f, 15f);
            diamondRotate.setDuration(4000);
            diamondRotate.setRepeatCount(ObjectAnimator.INFINITE);
            diamondRotate.setRepeatMode(ObjectAnimator.REVERSE);
            diamondRotate.setInterpolator(new AccelerateDecelerateInterpolator());
            diamondRotate.start();
        }

        // 中间左侧小圆：缓慢浮动
        View decoCircle1 = findViewById(R.id.deco_circle_1);
        if (decoCircle1 != null) {
            ObjectAnimator circleFloat = ObjectAnimator.ofFloat(decoCircle1, "translationX", 0f, 8f, 0f);
            circleFloat.setDuration(2500);
            circleFloat.setRepeatCount(ObjectAnimator.INFINITE);
            circleFloat.setRepeatMode(ObjectAnimator.REVERSE);
            circleFloat.setInterpolator(new AccelerateDecelerateInterpolator());
            circleFloat.setStartDelay(300);
            circleFloat.start();
        }

        // 中间右侧菱形：缓慢浮动
        View decoDiamond2 = findViewById(R.id.deco_diamond_2);
        if (decoDiamond2 != null) {
            ObjectAnimator diamond2Float = ObjectAnimator.ofFloat(decoDiamond2, "translationY", 0f, -10f, 0f);
            diamond2Float.setDuration(3500);
            diamond2Float.setRepeatCount(ObjectAnimator.INFINITE);
            diamond2Float.setRepeatMode(ObjectAnimator.REVERSE);
            diamond2Float.setInterpolator(new AccelerateDecelerateInterpolator());
            diamond2Float.setStartDelay(400);
            diamond2Float.start();
        }

        // 中间右侧圆环：缓慢旋转
        View decoRing2 = findViewById(R.id.deco_ring_2);
        if (decoRing2 != null) {
            ObjectAnimator ring2Rotator = ObjectAnimator.ofFloat(decoRing2, "rotation", 0f, -360f);
            ring2Rotator.setDuration(10000);
            ring2Rotator.setRepeatCount(ObjectAnimator.INFINITE);
            ring2Rotator.setInterpolator(new AccelerateDecelerateInterpolator());
            ring2Rotator.setStartDelay(500);
            ring2Rotator.start();
        }
    }
}