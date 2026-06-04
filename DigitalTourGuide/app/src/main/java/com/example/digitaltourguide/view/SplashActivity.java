package com.example.digitaltourguide.view;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

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

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            String lastLoginType=SpUtils.getLastLoginType(SplashActivity.this);
            boolean isAdminValid=SpUtils.isAdminTokenValid(SplashActivity.this);
            boolean isUserValid=SpUtils.isUserTokenValid(SplashActivity.this);

            if("admin".equals(lastLoginType) && isAdminValid) {
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
                // 4. 都没有有效 Token，跳转登录页
                startActivity(new Intent(SplashActivity.this, UserLoginActivity.class));
                finish();
            }
            }, 1000);//1秒延迟展示启动页
    }
}
