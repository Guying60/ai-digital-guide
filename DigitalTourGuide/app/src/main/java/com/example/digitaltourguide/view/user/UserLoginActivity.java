package com.example.digitaltourguide.view.user;

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
import com.example.digitaltourguide.model.user.UserLoginData;
import com.example.digitaltourguide.network.ApiService;
import com.example.digitaltourguide.view.admin.AdminLoginActivity;
import com.example.digitaltourguide.utils.SpUtils;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UserLoginActivity extends AppCompatActivity {
    private ImageView ivPwdVisibility;
    private static final String TAG="UserLoginActivity";
    private boolean isPasswordVisible = false;
    private EditText etUsername,etPassword;
    private TextView tvAdminLogin,btnRegister,btnLogin;
    private static final String SP_NAME = "user_info";
    private static final String LOGIN_URL = "https://ai.guying.xyz/ai-project/v1/users/login";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_userlogin);

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
                    Toast.makeText(UserLoginActivity.this, "用户名或密码不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }

                login(username, password);
            }
        });
        tvAdminLogin.setOnClickListener(v -> {
            // 跳转到管理员登录页面（替换成你自己的Activity类）
            Intent intent = new Intent(UserLoginActivity.this, AdminLoginActivity.class);
            startActivity(intent);
        });
        btnRegister.setOnClickListener(v -> {
            Intent intent = new Intent(UserLoginActivity.this, UserRegisterActivity.class);
            startActivity(intent);
        });

    }

    private void login(String username, String password) {
        LoginRequest request = new LoginRequest(username, password);

        // ✅ 正确写法：明确指定泛型类型
        ApiService.getInstance().login(request).enqueue(new Callback<BaseResponse<UserLoginData>>() {
            @Override
            public void onResponse(@NonNull Call<BaseResponse<UserLoginData>> call,
                                   @NonNull Response<BaseResponse<UserLoginData>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    BaseResponse<UserLoginData> baseResp = response.body();
                    if (baseResp.getCode() == 1 && baseResp.getData() != null) {
                        UserLoginData data = baseResp.getData();
                        String token = data.getToken();      // ✅ 正确方法名
                        String userId = data.getId();
                        long expiresIn = 7 * 24 * 60 * 60;   // 默认7天

                        Log.d(TAG, "User Token = " + SpUtils.getUserToken(UserLoginActivity.this));

                        SpUtils.saveUserTokenWithExpire(UserLoginActivity.this, token, expiresIn);
                        SpUtils.saveUserId(UserLoginActivity.this, userId);
                        SpUtils.saveLastLoginType(UserLoginActivity.this,"user");

                        Toast.makeText(UserLoginActivity.this, "登录成功", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(UserLoginActivity.this, HistoryActivity.class));
                        finish();
                    } else {
                        Toast.makeText(UserLoginActivity.this, baseResp.getMsg(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(UserLoginActivity.this, "登录失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<BaseResponse<UserLoginData>> call,
                                  @NonNull Throwable t) {
                Log.e(TAG, "网络请求失败", t);
                Toast.makeText(UserLoginActivity.this, "网络异常：" + t.getMessage(), Toast.LENGTH_SHORT).show();
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
        tvAdminLogin = findViewById(R.id.tv_admin_login);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        btnRegister = findViewById(R.id.tv_register);
        ivPwdVisibility = findViewById(R.id.iv_pwd_visibility);
        ivPwdVisibility.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePasswordVisibility();
            }

        });
    }
}
