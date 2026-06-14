package com.example.digitaltourguide.view.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.digitaltourguide.R;
import com.example.digitaltourguide.model.BaseResponse;
import com.example.digitaltourguide.model.LoginRequest;
import com.example.digitaltourguide.model.admin.AdminLoginResponse;
import com.example.digitaltourguide.network.AdminApiService;
import com.example.digitaltourguide.view.user.UserLoginActivity;
import com.example.digitaltourguide.utils.SpUtils;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminLoginActivity extends AppCompatActivity {
    private static final String TAG="AdminLoginActivity";
    private ImageView ivPwdVisibility;
    private boolean isPasswordVisible = false;
    private EditText etUsername,etPassword;
    private TextView tvUserLogin,btnRegister;
    private TextView btnLogin;
    private static final String SP_NAME = "user_info";
    private static final String LOGIN_URL = "https://ai.guying.xyz/ai-project/v1/admins/login";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adminlogin);

        initiView();
        String username = getIntent().getStringExtra("username");
        if (username != null) {
            etUsername.setText(username);
        }

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username = etUsername.getText().toString().trim();
                String password = etPassword.getText().toString().trim();

                if (username.isEmpty() || password.isEmpty()) {
                    Toast.makeText(AdminLoginActivity.this, "用户名或密码不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }

                login(username, password);
            }
        });

        tvUserLogin.setOnClickListener(v -> {
            Intent intent = new Intent(AdminLoginActivity.this, UserLoginActivity.class);
            startActivity(intent);
        });
        btnRegister.setOnClickListener(v -> {
            Intent intent = new Intent(AdminLoginActivity.this, AdminRegisterActivity.class);
            startActivity(intent);
        });
    }

    private void login(String username, String password) {
        // 请求体
        LoginRequest request = new LoginRequest(username, password);

        // 调用登录接口
        AdminApiService.getInstance().login(request).enqueue(new Callback<BaseResponse<AdminLoginResponse.AdminLoginData>>() {
            @Override
            public void onResponse(@NonNull Call<BaseResponse<AdminLoginResponse.AdminLoginData>> call,
                                   @NonNull Response<BaseResponse<AdminLoginResponse.AdminLoginData>> response) {
                Log.d(TAG, "response code: " + response.code());
                if (!response.isSuccessful()) {
                    try {
                        String errorBody = response.errorBody().string();
                        Log.e(TAG, "errorBody: " + errorBody);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (response.isSuccessful() && response.body() != null) {
                    BaseResponse<AdminLoginResponse.AdminLoginData> baseResp = response.body();
                    if (baseResp.getCode() == 1 && baseResp.getData() != null) {
                        AdminLoginResponse.AdminLoginData data = baseResp.getData();

                        String token = data.getToken();
                        String adminId = data.getId();

                        // 计算有效期（默认7天，若需从JWT解析可调用 getExpiresInFromToken(token)）
                        long expiresIn = 7 * 24 * 60 * 60; // 7天，单位秒

                        // ===== 核心：存储Token与过期时间（实现登录记忆）=====
                        SpUtils.saveAdminTokenWithExpire(AdminLoginActivity.this, token, expiresIn);
                        SpUtils.saveAdminId(AdminLoginActivity.this, adminId);
                        SpUtils.saveLastLoginType(AdminLoginActivity.this, "admin");

                        Toast.makeText(AdminLoginActivity.this, "登录成功", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(AdminLoginActivity.this, PointManagerActivity.class));
                        finish();
                    } else {
                        Toast.makeText(AdminLoginActivity.this, baseResp.getMsg(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(AdminLoginActivity.this, "登录失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<BaseResponse<AdminLoginResponse.AdminLoginData>> call, @NonNull Throwable t) {
                Toast.makeText(AdminLoginActivity.this, "网络异常：" + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void togglePasswordVisibility() {
// 保存当前文本和光标位置
        String currentText = etPassword.getText().toString();
        int selectionStart = etPassword.getSelectionStart();
        int selectionEnd = etPassword.getSelectionEnd();

        if (isPasswordVisible) {
            // 当前可见 → 隐藏密码（显示为 *）
            etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            ivPwdVisibility.setImageResource(R.drawable.ic_eye_close);
            isPasswordVisible = false;
        } else {
            // 当前隐藏 → 显示密码明文
            etPassword.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            ivPwdVisibility.setImageResource(R.drawable.ic_eye_open);
            isPasswordVisible = true;
        }

        // 恢复文本和光标位置
        etPassword.setText(currentText);
        if (selectionStart >= 0 && selectionEnd >= 0) {
            etPassword.setSelection(selectionStart, selectionEnd);
        } else {
            etPassword.setSelection(etPassword.length());
        }
    }
    private void initiView() {
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        btnRegister = findViewById(R.id.tv_register);
        tvUserLogin=findViewById(R.id.tv_user_login);
        ivPwdVisibility = findViewById(R.id.iv_pwd_visibility);
        ivPwdVisibility.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePasswordVisibility();
            }

        });
    }
}
