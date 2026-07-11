package com.example.digitaltourguide.view.admin;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.digitaltourguide.R;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.Locale;

/**
 * 数字人形象视频录制页。
 * 使用 CameraX 实时预览并录制一段视频，顶部显示朗读文本提示，
 * 录制完成后将视频文件路径回传给调用方（ManageAIHumanActivity），
 * 后续走与“选择视频文件”相同的上传流程。
 */
public class RecordVideoActivity extends AppCompatActivity {

    private static final String TAG = "RecordVideoActivity";
    private static final int REQUEST_PERMISSIONS = 200;
    private static final String[] REQUIRED_PERMISSIONS =
            {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};

    /** 回传给调用方的录制文件绝对路径 key */
    public static final String EXTRA_VIDEO_PATH = "extra_video_path";
    /** 调用方可传入自定义朗读文本 key（可选） */
    public static final String EXTRA_PROMPT_TEXT = "extra_prompt_text";

    private static final String DEFAULT_PROMPT =
            "请保持面部居中、光线充足，对着镜头自然地朗读以下文字：\n\n" +
            "大家好，欢迎来到本次的数字导览。我将为您介绍这里的历史与风景，" +
            "希望我的讲解能让您的旅程更加轻松愉快。让我们一起出发吧。";

    private PreviewView previewView;
    private Button btnRecord;
    private Button btnCancel;
    private Button btnSwitchCamera;
    private TextView tvTimer;

    private VideoCapture<Recorder> videoCapture;
    private Recording activeRecording;
    private ProcessCameraProvider cameraProvider;
    private int lensFacing = CameraSelector.LENS_FACING_FRONT;
    private File outputFile;

    // 计时
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private long recordStartMs = 0L;
    private Runnable timerRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_video);

        previewView = findViewById(R.id.preview_view);
        btnRecord = findViewById(R.id.btn_record);
        btnCancel = findViewById(R.id.btn_cancel);
        btnSwitchCamera = findViewById(R.id.btn_switch_camera);
        tvTimer = findViewById(R.id.tv_timer);

        TextView tvPrompt = findViewById(R.id.tv_prompt);
        String prompt = getIntent().getStringExtra(EXTRA_PROMPT_TEXT);
        tvPrompt.setText(prompt != null && !prompt.trim().isEmpty() ? prompt : DEFAULT_PROMPT);

        btnRecord.setOnClickListener(v -> toggleRecording());
        btnCancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        btnSwitchCamera.setOnClickListener(v -> switchCamera());

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_PERMISSIONS);
        }
    }

    private boolean allPermissionsGranted() {
        for (String p : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "需要相机和麦克风权限才能录制", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindCameraUseCases();
            } catch (Exception e) {
                Log.e(TAG, "相机初始化失败", e);
                Toast.makeText(this, "相机初始化失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) return;

        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_16_9)
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        Recorder recorder = new Recorder.Builder()
                .setAspectRatio(androidx.camera.core.AspectRatio.RATIO_16_9)
                .setQualitySelector(QualitySelector.from(
                        Quality.HIGHEST, androidx.camera.video.FallbackStrategy.higherQualityOrLowerThan(Quality.HD)))
                .build();
        videoCapture = VideoCapture.withOutput(recorder);

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build();

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture);
        } catch (Exception e) {
            Log.e(TAG, "绑定相机用例失败", e);
            Toast.makeText(this, "无法启动相机：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void switchCamera() {
        if (activeRecording != null) {
            Toast.makeText(this, "录制中无法切换摄像头", Toast.LENGTH_SHORT).show();
            return;
        }
        lensFacing = (lensFacing == CameraSelector.LENS_FACING_FRONT)
                ? CameraSelector.LENS_FACING_BACK
                : CameraSelector.LENS_FACING_FRONT;
        bindCameraUseCases();
    }

    private void toggleRecording() {
        if (videoCapture == null) {
            Toast.makeText(this, "相机尚未就绪，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        if (activeRecording != null) {
            // 正在录制 -> 停止
            activeRecording.stop();
            activeRecording = null;
            return;
        }
        startRecording();
    }

    private void startRecording() {
        outputFile = new File(getCacheDir(), "record_" + System.currentTimeMillis() + ".mp4");
        FileOutputOptions options = new FileOutputOptions.Builder(outputFile).build();

        Recorder recorder = videoCapture.getOutput();
        androidx.camera.video.PendingRecording pending = recorder.prepareRecording(this, options);

        // 已在 onCreate 校验过 RECORD_AUDIO 权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            pending = pending.withAudioEnabled();
        }

        activeRecording = pending.start(ContextCompat.getMainExecutor(this), event -> {
            if (event instanceof VideoRecordEvent.Start) {
                onRecordingStarted();
            } else if (event instanceof VideoRecordEvent.Finalize) {
                onRecordingFinalized((VideoRecordEvent.Finalize) event);
            }
        });
    }

    private void onRecordingStarted() {
        btnRecord.setText("停止录制");
        btnSwitchCamera.setEnabled(false);
        tvTimer.setVisibility(android.view.View.VISIBLE);
        recordStartMs = android.os.SystemClock.elapsedRealtime();
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                long elapsed = android.os.SystemClock.elapsedRealtime() - recordStartMs;
                long sec = elapsed / 1000;
                tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", sec / 60, sec % 60));
                timerHandler.postDelayed(this, 500);
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void onRecordingFinalized(VideoRecordEvent.Finalize finalize) {
        if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);
        btnRecord.setText("开始录制");
        btnSwitchCamera.setEnabled(true);
        tvTimer.setVisibility(android.view.View.INVISIBLE);

        if (finalize.hasError()) {
            Log.e(TAG, "录制失败，错误码=" + finalize.getError());
            if (outputFile != null && outputFile.exists()) outputFile.delete();
            Toast.makeText(this, "录制失败，请重试", Toast.LENGTH_SHORT).show();
            return;
        }

        if (outputFile == null || !outputFile.exists() || outputFile.length() == 0) {
            Toast.makeText(this, "录制文件无效", Toast.LENGTH_SHORT).show();
            return;
        }

        // 回传文件路径，结束本页
        android.content.Intent result = new android.content.Intent();
        result.putExtra(EXTRA_VIDEO_PATH, outputFile.getAbsolutePath());
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);
        if (activeRecording != null) {
            activeRecording.stop();
            activeRecording = null;
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }
}