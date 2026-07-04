package com.example.digitaltourguide.view.admin;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.digitaltourguide.R;
import com.example.digitaltourguide.databinding.ActivityManagerAihumanBinding;
import com.example.digitaltourguide.model.admin.DigitalHuman;
import com.example.digitaltourguide.model.admin.TestVideoStatus;
import com.example.digitaltourguide.utils.SpUtils;
import com.example.digitaltourguide.viewmodel.AIHumanViewModel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ManageAIHumanActivity extends AppCompatActivity {
    private static final String TAG="ManageAIHumanActivity";
    private ImageView ivCover;
    private TextView tvUpsert,tvScenicEdit;
    private Button btnGenerateTestVideo;
    private View loadingBottomBar;
    private ActivityManagerAihumanBinding binding;
    private AIHumanViewModel viewModel;
    private String attractionId;
    private String currentAIHumanId;  // 查询后拿到的数字人id
    private String currentOssUrl;       // 上传成功后的视频地址
    private String selectedVideoFileName=""; //保存选中的视频文件名，用于显示和确认
    private File selectedVideoFile; // 临时视频文件对象
    private boolean hasVideoFrameShown = false; // 标记是否已展示视频首帧封面
    private AlertDialog uploadDialog,currentDialog;
    // 轮询
    private Handler preloadHandler = new Handler();
    private Runnable preloadRunnable;
    private boolean isPollingPreload = false;
    private Handler testVideoHandler = new Handler();
    private Runnable testVideoRunnable;
    private boolean isPollingTestVideo = false;
    private static final int REQUEST_CODE_SELECT_VIDEO = 100;
    private static final int REQUEST_CODE_RECORD_VIDEO = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityManagerAihumanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initView();


        viewModel = new ViewModelProvider(this).get(AIHumanViewModel.class);
        attractionId= getIntent().getStringExtra("attraction_id");
        if (TextUtils.isEmpty(attractionId)) {
            Toast.makeText(this, "景点ID不存在，请返回重试", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 设置点击事件
        tvUpsert.setOnClickListener(v -> showUpsertDialog());
        btnGenerateTestVideo.setOnClickListener(v -> generateTestVideo());
        ivCover.setOnClickListener(v -> playCurrentVideo());
        tvScenicEdit.setOnClickListener(v->{
            Intent intent = new Intent(ManageAIHumanActivity.this, ScenicEditActivity.class);
            intent.putExtra("attraction_id", attractionId);
            startActivity(intent);
            overridePendingTransition(R.anim.sibling_fade_in, R.anim.sibling_fade_out);
        });

        //观察上传视频结果
        viewModel.getUploadResult().observe(this,response->{
            if(response!=null && response.getCode()==1){
                String ossUrl=response.getData();
                if (ossUrl != null && !TextUtils.isEmpty(ossUrl)) {
                    Log.e(TAG, "========== 上传视频成功，将调用 upsertDigitalHuman ==========");
                    currentOssUrl = ossUrl;
                    showLoadingBar();
                    Toast.makeText(this, "视频上传成功，数字人配置中...", Toast.LENGTH_SHORT).show();
                    Toast.makeText(this, "温馨提示：15秒的视频需等待5分钟左右", Toast.LENGTH_SHORT).show();
                    upsertDigitalHuman(ossUrl);  // ✅ 这里调用保存

                    //删除临时视频文件
                    if (selectedVideoFile != null && selectedVideoFile.exists()) {
                        selectedVideoFile.delete();
                        selectedVideoFile = null;   // 清空引用
                    }

                    // 更新弹窗中的视频地址显示
                    if (currentDialog != null && currentDialog.isShowing()) {
                        TextView tvOssUrl = uploadDialog.findViewById(R.id.tv_selected_video);
                        if (tvOssUrl != null) {
                            tvOssUrl.setText("已上传视频地址：" + ossUrl);
                        }
                    }
                }
            } else {
                hideLoadingBar();
                String msg = (response != null) ? response.getMsg() : "上传失败";
                Toast.makeText(this, "上传失败：" + msg, Toast.LENGTH_SHORT).show();
            }
        });

        //观察新增/修改结果
        viewModel.getUpsertResult().observe(this,response->{
            if (response != null && response.getCode() == 1) {
                DigitalHuman saved = response.getData();
                if (saved != null) {
                    currentAIHumanId = saved.getId();
                    currentOssUrl = saved.getOssUrl();
                    updateUIForDigitalHumanExists();

                    Toast.makeText(this, "数字人保存成功，正在预加载...", Toast.LENGTH_SHORT).show();
                    startPollingPreloadStatus();
                } else {
                    Toast.makeText(this, "保存成功但返回数据为空", Toast.LENGTH_SHORT).show();
                }
            } else {
                hideLoadingBar();
                String msg = (response != null) ? response.getMsg() : "保存失败";
                Toast.makeText(this, "保存数字人失败：" + msg, Toast.LENGTH_SHORT).show();
            }
        });

        // 观察查询结果
        viewModel.getQueryResult().observe(this, response -> {
            if (response != null && response.getCode() == 1) {
                DigitalHuman digitalHuman = response.getData();
                if (digitalHuman != null) {
                    currentAIHumanId = digitalHuman.getId();
                    String ossUrl = digitalHuman.getOssUrl();
                    Log.d(TAG, "查询返回的 ossUrl = " + ossUrl);
                    currentOssUrl = ossUrl;  // 确保赋值
                    updateUIForDigitalHumanExists();
                } else {
                    currentAIHumanId = null;
                    currentOssUrl = null;
                    updateUIForNoDigitalHuman();
                }
            } else {
                String msg = (response != null) ? response.getMsg() : "网络错误";
                Toast.makeText(this, "查询失败：" + msg, Toast.LENGTH_SHORT).show();
                updateUIForNoDigitalHuman();
            }
        });

        //测试视频生成结果
        viewModel.getTestVideoStatusResult().observe(this, response -> {
            if (response != null && response.getCode() == 1) {
                TestVideoStatus statusObj = response.getData();
                if ("SUCCESS".equals(statusObj.getStatus())) {
                    playTestVideo(statusObj.getVideoUrl());
                    stopPollingTestVideo();
                } else if ("FAILED".equals(statusObj.getStatus())) {
                    Toast.makeText(this, "测试视频生成失败", Toast.LENGTH_SHORT).show();
                    stopPollingTestVideo();
                } else {
                    // 继续轮询
                    testVideoHandler.postDelayed(testVideoRunnable, 3000);
                }
            } else {
                // 请求失败，继续轮询
                testVideoHandler.postDelayed(testVideoRunnable, 3000);
            }
             });

        // 观察生成测试视频结果（2.19）
        viewModel.getGenerateTestVideoResult().observe(this, response -> {
            if (response != null && response.getCode() == 1) {
                Toast.makeText(this, "测试视频任务已提交，正在生成...", Toast.LENGTH_SHORT).show();
                startPollingTestVideoStatus();
            } else {
                String msg = (response != null) ? response.getMsg() : "提交失败";
                Toast.makeText(this, "生成测试视频失败：" + msg, Toast.LENGTH_SHORT).show();
            }
        });

        //预加载状态结果
        viewModel.getPreloadStatusResult().observe(this,response->{
            if(response!=null && response.getCode()==1){
                String status=response.getData();
                Log.d(TAG, "预加载状态: " + status);
                if ("SUCCESS".equals(status)) {
                    hideLoadingBar();
                    Toast.makeText(this, "数字人预加载成功", Toast.LENGTH_SHORT).show();
                    stopPollingPreload();
                } else if ("FAILED".equals(status)) {
                    hideLoadingBar();
                    Toast.makeText(this, "预加载失败", Toast.LENGTH_SHORT).show();
                    stopPollingPreload();
                } else {
                    if (isPollingPreload) preloadHandler.postDelayed(preloadRunnable, 2000);
                }
            } else {
                Log.e(TAG, "预加载请求失败: " + (response != null ? response.getMsg() : "null"));
                if (isPollingPreload) preloadHandler.postDelayed(preloadRunnable, 2000);
            }
        });
        // 进入页面自动查询
        queryDigitalHuman();

    }

    private void queryDigitalHuman() {
        viewModel.queryDigitalHuman(attractionId);
    }

    // 更新UI：有数字人
    private void updateUIForDigitalHumanExists() {
        Log.d(TAG, "updateUIForDigitalHumanExists, currentOssUrl = " + currentOssUrl);
        // 如果已经显示了视频首帧，不覆盖
        if (!hasVideoFrameShown) {
            ivCover.setImageResource(R.drawable.ic_aihuman_video);
        }
        ivCover.setEnabled(true);

    }

    // 更新UI：无数字人
    private void updateUIForNoDigitalHuman() {
        ivCover.setImageResource(R.drawable.ic_aihuman_video); // 默认占位图
         ivCover.setEnabled(false);

    }

    //播放当前
    private void playCurrentVideo(){
        if(TextUtils.isEmpty(currentOssUrl)){
            Toast.makeText(this,"没有可播放的视频",Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent=new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse(currentOssUrl),"video/*");
        startActivity(intent);
    }

    private void showUpsertDialog() {
        AlertDialog.Builder builder=new AlertDialog.Builder(this);
        View dialogView=getLayoutInflater().inflate(R.layout.dialog_upsert_digital_human,null);
        builder.setView(dialogView);
        uploadDialog=builder.create();

        TextView tvAttractionId = dialogView.findViewById(R.id.tv_attraction_id);
        TextView tvSelectedVideo = dialogView.findViewById(R.id.tv_selected_video);
        Button btnSelectVideo = dialogView.findViewById(R.id.btn_select_video);
        Button btnRecordVideo = dialogView.findViewById(R.id.btn_record_video);
        Button btnUpload = dialogView.findViewById(R.id.btn_upload);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);

        tvAttractionId.setText("景点id："+attractionId);

        //选择视频按钮
        btnSelectVideo.setOnClickListener(v->{
            Intent intent=new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("video/*");
            startActivityForResult(intent,REQUEST_CODE_SELECT_VIDEO);
        });

        //录制视频按钮
        btnRecordVideo.setOnClickListener(v->{
            Intent intent=new Intent(ManageAIHumanActivity.this, RecordVideoActivity.class);
            startActivityForResult(intent, REQUEST_CODE_RECORD_VIDEO);
        });

        //上传视频按钮
        btnUpload.setOnClickListener(v -> {
                    if (selectedVideoFile == null || !selectedVideoFile.exists()) {
                        Toast.makeText(this, "请先选择视频文件", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("确认更换")
                            .setMessage("更换后将覆盖当前数字人，是否继续？")
                            .setPositiveButton("继续", (d, w) -> {
                                uploadVideo();
                                uploadDialog.dismiss();
                            })
                            .setNegativeButton("取消", null)
                            .show();
                });

        btnCancel.setOnClickListener(v -> uploadDialog.dismiss());
        uploadDialog.show();
    }

    private void uploadVideo() {
        if (selectedVideoFile == null){
            Log.e(TAG, "selectedVideoFile is null");
            return;
        }
        Log.d(TAG, "文件名: " + selectedVideoFileName);
        Log.d(TAG, "文件大小: " + selectedVideoFile.length() + " bytes");
        // 确保文件名不为空且不包含特殊字符（简化：移除空格）
        String safeFileName = selectedVideoFileName.replace(" ", "_");
        RequestBody requestBody = RequestBody.create(MediaType.parse("video/*"), selectedVideoFile);
        MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", selectedVideoFileName, requestBody);
        viewModel.uploadVideo(attractionId, filePart);
    }

    private void upsertDigitalHuman(String ossUrl) {
        Log.e(TAG, "upsertDigitalHuman 被调用，ossUrl=" + ossUrl);
        DigitalHuman req = new DigitalHuman();
        req.setAttractionId(attractionId);
        req.setOssUrl(ossUrl);
        if (!TextUtils.isEmpty(currentAIHumanId)) {
            req.setId(currentAIHumanId);
        }
        viewModel.upsertDigitalHuman(req);
    }

    //轮询预加载状态 2.15
    private void startPollingPreloadStatus(){
        if(isPollingPreload) return;
        Toast.makeText(this, "正在预加载数字人，请稍候...", Toast.LENGTH_SHORT).show();
        isPollingPreload=true;
        preloadRunnable=new Runnable() {
            @Override
            public void run() {
                viewModel.getPreloadStatus(attractionId);  // 触发请求
            }
        };
        preloadHandler.post(preloadRunnable);
    }

    private void stopPollingPreload() {
        if (preloadRunnable != null) preloadHandler.removeCallbacks(preloadRunnable);
        isPollingPreload = false;
    }

    //测试生成视频
    private void generateTestVideo(){
        if(TextUtils.isEmpty(currentAIHumanId)){

            Toast.makeText(this, "请先配置数字人", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_test_video_text, null);
        builder.setView(dialogView);
        builder.setTitle("生成测试视频");
        builder.setPositiveButton("生成", (dialog, which) -> {
            android.widget.EditText etText = dialogView.findViewById(R.id.et_test_text);
            String customText = etText.getText().toString().trim();
            viewModel.generateTestVideo(attractionId, customText);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    //轮询测试视频状态 2.20
    private void startPollingTestVideoStatus(){
        Log.e("POLL", "startPollingTestVideoStatus 开始轮询");
        if(isPollingTestVideo) return;
        isPollingTestVideo=true;
        testVideoRunnable=()->viewModel.fetchTestVideoStatus(attractionId);
        testVideoHandler.post(testVideoRunnable);
    }

    private void stopPollingTestVideo() {
        if (testVideoRunnable != null) testVideoHandler.removeCallbacks(testVideoRunnable);
        isPollingTestVideo = false;
    }

    private void playTestVideo(String videoUrl) {
        if (TextUtils.isEmpty(videoUrl)) return;

        String fullUrl = videoUrl.startsWith("http") ? videoUrl : "https://ai.guying.xyz" + videoUrl;
        final String finalVideoUrl = fullUrl;

        Toast.makeText(this, "正在加载视频，请稍候...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .addInterceptor(chain -> {
                            Request original = chain.request();
                            String token = SpUtils.getAdminToken(this); // 根据你的 SpUtils 方法名调整
                            Request authorized = original.newBuilder()
                                    .header("Authorization", "Bearer " + token)
                                    .build();
                            return chain.proceed(authorized);
                        })
                        .build();

                Request request = new Request.Builder().url(finalVideoUrl).build();
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        File tempFile = File.createTempFile("test_video_", ".mp4", getCacheDir());
                        long totalBytes = 0;
                        try (InputStream is = response.body().byteStream();
                             FileOutputStream fos = new FileOutputStream(tempFile)) {
                            byte[] buffer = new byte[8192];
                            int len;
                            while ((len = is.read(buffer)) != -1) {
                                fos.write(buffer, 0, len);
                                totalBytes += len;
                            }
                        }
                        Log.d("VideoDownload", "Downloaded " + totalBytes + " bytes to " + tempFile.getAbsolutePath());
                        if (totalBytes > 0) {
                            runOnUiThread(() -> showVideoDialog(tempFile.getAbsolutePath()));
                        } else {
                            runOnUiThread(() -> Toast.makeText(this, "下载失败：文件为空", Toast.LENGTH_SHORT).show());
                        }
                    } else {
                        runOnUiThread(() -> Toast.makeText(this, "下载失败，HTTP " + response.code(), Toast.LENGTH_SHORT).show());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "下载异常：" + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void showVideoDialog(String filePath) {
        // 创建 Dialog 并显示 VideoView
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_video_player, null);
        VideoView videoView = dialogView.findViewById(R.id.video_view);
        Button btnClose = dialogView.findViewById(R.id.btn_close);

        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        videoView.setVideoPath(filePath);
        videoView.setOnPreparedListener(mp -> {
            mp.setLooping(false);
            videoView.start();
        });
        videoView.setOnCompletionListener(mp -> Toast.makeText(this, "播放完成", Toast.LENGTH_SHORT).show());
        videoView.setOnErrorListener((mp, what, extra) -> {
            Toast.makeText(this, "播放失败，请检查视频格式", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            return true;
        });

        btnClose.setOnClickListener(v -> {
            if (videoView.isPlaying()) videoView.stopPlayback();
            dialog.dismiss();
        });

        dialog.setOnDismissListener(dialogInterface -> videoView.stopPlayback());
        dialog.show();
    }



    /**
     * 从视频文件中提取第一帧作为封面显示在 ivCover 上
     */
    private void setVideoCoverFromFile(File videoFile) {
        if (videoFile == null || !videoFile.exists()) return;
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(videoFile.getAbsolutePath());
            Bitmap frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            retriever.release();
            if (frame != null) {
                runOnUiThread(() -> {
                    ivCover.setImageBitmap(frame);
                    hasVideoFrameShown = true;
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "提取视频首帧失败：" + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SELECT_VIDEO && resultCode == RESULT_OK && data != null) {
            Uri videoUri = data.getData();
            if (videoUri != null) {
                try {
                    selectedVideoFileName = getFileNameFromUri(videoUri);
                    selectedVideoFile = copyUriToTempFile(videoUri, selectedVideoFileName);
                    // 提取视频第一帧作为封面
                    setVideoCoverFromFile(selectedVideoFile);
                    if (uploadDialog != null && uploadDialog.isShowing()) {
                        TextView tvSelected = uploadDialog.findViewById(R.id.tv_selected_video);
                        if (tvSelected != null) {
                            tvSelected.setText("已选择：" + selectedVideoFileName);
                        }
                    }
                    Toast.makeText(this, "已选择视频：" + selectedVideoFileName, Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "读取视频文件失败", Toast.LENGTH_SHORT).show();
                }
            }
        } else if (requestCode == REQUEST_CODE_RECORD_VIDEO && resultCode == RESULT_OK && data != null) {
            String path = data.getStringExtra(RecordVideoActivity.EXTRA_VIDEO_PATH);
            if (!TextUtils.isEmpty(path)) {
                File recorded = new File(path);
                if (recorded.exists() && recorded.length() > 0) {
                    // 录制文件已落在 cacheDir，可直接复用上传流程
                    selectedVideoFile = recorded;
                    selectedVideoFileName = recorded.getName();
                    setVideoCoverFromFile(selectedVideoFile);
                    if (uploadDialog != null && uploadDialog.isShowing()) {
                        TextView tvSelected = uploadDialog.findViewById(R.id.tv_selected_video);
                        if (tvSelected != null) {
                            tvSelected.setText("已录制：" + selectedVideoFileName);
                        }
                    }
                    Toast.makeText(this, "录制完成，可点击上传", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "录制文件无效，请重试", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String fileName = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    fileName = cursor.getString(nameIndex);
                }
            }
        }
        if (fileName == null) {
            fileName = uri.getLastPathSegment();
        }
        if (fileName == null) {
            fileName = "video_" + System.currentTimeMillis() + ".mp4";
        }
        return fileName;
    }

    private File copyUriToTempFile(Uri uri, String fileName) throws IOException {
        File tempFile = File.createTempFile("video_", ".mp4", getCacheDir());
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
        }
        return tempFile;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hideLoadingBar();
        if (selectedVideoFile != null && selectedVideoFile.exists()) selectedVideoFile.delete();
        stopPollingPreload();
        stopPollingTestVideo();
    }

    private void initView(){
        ivCover = findViewById(R.id.iv_cover);
        tvUpsert = findViewById(R.id.tv_upsert);
        btnGenerateTestVideo = findViewById(R.id.btn_generate_test_video);
        tvScenicEdit = findViewById(R.id.tab_scenic);
        loadingBottomBar = findViewById(R.id.loading_bottom_bar);
    }

    private void showLoadingBar() {
        if (loadingBottomBar != null && loadingBottomBar.getVisibility() != View.VISIBLE) {
            loadingBottomBar.setVisibility(View.VISIBLE);
        }
    }

    private void hideLoadingBar() {
        if (loadingBottomBar != null) {
            loadingBottomBar.setVisibility(View.GONE);
        }
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(ManageAIHumanActivity.this, PointManagerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

}
