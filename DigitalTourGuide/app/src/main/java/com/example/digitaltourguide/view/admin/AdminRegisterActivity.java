package com.example.digitaltourguide.view.admin;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.digitaltourguide.R;
import com.example.digitaltourguide.model.BaseResponse;
import com.example.digitaltourguide.model.RegisterRequest;
import com.example.digitaltourguide.model.user.RegisterResponse;
import com.example.digitaltourguide.network.AdminApiService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminRegisterActivity extends AppCompatActivity {
    private EditText etUsername, etPassword, etNickname,etConfirmPassword;
    private TextView btnRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_register);

        initView();

        btnRegister.setOnClickListener(v -> doRegister());
    }

    private void doRegister() {
        // 1. 获取输入内容
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();
        String nickname = etNickname.getText().toString().trim();

        // 2. 简单校验
        if (username.isEmpty() || password.isEmpty() || nickname.isEmpty()) {
            Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show();
            return;
        }

        // 3. 构建注册请求体
        RegisterRequest request = new RegisterRequest(username, password,confirmPassword, nickname);

        // 4. 调用 Retrofit 注册接口（泛型修正为RegisterResponse）
        AdminApiService.getInstance().register(request).enqueue(new Callback<BaseResponse<RegisterResponse>>() {
            @Override
            public void onResponse(@NonNull Call<BaseResponse<RegisterResponse>> call,
                                   @NonNull Response<BaseResponse<RegisterResponse>> response) {
                // 5. 处理响应
                if (response.isSuccessful() && response.body() != null) {
                    BaseResponse<RegisterResponse> baseResponse = response.body();
                    if (baseResponse.getCode() == 1 && baseResponse.getData() != null) {
                        // 注册成功
                        String username = baseResponse.getData().getUsername();
                        if (username == null) { // 兜底：防止data里username为null
                            Toast.makeText(AdminRegisterActivity.this, "注册成功(用户名空)", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(AdminRegisterActivity.this, "注册成功！", Toast.LENGTH_SHORT).show();
                        }


                        // 6. 跳转登录页，回传用户名
                        Intent intent = new Intent(AdminRegisterActivity.this, com.example.digitaltourguide.view.user.UserLoginActivity.class);
                        intent.putExtra("username", username);
                        intent.putExtra("login_mode", "admin");
                        startActivity(intent);
                    } else {
                        // 后端返回非成功码
                        Toast.makeText(AdminRegisterActivity.this,
                                baseResponse.getMsg(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // 网络错误/状态码非2xx
                    Toast.makeText(AdminRegisterActivity.this, "请求失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<BaseResponse<RegisterResponse>> call,
                                  @NonNull Throwable t) {
                // 网络异常
                Toast.makeText(AdminRegisterActivity.this, "网络异常：" + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initView() {
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        etNickname = findViewById(R.id.et_nickname);
        btnRegister = findViewById(R.id.btn_register);
    }
}
