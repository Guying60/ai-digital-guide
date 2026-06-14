package com.example.digitaltourguide.view.user;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.digitaltourguide.R;
import com.example.digitaltourguide.model.BaseResponse;
import com.example.digitaltourguide.model.user.UpdateUserRequest;
import com.example.digitaltourguide.network.ApiService;
import com.example.digitaltourguide.network.RetrofitClient;
import com.example.digitaltourguide.utils.SpUtils;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;


import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class ProfileInfoActivity extends AppCompatActivity {
    private int genderCode;
    String token;
    private Button btnOutLogin;
    private static final String TAG = "MyActivity";
    private ImageView ivAvatar;
    private TextView tvAge, tvNickname, tvGender;
    private String currentGender = "性别";
    private ActivityResultLauncher<String> imagePickerLauncher;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_info);

        SpUtils.init(this);

        initView();
        initListener();
        loadUserInfo();

        // 注册图片选择器
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    Log.d(TAG, "选择了图片，uri=" + uri);
                    if (uri != null) {
                        uploadAvatar(uri);
                    } else {
                        Log.d(TAG, "未选择图片");
                    }
                }
        );
    }

    private void initListener() {

        // 返回按钮 → 回到上一个页面
        ((com.google.android.material.appbar.MaterialToolbar) findViewById(R.id.topAppBar))
                .setNavigationOnClickListener(v -> finish());

        // 退出登录
        btnOutLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, UserLoginActivity.class));
        });

        // 性别
        tvGender.setOnClickListener(v -> showGenderSelectDialog());

        // 昵称
        tvNickname.setOnClickListener(v -> showEditNicknameDialog());

        // 年龄
        tvAge.setOnClickListener(v -> showEditAgeDialog());

        // 头像
        ivAvatar.setOnClickListener(v ->
                imagePickerLauncher.launch("image/*")
        );

        // 导游偏好 → 跳转到 MyPerActivity
        // ProfileInfoActivity 是编辑资料页，不需要偏好导航入口
    }

    // ===================== 以下方法保持不变 =====================

    // 1.3
    private void loadUserInfo() {
        String token = SpUtils.getUserToken(ProfileInfoActivity.this);
        Log.d("MyActivity", "User Token = " + token);

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("https://ai.guying.xyz/ai-project/v1/users/userInfo")
                .header("Authorization", "Bearer " + token)
                .get()
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(ProfileInfoActivity.this, "网络异常", Toast.LENGTH_SHORT).show());
                Log.e(TAG, "loadUserInfo 请求失败", e);
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "loadUserInfo 响应码: " + response.code());
                    runOnUiThread(() -> Toast.makeText(ProfileInfoActivity.this, "获取用户信息失败", Toast.LENGTH_SHORT).show());
                    return;
                }

                String body = response.body().string();
                Log.d(TAG, "loadUserInfo 响应: " + body);

                try {
                    Gson gson = new Gson();
                    BaseResponse<UpdateUserRequest> resp = gson.fromJson(body,
                            new com.google.gson.reflect.TypeToken<BaseResponse<UpdateUserRequest>>() {
                            }.getType());

                    if (resp.getCode() == 1 && resp.getData() != null) {
                        UpdateUserRequest data = resp.getData();
                        runOnUiThread(() -> {
                            if (data.getNickname() != null) {
                                tvNickname.setText(data.getNickname());
                            } else {
                                tvNickname.setText("未设置昵称");
                            }

                            if (data.getAge() != null) {
                                tvAge.setText(String.valueOf(data.getAge()));
                            } else {
                                tvAge.setText("未设置年龄");
                            }

                            if (data.getGender() != null) {
                                String genderStr;
                                switch (data.getGender()) {
                                    case 0:
                                        genderStr = "女";
                                        break;
                                    case 1:
                                        genderStr = "男";
                                        break;
                                    default:
                                        genderStr = "未知";
                                        break;
                                }
                                tvGender.setText(genderStr);
                            } else {
                                tvGender.setText("未知");
                            }

                            if (data.getAvatarUrl() != null) {
                                Glide.with(ProfileInfoActivity.this)
                                        .load(data.getAvatarUrl())
                                        .circleCrop()
                                        .into(ivAvatar);
                            }
                        });
                    } else {
                        runOnUiThread(() -> Toast.makeText(ProfileInfoActivity.this, resp.getMsg(), Toast.LENGTH_SHORT).show());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "JSON 解析失败", e);
                    runOnUiThread(() -> Toast.makeText(ProfileInfoActivity.this, "数据解析错误", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void uploadAvatar(Uri imageUri) {
        Log.d(TAG, "开始上传头像，uri=" + imageUri);
        try {
            File tempFile = new File(getCacheDir(), "temp_avatar.jpg");
            try (InputStream is = getContentResolver().openInputStream(imageUri);
                 FileOutputStream os = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    os.write(buffer, 0, len);
                }
                Log.d(TAG, "临时文件创建成功，大小=" + tempFile.length());
            }
            RequestBody requestFile = RequestBody.create(okhttp3.MediaType.parse("image/*"), tempFile);
            MultipartBody.Part body = MultipartBody.Part.createFormData("file", tempFile.getName(), requestFile);

            String token = SpUtils.getUserToken(this);
            if (token == null || token.isEmpty()) {
                Toast.makeText(this, "未登录", Toast.LENGTH_SHORT).show();
                return;
            }

            // 构建 multipart body
            okhttp3.MultipartBody.Builder builder = new okhttp3.MultipartBody.Builder()
                    .setType(okhttp3.MultipartBody.FORM);
            builder.addFormDataPart("file", tempFile.getName(),
                    okhttp3.RequestBody.create(okhttp3.MediaType.parse("image/*"), tempFile));

            okhttp3.RequestBody requestBody = builder.build();

            Request request = new Request.Builder()
                    .url("https://ai.guying.xyz/ai-project/v1/users/file/avatar")
                    .header("Authorization", "Bearer " + token)
                    .post(requestBody)
                    .build();

            OkHttpClient client = new OkHttpClient();
            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, IOException e) {
                    runOnUiThread(() -> Toast.makeText(ProfileInfoActivity.this, "上传失败：" + e.getMessage(), Toast.LENGTH_SHORT).show());
                    tempFile.delete();
                }

                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                    tempFile.delete();
                    if (response.isSuccessful()) {
                        String body = response.body().string();
                        Gson gson = new Gson();
                        BaseResponse<String> resp = gson.fromJson(body,
                                new com.google.gson.reflect.TypeToken<BaseResponse<String>>() {
                                }.getType());
                        if (resp.getCode() == 1 && resp.getData() != null) {
                            String avatarUrl = resp.getData();
                            // 更新用户信息中的 avatarUrl
                            UpdateUserRequest updateReq = new UpdateUserRequest();
                            updateReq.setAvatarUrl(avatarUrl);
                            updateUserInfoWithOkHttp(updateReq, () -> {
                                // 成功回调：直接显示新头像
                                runOnUiThread(() -> {
                                    Glide.with(ProfileInfoActivity.this)
                                            .load(avatarUrl)
                                            .circleCrop()
                                            .into(ivAvatar);
                                    Toast.makeText(ProfileInfoActivity.this, "头像已更新", Toast.LENGTH_SHORT).show();
                                });
                            });
                        } else {
                            runOnUiThread(() -> Toast.makeText(ProfileInfoActivity.this, "上传失败：" + resp.getMsg(), Toast.LENGTH_SHORT).show());
                        }
                    } else {
                        runOnUiThread(() -> Toast.makeText(ProfileInfoActivity.this, "上传失败，HTTP " + response.code(), Toast.LENGTH_SHORT).show());
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "图片处理失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUserInfoWithOkHttp(UpdateUserRequest request, Runnable onSuccess) {
        token = SpUtils.getUserToken(this);
        Gson gson = new Gson();
        String jsonBody = gson.toJson(request);

        OkHttpClient client = new OkHttpClient();
        Request httpRequest = new Request.Builder()
                .url("https://ai.guying.xyz/ai-project/v1/users")
                .put(okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/json"), jsonBody))
                .header("Authorization", "Bearer " + token)
                .build();

        client.newCall(httpRequest).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(ProfileInfoActivity.this, "网络错误", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                int code = response.code();
                String body = response.body().string();
                Log.d(TAG, "updateUserInfo 响应码: " + code);
                Log.d(TAG, "updateUserInfo 响应体: " + body);
                if (response.isSuccessful()) {
                    Gson gson = new Gson();
                    BaseResponse<Void> resp = gson.fromJson(body,
                            new com.google.gson.reflect.TypeToken<BaseResponse<Void>>() {
                            }.getType());
                    if (resp.getCode() == 1) {
                        if (onSuccess != null) {
                            runOnUiThread(onSuccess);
                        }
                        runOnUiThread(() -> Toast.makeText(ProfileInfoActivity.this, "更新成功", Toast.LENGTH_SHORT).show());
                    } else {
                        runOnUiThread(() -> Toast.makeText(ProfileInfoActivity.this, resp.getMsg(), Toast.LENGTH_SHORT).show());
                    }
                } else {
                    runOnUiThread(() -> Toast.makeText(ProfileInfoActivity.this, "更新失败", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void showEditAgeDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_edit_age);

        EditText etAge = dialog.findViewById(R.id.et_age_dialog);
        TextView tv_cancel = dialog.findViewById(R.id.tv_cancel);
        TextView tv_confirm = dialog.findViewById(R.id.tv_confirm);

        String currentNickname = tvAge.getText().toString();
        etAge.setText(currentNickname);
        etAge.setSelection(etAge.getText().length());

        tv_cancel.setOnClickListener(v -> dialog.dismiss());
        tv_confirm.setOnClickListener(v -> {
            String ageText = etAge.getText().toString().trim();
            if (ageText.isEmpty()) {
                Toast.makeText(this, "请输入年龄", Toast.LENGTH_SHORT).show();
                return;
            }

            int newAge = Integer.parseInt(ageText);
            UpdateUserRequest request = new UpdateUserRequest();
            request.setAge(newAge);

            token = SpUtils.getUserToken(ProfileInfoActivity.this);

            RetrofitClient.getInstance()
                    .create(ApiService.class)
                    .updateUserInfo("Bearer " + token, request)
                    .enqueue(new Callback<ResponseBody>() {
                        @Override
                        public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                            if (response.isSuccessful()) {
                                Toast.makeText(ProfileInfoActivity.this, "修改成功", Toast.LENGTH_SHORT).show();
                                tvAge.setText(String.valueOf(newAge));
                                dialog.dismiss();
                            } else {
                                Toast.makeText(ProfileInfoActivity.this, "失败：" + response.code(), Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<ResponseBody> call, Throwable t) {
                            Toast.makeText(ProfileInfoActivity.this, "网络错误：" + t.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.85),
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
            window.setGravity(Gravity.CENTER);
            window.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        }

        dialog.show();
    }

    private void showEditNicknameDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_edit_nickname);

        EditText et_nickname = dialog.findViewById(R.id.et_nickname_dialog);
        TextView tv_cancel = dialog.findViewById(R.id.tv_cancel);
        TextView tv_confirm = dialog.findViewById(R.id.tv_confirm);

        String currentNickname = tvNickname.getText().toString();
        et_nickname.setText(currentNickname);
        et_nickname.setSelection(et_nickname.getText().length());

        tv_cancel.setOnClickListener(v -> dialog.dismiss());
        tv_confirm.setOnClickListener(v -> {
            String newName = et_nickname.getText().toString().trim();
            if (!(newName.length() >= 1 && newName.length() <= 20)) {
                Toast.makeText(this, "昵称需在1~20个字符内", Toast.LENGTH_SHORT).show();
                return;
            }
            UpdateUserRequest request = new UpdateUserRequest();
            request.setNickname(newName);

            token = SpUtils.getUserToken(ProfileInfoActivity.this);
            Log.d("MyActivity", "昵称Token = " + SpUtils.getUserToken(ProfileInfoActivity.this));

            RetrofitClient.getInstance()
                    .create(ApiService.class)
                    .updateUserInfo("Bearer " + token, request)
                    .enqueue(new Callback<ResponseBody>() {
                        @Override
                        public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                            if (response.isSuccessful()) {
                                Toast.makeText(ProfileInfoActivity.this, "修改成功", Toast.LENGTH_SHORT).show();
                                tvNickname.setText(newName);
                                dialog.dismiss();
                            } else {
                                Toast.makeText(ProfileInfoActivity.this, "失败：" + response.code(), Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<ResponseBody> call, Throwable t) {
                            Toast.makeText(ProfileInfoActivity.this, "网络错误：" + t.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.85),
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
            window.setGravity(Gravity.CENTER);
            window.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        }

        dialog.show();
    }

    private void showGenderSelectDialog() {
        Dialog dialog = new Dialog(this, R.style.BottomDialogTheme);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_gender_selector, null);
        dialog.setContentView(dialogView);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
            window.setGravity(Gravity.BOTTOM);
            TextView tvMale = dialogView.findViewById(R.id.tv_gender_male);
            TextView tvFemale = dialogView.findViewById(R.id.tv_gender_female);
            TextView tvSecret = dialogView.findViewById(R.id.tv_gender_secret);
            tvMale.setOnClickListener(v -> selectGender("男", dialog));
            tvFemale.setOnClickListener(v -> selectGender("女", dialog));
            tvSecret.setOnClickListener(v -> selectGender("保密", dialog));

            dialog.setCanceledOnTouchOutside(true);
            dialog.show();
        }
    }

    private void selectGender(String gender, Dialog dialog) {
        currentGender = gender;
        tvGender.setText(gender);
        dialog.dismiss();
        Toast.makeText(this, "已选择：" + gender, Toast.LENGTH_SHORT).show();

        if (gender.equals("男")) {
            genderCode = 1;
        } else if (gender.equals("女")) {
            genderCode = 0;
        } else {
            genderCode = 2;
        }
        updateGenderToServer();
    }

    private void updateGenderToServer() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setGender(genderCode);

        token = SpUtils.getUserToken(ProfileInfoActivity.this);
        Log.d("MyActivity", "性别Token = " + SpUtils.getUserToken(ProfileInfoActivity.this));

        RetrofitClient.getInstance()
                .create(ApiService.class)
                .updateUserInfo("Bearer " + token, request)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Toast.makeText(ProfileInfoActivity.this, "性别修改成功", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        Toast.makeText(ProfileInfoActivity.this, "修改失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void initView() {
        tvNickname = findViewById(R.id.tv_nickname);
        tvGender = findViewById(R.id.tv_gender);
        tvAge = findViewById(R.id.tv_age);
        ivAvatar = findViewById(R.id.iv_avatar);
        btnOutLogin = findViewById(R.id.btnSignOut);

        tvGender.setText(currentGender);
    }
}