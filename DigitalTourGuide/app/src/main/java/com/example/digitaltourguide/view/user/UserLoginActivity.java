package com.example.digitaltourguide.view.user;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.digitaltourguide.R;
import com.example.digitaltourguide.model.BaseResponse;
import com.example.digitaltourguide.model.LoginRequest;
import com.example.digitaltourguide.model.admin.AdminLoginResponse;
import com.example.digitaltourguide.model.user.UserLoginData;
import com.example.digitaltourguide.network.AdminApiService;
import com.example.digitaltourguide.network.ApiService;
import com.example.digitaltourguide.view.admin.AdminRegisterActivity;
import com.example.digitaltourguide.view.admin.PointManagerActivity;
import com.example.digitaltourguide.utils.SpUtils;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UserLoginActivity extends AppCompatActivity {
    private ImageView ivPwdVisibility;
    private static final String TAG = "UserLoginActivity";
    private boolean isPasswordVisible = false;
    private EditText etUsername, etPassword;
    private TextView tvAdminLogin, tvNormalUser, btnRegister, btnLogin;
    private View toggleIndicator;
    private View toggleContainer;

    private boolean isAdminMode = false;
    private int indicatorWidth = 0;
    private int containerPadding = 0;

    private int colorSelected;
    private int colorUnselected;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_userlogin);

        initiView();
        initColors();
        initToggle();

        String username = getIntent().getStringExtra("username");
        if (username != null) {
            etUsername.setText(username);
        }

        // Support direct navigation to admin mode via intent extra
        String loginMode = getIntent().getStringExtra("login_mode");
        if ("admin".equals(loginMode) && !isAdminMode) {
            switchToAdminMode(false); // no animation on initial load
        }

        btnLogin.setOnClickListener(v -> {
            String user = etUsername.getText().toString().trim();
            String pwd = etPassword.getText().toString().trim();

            if (user.isEmpty() || pwd.isEmpty()) {
                Toast.makeText(UserLoginActivity.this, "用户名或密码不能为空", Toast.LENGTH_SHORT).show();
                return;
            }

            login(user, pwd);
        });

        tvAdminLogin.setOnClickListener(v -> {
            if (!isAdminMode) {
                switchToAdminMode(true);
            }
        });

        tvNormalUser.setOnClickListener(v -> {
            if (isAdminMode) {
                switchToUserMode(true);
            }
        });

        btnRegister.setOnClickListener(v -> {
            Intent intent;
            if (isAdminMode) {
                intent = new Intent(UserLoginActivity.this, AdminRegisterActivity.class);
            } else {
                intent = new Intent(UserLoginActivity.this, UserRegisterActivity.class);
            }
            startActivity(intent);
        });
    }

    private void initColors() {
        colorSelected = ContextCompat.getColor(this, R.color.login_on_primary);
        colorUnselected = ContextCompat.getColor(this, R.color.login_on_surface_variant);
    }

    private void initToggle() {
        // Measure and position the indicator once the layout is ready
        toggleContainer.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        toggleContainer.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        int containerWidth = toggleContainer.getWidth();
                        containerPadding = (int) (4 * getResources().getDisplayMetrics().density);
                        indicatorWidth = (containerWidth - containerPadding * 2) / 2;

                        // Set indicator width
                        toggleIndicator.getLayoutParams().width = indicatorWidth;

                        // Position based on current mode (respect prior switchToAdminMode call)
                        float targetX = isAdminMode ? indicatorWidth : 0f;
                        toggleIndicator.setTranslationX(targetX);
                        toggleIndicator.requestLayout();
                    }
                });
    }

    private void switchToAdminMode(boolean animate) {
        isAdminMode = true;
        float targetX = indicatorWidth;

        if (animate && indicatorWidth > 0) {
            animateIndicator(targetX);
            animateTextColor(tvNormalUser, colorSelected, colorUnselected);
            animateTextColor(tvAdminLogin, colorUnselected, colorSelected);
        } else {
            toggleIndicator.setTranslationX(targetX);
            tvNormalUser.setTextColor(colorUnselected);
            tvAdminLogin.setTextColor(colorSelected);
        }
    }

    private void switchToUserMode(boolean animate) {
        isAdminMode = false;

        if (animate && indicatorWidth > 0) {
            animateIndicator(0);
            animateTextColor(tvNormalUser, colorUnselected, colorSelected);
            animateTextColor(tvAdminLogin, colorSelected, colorUnselected);
        } else {
            toggleIndicator.setTranslationX(0);
            tvNormalUser.setTextColor(colorSelected);
            tvAdminLogin.setTextColor(colorUnselected);
        }
    }

    private void animateIndicator(float targetX) {
        toggleIndicator.animate()
                .translationX(targetX)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator(2.0f))
                .start();
    }

    private void animateTextColor(final TextView tv, int fromColor, int toColor) {
        ValueAnimator colorAnim = ValueAnimator.ofObject(new ArgbEvaluator(), fromColor, toColor);
        colorAnim.setDuration(250);
        colorAnim.addUpdateListener(animation -> tv.setTextColor((int) animation.getAnimatedValue()));
        colorAnim.start();
    }

    private void login(String username, String password) {
        LoginRequest request = new LoginRequest(username, password);

        if (isAdminMode) {
            loginAsAdmin(request);
        } else {
            loginAsUser(request);
        }
    }

    private void loginAsUser(LoginRequest request) {
        ApiService.getInstance().login(request).enqueue(new Callback<BaseResponse<UserLoginData>>() {
            @Override
            public void onResponse(@NonNull Call<BaseResponse<UserLoginData>> call,
                                   @NonNull Response<BaseResponse<UserLoginData>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    BaseResponse<UserLoginData> baseResp = response.body();
                    if (baseResp.getCode() == 1 && baseResp.getData() != null) {
                        UserLoginData data = baseResp.getData();
                        String token = data.getToken();
                        String userId = data.getId();
                        long expiresIn = 7 * 24 * 60 * 60;

                        Log.d(TAG, "User Token = " + SpUtils.getUserToken(UserLoginActivity.this));

                        SpUtils.saveUserTokenWithExpire(UserLoginActivity.this, token, expiresIn);
                        SpUtils.saveUserId(UserLoginActivity.this, userId);
                        SpUtils.saveLastLoginType(UserLoginActivity.this, "user");

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

    private void loginAsAdmin(LoginRequest request) {
        AdminApiService.getInstance().login(request).enqueue(new Callback<BaseResponse<AdminLoginResponse.AdminLoginData>>() {
            @Override
            public void onResponse(@NonNull Call<BaseResponse<AdminLoginResponse.AdminLoginData>> call,
                                   @NonNull Response<BaseResponse<AdminLoginResponse.AdminLoginData>> response) {
                Log.d(TAG, "response code: " + response.code());
                if (!response.isSuccessful()) {
                    try {
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "null";
                        Log.e(TAG, "errorBody: " + errorBody);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (response.isSuccessful() && response.body() != null) {
                    BaseResponse<AdminLoginResponse.AdminLoginData> baseResp = response.body();
                    if (baseResp.getCode() == 1 && baseResp.getData() != null) {
                        AdminLoginResponse.AdminLoginData data = baseResp.getData();

                        String token = data.getToken();
                        String adminId = data.getId();

                        long expiresIn = 7 * 24 * 60 * 60;

                        SpUtils.saveAdminTokenWithExpire(UserLoginActivity.this, token, expiresIn);
                        SpUtils.saveAdminId(UserLoginActivity.this, adminId);
                        SpUtils.saveAdminUsername(UserLoginActivity.this,
                                etUsername.getText().toString().trim());
                        SpUtils.saveLastLoginType(UserLoginActivity.this, "admin");

                        Toast.makeText(UserLoginActivity.this, "登录成功", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(UserLoginActivity.this, PointManagerActivity.class));
                        finish();
                    } else {
                        Toast.makeText(UserLoginActivity.this, baseResp.getMsg(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(UserLoginActivity.this, "登录失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<BaseResponse<AdminLoginResponse.AdminLoginData>> call,
                                  @NonNull Throwable t) {
                Toast.makeText(UserLoginActivity.this, "网络异常：" + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void togglePasswordVisibility() {
        String currentText = etPassword.getText().toString();
        int selectionStart = etPassword.getSelectionStart();
        int selectionEnd = etPassword.getSelectionEnd();

        if (isPasswordVisible) {
            etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            ivPwdVisibility.setImageResource(R.drawable.ic_eye_close);
            isPasswordVisible = false;
        } else {
            etPassword.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            ivPwdVisibility.setImageResource(R.drawable.ic_eye_open);
            isPasswordVisible = true;
        }

        etPassword.setText(currentText);
        if (selectionStart >= 0 && selectionEnd >= 0) {
            etPassword.setSelection(selectionStart, selectionEnd);
        } else {
            etPassword.setSelection(etPassword.length());
        }
    }

    private void initiView() {
        tvAdminLogin = findViewById(R.id.tv_admin_login);
        tvNormalUser = findViewById(R.id.tv_normal_user);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        btnRegister = findViewById(R.id.tv_register);
        ivPwdVisibility = findViewById(R.id.iv_pwd_visibility);
        toggleIndicator = findViewById(R.id.toggle_indicator);
        toggleContainer = findViewById(R.id.toggle_container);

        ivPwdVisibility.setOnClickListener(v -> togglePasswordVisibility());
    }
}