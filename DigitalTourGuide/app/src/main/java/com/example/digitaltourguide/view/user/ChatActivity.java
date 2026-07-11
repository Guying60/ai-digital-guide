package com.example.digitaltourguide.view.user;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.graphics.SurfaceTexture;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import com.example.digitaltourguide.BuildConfig;
import java.net.InetAddress;
import java.net.Socket;
import javax.net.SocketFactory;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.digitaltourguide.R;
import com.example.digitaltourguide.model.BaseResponse;
import com.example.digitaltourguide.model.admin.DigitalHuman;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;
import com.example.digitaltourguide.model.LocationManager;
import com.example.digitaltourguide.model.user.RoutePlanVO;
import com.example.digitaltourguide.model.user.RouteStopVO;
import com.example.digitaltourguide.network.ApiService;
import com.example.digitaltourguide.utils.ImageUtils;
import com.example.digitaltourguide.utils.SpUtils;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class ChatActivity extends AppCompatActivity {
    private boolean isCameraOpen = false;          // 摄像头开关状态
    private ProcessCameraProvider cameraProvider;  // 保存相机提供者实例
    private int currentLensFacing = CameraSelector.LENS_FACING_FRONT;  // 当前使用的摄像头，前置采集面部表情
    private static final String TAG="ChatActivity";
    private String conversationId,attractionId;
    private ImageAnalysis imageAnalysis;          // 图像分析用例
    private Bitmap latestFrameBitmap;               // 最新的一帧
    private final Object frameLock = new Object();   // 线程安全锁
    private byte[] lastEmotionThumbnail;             // 上一帧降采样灰度图，供帧差初筛
    private Thread recordThread;
    private ScheduledExecutorService heartbeatExecutor;
    private StringBuilder aiBuffer = new StringBuilder(); // 存AI完整句子
    private boolean aiIsReplying = false; // 标记AI是否正在回复
    // 新增：字幕相关
    private TextView tvSubtitle;
    private boolean isSubtitleVisible=true;//字幕默认可见
    private StringBuilder subtitleBuilder = new StringBuilder(); // 用来拼接对话
    private static final String LINE_SEPARATOR = "\n\n"; // 对话之间的分隔
    private static final int REQUEST_CAMERA_PERMISSION = 1001;
    private WebSocket webSocketClient;
    private volatile boolean wsConnected = false;  // 标记 WebSocket 是否已成功连接
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private static final int SAMPLE_RATE = 16000;
    private static final int REQUEST_RECORD_PERMISSION = 100;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private int bufferSize;
    private EditText etMessage;
    private Button btnSend;
    private TextView btnEndChat;
    private ImageView ivMic,ivCapture,ivSubtitleBtn,ivCamera;
    private ImageCapture imageCapture;
    private PreviewView previewView;
    private ExecutorService cameraExecutor;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private String fullAiText = "";          // 累积完整的 AI 回复文本
    private int displayedLength = 0;         // 已显示的字符数
    private Handler typeWriterHandler;       // 打字机定时器
    // 数字人视频播放相关（AVSyncPlayer 统一管理音画同步）
    private TextureView tvDigitalHuman;
    private AVSyncPlayer avSyncPlayer;
    // 数字人待机视频（管理员原始视频静音循环，AI 未说话时盖住空白/冻结帧）
    private PlayerView idleVideoView;
    private ExoPlayer idlePlayer;
    private String idleVideoUrl;
    private volatile boolean aiRoundTextDone = false;      // 本轮 LLM 文本是否已结束（responseDone）
    private volatile boolean idleCurrentlyVisible = false; // 当前待机视频是否可见（去重 hide/show）
    private Handler idleShowHandler;                        // 帧流停止后延时显示待机视频
    private Runnable idleShowRunnable;
    private static final long CROSSFADE_MS = 150;          // 待机↔数字人 淡入淡出时长
    private static final long IDLE_SHOW_DELAY_MS = 500;    // 末帧后多久判定"已停说话"显示待机
    // 面部表情采集参数（成本控制 + 带宽隔离，事件触发：发文字/开始录音/停止录音）
    private static final int EMOTION_TARGET_WIDTH = 480;          // 面部分辨率上限（宽）
    private static final int EMOTION_TARGET_HEIGHT = 640;         // 面部分辨率上限（高）
    private static final int EMOTION_JPEG_QUALITY = 70;           // JPEG 质量
    private static final int EMOTION_BRIGHTNESS_THRESHOLD = 30;   // 亮度低于该值跳过（过暗）
    private static final double EMOTION_FRAME_DIFF_THRESHOLD = 5; // 帧差均值阈值（仅定时采集用，事件触发跳过）
    // 路线数轴
    private RouteTimelineView routeTimeline;
    // GPS 到达判定
    private LocationManager arrivalLocationManager;
    private int gpsNearbyCount = 0;               // 连续在范围内的定位次数
    private int lastAutoArrivedStopIndex = -1;    // 防止重复上报
    private static final int ARRIVAL_DISTANCE_M = 50;   // 到达判定距离阈值（米）
    private static final int ARRIVAL_CONFIRM_COUNT = 3; // 连续 N 次在范围内才触发（3×2s=6s）
    private int binaryMsgCount = 0;           // 二进制消息计数器，用于限频日志
    // ★ 诊断：WebSocket 文本消息接收延迟追踪（Bug 4）
    private long lastTextMsgTime = 0;
    private int textMsgCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        attractionId=getIntent().getStringExtra("attractionId");
        conversationId = getIntent().getStringExtra("conversationId");
        Log.d(TAG,"attractionId:"+attractionId);
        if(attractionId==null || attractionId.isEmpty()){
            Log.w(TAG, "未传入 attractionId，使用默认值：" + attractionId);
        }
        if (conversationId == null || conversationId.isEmpty()) {
            Log.d("ChatActivity", "未传入 conversationId，将开始新对话");
        }

        initView();

        //申请录音权限
        if (checkRecordPermission()) {
            initAudio();
            initWebSocket();
        }

        ivMic.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_PERMISSION);
                return;
            }
            if (isRecording) {
                // 正在录音 -> 停止录音
                Toast.makeText(this,"停止录音",Toast.LENGTH_SHORT).show();
                stopRecord();
            } else {
                // 未录音 -> 开始录音
                if (isRecordReady()) {
                    Toast.makeText(this,"开始录音",Toast.LENGTH_SHORT).show();
                    startRecord();
                } else {
                    Toast.makeText(this, "请先授予录音权限", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnSend.setOnClickListener(v->{
            if (aiIsReplying) return; // AI回复中不允许发送
            String text=etMessage.getText().toString().trim();
            if(text.isEmpty()){
                Toast.makeText(this,"请输入文本",Toast.LENGTH_SHORT).show();
                return;
            }
            sendTextMessage(text);
            etMessage.setText("");//清空输入框
        });

        ivSubtitleBtn.setOnClickListener(v -> {
            if (isSubtitleVisible) {
                // 隐藏字幕栏
                tvSubtitle.setVisibility(View.GONE);
                isSubtitleVisible = false;
            } else {
                // 显示字幕栏
                tvSubtitle.setVisibility(View.VISIBLE);
                isSubtitleVisible = true;
            }
        });

        //初始化相机线程
        cameraExecutor= Executors.newSingleThreadExecutor();
        checkCameraPermissionAndStart();
    }

    private void stopPlayback() {
        if (avSyncPlayer != null) {
            avSyncPlayer.interrupt();
        }
        Log.d("MYTEST", "已主动停止播放，等待下次交互");
    }

    /**
     * 结束对话：关闭 WebSocket、释放资源，回到 HistoryActivity。
     * 页面过渡动画与 DataAnalysisActivity ↔ TouristAnalysisActivity 一致（sibling_fade）。
     */
    private void endChatAndGoBack() {
        Log.d(TAG, "结束对话按钮被点击，开始清理资源");
        try {
            // 1. 关闭 WebSocket
            if (webSocketClient != null) {
                webSocketClient.close(1000, "用户结束对话");
                webSocketClient = null;
            }
            wsConnected = false;

            // 2. 停止心跳
            stopHeartbeat();

            // 3. 停止录音
            if (isRecording) {
                isRecording = false;
                if (audioRecord != null) {
                    audioRecord.stop();
                }
                if (recordThread != null) {
                    try {
                        recordThread.join(500);
                    } catch (InterruptedException ignored) {}
                    recordThread = null;
                }
            }
            if (audioRecord != null) {
                audioRecord.release();
                audioRecord = null;
            }

            // 4. 停止相机
            stopCamera();
            if (cameraExecutor != null) {
                cameraExecutor.shutdownNow();
                cameraExecutor = null;
            }

            // 5. 释放 AVSyncPlayer
            if (avSyncPlayer != null) {
                avSyncPlayer.release();
                avSyncPlayer = null;
            }

            // 6. 释放待机视频
            cancelIdleShowTimer();
            if (idlePlayer != null) {
                idlePlayer.release();
                idlePlayer = null;
            }

            // 7. 停止路线 GPS 监控
            stopArrivalMonitoring();

            Log.d(TAG, "资源清理完成，准备跳转到 HistoryActivity");
        } catch (Exception e) {
            Log.e(TAG, "清理资源时出错", e);
        }

        // 8. 返回 HistoryActivity（使用与数据分析相同的 fade 动画）
        // 使用 finish() 而非 startActivity，确保 onActivityResult 被正确触发
        setResult(RESULT_OK);
        finish();
        overridePendingTransition(R.anim.sibling_fade_in, R.anim.sibling_fade_out);
    }

    private void sendTextMessage(String text) {
        if(webSocketClient==null){
            runOnUiThread(()->Toast.makeText(this,"WebSocket未连接",Toast.LENGTH_SHORT).show());
            return;
        }
        try {
            JSONObject json=new JSONObject();
            json.put("type","text");
            json.put("text",text);
            webSocketClient.send(json.toString());
            Log.d("MYTEST","发送文本信息："+text);
            addUserMessage(text);//字幕
            captureEmotionFrame();  // 事件触发：发文字时抓表情
            // 发送后立刻显示等待图标
            runOnUiThread(() -> setSendButtonLoading(true));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 发送按钮切换：AI回复中显示 ic_ing，否则显示"发送" */
    private void setSendButtonLoading(boolean loading) {
        if (loading) {
            btnSend.setText("");
            btnSend.setEnabled(false);
            android.graphics.drawable.Drawable ing = ContextCompat.getDrawable(this, R.drawable.ic_ing);
            if (ing != null) {
                int size = (int) (24 * getResources().getDisplayMetrics().density);
                ing.setBounds(0, 0, size, size);
                btnSend.setCompoundDrawables(ing, null, null, null);
            }
            btnSend.setCompoundDrawablePadding(0);
        } else {
            btnSend.setText("发送");
            btnSend.setEnabled(true);
            btnSend.setCompoundDrawables(null, null, null, null);
        }
    }


    // 启动相机：前置摄像头 + 面部表情低频采集
    private void startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
                isCameraOpen = true;
                if (previewView != null) {
                    previewView.setVisibility(View.VISIBLE);
                }
            } catch (ExecutionException | InterruptedException e) {
                Log.e("MYTEST", "相机启动失败", e);
                Toast.makeText(this, "相机初始化失败", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void stopCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider = null;
        }
        imageCapture = null;
        imageAnalysis = null;
        synchronized (frameLock) {
            if (latestFrameBitmap != null && !latestFrameBitmap.isRecycled()) {
                latestFrameBitmap.recycle();
                latestFrameBitmap = null;
            }
        }
        lastEmotionThumbnail = null;
        isCameraOpen = false;
        Log.d("MYTEST", "前置摄像头已关闭");

        if (previewView != null) {
            previewView.setVisibility(View.GONE);
        }
    }

    //摄像头开关
    private void toggleCamera() {
        if (isCameraOpen) {
            stopCamera();
        } else {
            checkEmotionPrivacyAndStart();
        }
    }

    private void bindCameraUseCases(ProcessCameraProvider cameraProvider) {
        // 预览
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // 手动拍照用例
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        // 图像分析用例
        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        // 选择摄像头（根据 currentLensFacing）
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(currentLensFacing)
                .build();

        // 解绑所有用例再绑定新用例
        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(ChatActivity.this, cameraSelector, preview, imageCapture, imageAnalysis);

        // 设置分析器
        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
            Bitmap bitmap = imageProxyToBitmap(imageProxy);
            if (bitmap != null) {
                synchronized (frameLock) {
                    if (latestFrameBitmap != null && !latestFrameBitmap.isRecycled()) {
                        latestFrameBitmap.recycle();
                    }
                    latestFrameBitmap = bitmap;
                }
            }
            imageProxy.close();
        });
    }

    /**
     * 事件触发采集：文字发送 / 开始录音 / 停止录音时抓一帧。
     * 取 latestFrameBitmap 副本，分发到 cameraExecutor 做初筛+编码+发送。
     */
    private void captureEmotionFrame() {
        final Bitmap frame;
        synchronized (frameLock) {
            if (latestFrameBitmap != null && !latestFrameBitmap.isRecycled()) {
                frame = latestFrameBitmap.copy(latestFrameBitmap.getConfig(), false);
            } else {
                frame = null;
            }
        }
        if (frame != null) {
            cameraExecutor.execute(() -> preScreenAndEncode(frame, true));
        }
    }

    /** 本地初筛（亮度 + 可选帧差），通过则编码+发送；均在 cameraExecutor 线程执行。 */
    private void preScreenAndEncode(Bitmap frame, boolean skipFrameDiff) {
        // 1. 亮度初筛
        if (isTooDark(frame)) {
            Log.d("MYTEST", "表情帧亮度不足，跳过");
            frame.recycle();
            return;
        }
        // 2. 帧差初筛（事件触发时跳过，每次交互都有意义）
        if (!skipFrameDiff) {
            byte[] thumbnail = toGrayThumbnail(frame, 32);
            if (lastEmotionThumbnail != null && isFrameNearlySame(thumbnail)) {
                Log.d("MYTEST", "表情帧与上一帧无显著变化，跳过");
                lastEmotionThumbnail = thumbnail;
                frame.recycle();
                return;
            }
            lastEmotionThumbnail = thumbnail;
        } else {
            // 事件触发：更新 lastEmotionThumbnail 但不做帧差判断
            lastEmotionThumbnail = toGrayThumbnail(frame, 32);
        }
        // 3. 降分辨率 + 编码 + 发送
        Bitmap scaled = ImageUtils.compressBitmap(frame, EMOTION_TARGET_WIDTH, EMOTION_TARGET_HEIGHT);
        String base64 = bitmapToBase64Quality(scaled, EMOTION_JPEG_QUALITY);
        sendEmotionFrame(base64);
        frame.recycle();
        if (scaled != frame) scaled.recycle();
    }

    /** 降采样后计算平均亮度，低于阈值视为过暗 */
    private boolean isTooDark(Bitmap bitmap) {
        byte[] gray = toGrayThumbnail(bitmap, 32);
        int sum = 0;
        for (byte b : gray) sum += (b & 0xFF);
        double avg = sum / (double) gray.length;
        return avg < EMOTION_BRIGHTNESS_THRESHOLD;
    }

    /** 降采样到 smallW×auto 的灰度数组，同时用于亮度与帧差 */
    private byte[] toGrayThumbnail(Bitmap bitmap, int smallW) {
        int w = Math.min(bitmap.getWidth(), smallW);
        int h = bitmap.getHeight() * w / bitmap.getWidth();
        if (h < 1) h = 1;
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, w, h, true);
        int[] pixels = new int[w * h];
        scaled.getPixels(pixels, 0, w, 0, 0, w, h);
        scaled.recycle();
        byte[] gray = new byte[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            gray[i] = (byte) ((77 * ((p >> 16) & 0xFF) + 150 * ((p >> 8) & 0xFF) + 29 * (p & 0xFF)) >> 8);
        }
        return gray;
    }

    /** 与上一帧灰度均差小于阈值则视为无变化 */
    private boolean isFrameNearlySame(byte[] gray) {
        if (lastEmotionThumbnail == null || lastEmotionThumbnail.length != gray.length) return false;
        long sumDiff = 0;
        for (int i = 0; i < gray.length; i++) {
            sumDiff += Math.abs((gray[i] & 0xFF) - (lastEmotionThumbnail[i] & 0xFF));
        }
        double avgDiff = sumDiff / (double) gray.length;
        return avgDiff < EMOTION_FRAME_DIFF_THRESHOLD;
    }

    /** 按指定质量将 Bitmap 转 base64，无换行。复刻 ImageUtils.bitmapToBase64 逻辑。 */
    private String bitmapToBase64Quality(Bitmap bitmap, int quality) {
        if (bitmap == null) return "";
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
            return android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e("MYTEST", "bitmap转base64失败", e);
            return "";
        }
    }

    private void sendEmotionFrame(String base64) {
        if (webSocketClient == null || base64 == null || base64.isEmpty()) return;
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "emotionFrame");
            msg.put("photo", base64);
            webSocketClient.send(msg.toString());
            Log.d("MYTEST", "发送表情帧, base64长度=" + base64.length());
        } catch (JSONException e) {
            Log.e("MYTEST", "构造表情帧消息失败", e);
        }
    }

    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        return imageProxy.toBitmap();
    }

    /**
     * 检查表情采集隐私确认：首次弹对话框说明，已确认则直接启动前置摄像头。
     * 复刻现有 AlertDialog 风格（路线关闭按钮 / 权限拒绝引导）。
     */
    private void checkEmotionPrivacyAndStart() {
        if (SpUtils.isEmotionPrivacyAcknowledged(this)) {
            startCamera();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("面部表情采集说明")
                .setMessage("我们将低频采集您的面部表情，仅用于聚合情感统计并优化导览体验。" +
                        "您的面部图像不会被存储、不会用于个人身份识别，" +
                        "也不会与第三方共享。")
                .setPositiveButton("同意并开启", (dialog, which) -> {
                    SpUtils.setEmotionPrivacyAcknowledged(this);
                    startCamera();
                })
                .setNegativeButton("暂不开启", null)
                .show();
    }

    private void checkCameraPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        } else {
            Log.d("MYTEST", "已有相机权限 → 启动前置摄像头");
            checkEmotionPrivacyAndStart();
        }
    }

    private boolean checkRecordPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_PERMISSION);
            return false;
        }
        return true;
    }

    private void sendMicStatus(boolean isOn){
        if(webSocketClient==null) return;
        try{
            JSONObject json=new JSONObject();
            json.put("type",isOn ? "micOn" :"micOff");
            webSocketClient.send(json.toString());
            Log.d("MYTEST", "发送麦克风状态: " + (isOn ? "micOn" : "micOff"));
        } catch (JSONException e) {
            Log.e("MYTEST", "发送mic状态失败", e);
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //麦克风
        if (requestCode == REQUEST_RECORD_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initAudio();
                initWebSocket();
                runOnUiThread(() -> Toast.makeText(ChatActivity.this, "录音权限已授予", Toast.LENGTH_SHORT).show());
            } else {
                // 用户拒绝权限
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
                    // 用户拒绝了但没有勾选“不再询问”，可以再次解释并请求
                    Toast.makeText(this, "需要录音权限才能使用语音功能", Toast.LENGTH_SHORT).show();
                    // 你可以再次请求，但注意不要无限循环
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.RECORD_AUDIO},
                            REQUEST_RECORD_PERMISSION);
                } else {
                    // 用户勾选了“不再询问”，需要引导去设置页面
                    new AlertDialog.Builder(this)
                            .setTitle("权限需要")
                            .setMessage("录音权限已被禁止。请在设置中手动授予权限，否则将无法使用语音功能。")
                            .setPositiveButton("去设置", (dialog, which) -> {
                                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.setData(Uri.parse("package:" + getPackageName()));
                                startActivity(intent);
                            })
                            .setNegativeButton("取消", null)
                            .show();
                }
            }


        }
        //相机
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkEmotionPrivacyAndStart();
            } else {
                Toast.makeText(this, "需要相机权限才能进行面部表情采集", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void initAudio() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        // AudioTrack 已由 AVSyncPlayer 管理，此处仅初始化 AudioRecord（录音）
        if (audioRecord == null) {
            bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, FORMAT);
            if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
                bufferSize = SAMPLE_RATE * 2; // 回退值，约 32000 字节
            }
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, FORMAT, bufferSize);
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord 初始化失败");
                audioRecord = null;
            } else {
                Log.d(TAG, "AudioRecord 初始化成功，bufferSize=" + bufferSize);
            }
        }
    }

    private void initWebSocket() {
        if (webSocketClient != null) return;

        // 1. 获取你登录后的信息（已经能拿到了）
        String token = SpUtils.getUserToken(ChatActivity.this);
        String userId = SpUtils.getUserId(this);

        // 2. 构建 WebSocket URL（景点ID 通过 query parameter 传递）
        String wsUrl = "wss://ai.guying.xyz/ai-project/chat"
                + "?attractionId=" + attractionId;

        // 3. 构建 OkHttp 客户端（支持 wss 安全协议）
        // ★ TCP_NODELAY: 禁用 Nagle 算法，视频/音频帧立即发送不等待合并
        SocketFactory noDelayFactory = new SocketFactory() {
            private final SocketFactory delegate = SocketFactory.getDefault();
            private Socket apply(Socket s) {
                try {
                    s.setTcpNoDelay(true);
                    s.setReceiveBufferSize(512 * 1024);  // 512KB 接收缓冲避免丢帧
                    s.setSendBufferSize(256 * 1024);
                } catch (java.net.SocketException ignored) {}
                return s;
            }
            @Override public Socket createSocket() throws java.io.IOException { return apply(delegate.createSocket()); }
            @Override public Socket createSocket(String host, int port) throws java.io.IOException { return apply(delegate.createSocket(host, port)); }
            @Override public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws java.io.IOException { return apply(delegate.createSocket(host, port, localHost, localPort)); }
            @Override public Socket createSocket(InetAddress host, int port) throws java.io.IOException { return apply(delegate.createSocket(host, port)); }
            @Override public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws java.io.IOException { return apply(delegate.createSocket(address, port, localAddress, localPort)); }
        };
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)   // WebSocket 不设读超时，靠心跳保活
                .writeTimeout(30, TimeUnit.SECONDS)
                .socketFactory(noDelayFactory)
                .addNetworkInterceptor(chain -> {
                    Request original = chain.request();
                    // 去掉压缩扩展头，强制后端不使用压缩
                    Request newRequest = original.newBuilder()
                            .removeHeader("Sec-WebSocket-Extensions")
                            .build();
                    return chain.proceed(newRequest);
                })
                .build();

        // 4. 构建请求（token 通过 Authorization header 传递）
        Request request = new Request.Builder()
                .url(wsUrl)
                .addHeader("Authorization", "Bearer " + token)
                .build();

        // 4. 连接 WebSocket
        webSocketClient = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                super.onOpen(webSocket, response);
                wsConnected = true;
                runOnUiThread(() -> {
                    Toast.makeText(ChatActivity.this, "✅ 连接成功！", Toast.LENGTH_SHORT).show();
                    startHeartbeat();
                    // 1.16.1 恢复当前激活路线
                    loadCurrentRoute();
                    // 1.17 拉取数字人原始视频并开始待机循环播放
                    loadIdleVideo();
                });
            }

            private void startHeartbeat() {
                if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) return;
                heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
                heartbeatExecutor.scheduleAtFixedRate(() -> {
                    if (webSocketClient != null) {
                        try {
                            JSONObject ping = new JSONObject();
                            ping.put("type", "ping");
                            webSocketClient.send(ping.toString());
                            Log.d("MYTEST", "发送心跳 ping");
                        } catch (Exception e) {
                            Log.e("MYTEST", "构造 ping 失败", e);
                        }
                    }
                }, 0, 10, TimeUnit.SECONDS);
            }

            //拍照的
            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                super.onMessage(webSocket, text);
                // ★ 诊断：记录文本消息接收间隔，追踪 WebSocket 延迟（Bug 4）
                long textNow = System.currentTimeMillis();
                long textInterval = lastTextMsgTime == 0 ? 0 : textNow - lastTextMsgTime;
                lastTextMsgTime = textNow;
                textMsgCount++;
                if (textInterval > 500 || textMsgCount <= 20 || textMsgCount % 20 == 0) {
                    Log.w(TAG, "⇩ TEXT-RECV: interval=" + textInterval + "ms len=" + text.length()
                            + " total=" + textMsgCount + " preview="
                            + (text.length() > 80 ? text.substring(0, 80) : text));
                } else {
                    Log.d("MYTEST", "收到后端消息：" + text);
                }
                try {
                    JSONObject json = new JSONObject(text);
                    String type = json.optString("type");

                    if("ready".equals(type)){
                        Log.d("MYTEST", "✅ 服务端就绪，数字人待机");
                    }
                    // 新增其他消息类型的识别（核心补充）
                    else if ("speechStarted".equals(type)) {
                        Log.d("MYTEST", "✅ 后端检测到你开始说话");
                    } else if ("userInput".equals(type)) {
                        String userText = json.optString("text");
                        Log.d("webSocket", "✅ 你说的话识别结果：" + userText);
                        addUserMessage(userText);
                    }else if ("aiOutput".equals(type)) {
                            String aiText = json.optString("text");
                        Log.d("MYTEST", "aiOutput 片段长度：" + aiText.length() + "，内容：" + aiText);
                            if (aiText == null || aiText.isEmpty()) return;

                            runOnUiThread(() -> {
                                try {
                                    if (!aiIsReplying) {
                                        aiIsReplying = true;
                                        subtitleBuilder.append(LINE_SEPARATOR).append("AI：");
                                        // 重置累积变量
                                        fullAiText = "";
                                        displayedLength = 0;
                                        if (typeWriterHandler != null) {
                                            typeWriterHandler.removeCallbacksAndMessages(null);
                                        }
                                        typeWriterHandler = new Handler(Looper.getMainLooper());
                                    }

                                    // 累积新收到的文本
                                    fullAiText += aiText;

                                    // 如果有新的字符未显示，启动/继续逐字显示
                                    if (displayedLength < fullAiText.length()) {
                                        startTypeWriter();
                                    }
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });
                        } else if ("aiHuman".equals(type)) {
                        String aiSpeakText = json.optString("text");
                        Log.d("MYTEST", "✅ AI要说话：" + aiSpeakText);
                    } else if ("responseDone".equals(type)) {
                        Log.d("MYTEST", "✅ 本轮对话结束");
                        // 标记本轮文本已结束：当数字人帧流停止后即可恢复待机视频
                        aiRoundTextDone = true;
                        runOnUiThread(() -> {
                            aiIsReplying = false;
                            setSendButtonLoading(false);
                            // 输出完成后停留在最后一行
                            tvSubtitle.post(() -> scrollSubtitleToBottom());
                        });
                    } else if ("pong".equals(type)) {
                        Log.d("MYTEST", "✅ 心跳回复正常");
                    } else if ("routeTimeline".equals(type)) {
                        // 1.16.2 路线生成成功，下发完整数轴
                        RoutePlanVO route = new Gson().fromJson(json.optString("data"), RoutePlanVO.class);
                        runOnUiThread(() -> {
                            if (route != null && route.hasStops()) {
                                routeTimeline.setRoute(route);
                                startArrivalMonitoring(route);
                                Log.d("MYTEST", "路线已展示: " + route.getTitle() + " stops=" + route.getStops().size());
                            }
                        });
                    } else if ("routeUpdate".equals(type)) {
                        // 1.16.2 到达上报后，服务端下发更新后的数轴
                        RoutePlanVO route = new Gson().fromJson(json.optString("data"), RoutePlanVO.class);
                        runOnUiThread(() -> {
                            if (route != null && route.hasStops()) {
                                routeTimeline.setRoute(route);
                                startArrivalMonitoring(route);
                                Log.d("MYTEST", "路线已更新: " + route.getTitle());
                            }
                        });
                    } else if ("routeClosed".equals(type)) {
                        // 1.16.2 路线已关闭，清空数轴但保留生成按钮
                        runOnUiThread(() -> {
                            routeTimeline.clearRoute();
                            stopArrivalMonitoring();
                            Log.d("MYTEST", "路线已关闭");
                        });
                    } else if ("routeError".equals(type)) {
                        // 1.16.2 路线生成失败
                        String errText = json.optString("text", "路线生成失败");
                        Log.w("MYTEST", "路线错误: " + errText);
                        runOnUiThread(() -> Toast.makeText(ChatActivity.this, errText, Toast.LENGTH_SHORT).show());
                    } else if ("error".equals(type)) {
                        String errorMsg = json.optString("text");
                        Log.d("MYTEST", "❌ 后端报错：" + errorMsg);
                    }else if ("allDone".equals(type)) {
                        Log.d("MYTEST", "收到 allDone，后端已就绪");
                        // 清空队列，准备下一次对话
                        if (avSyncPlayer != null) avSyncPlayer.onConversationEnd();
                        aiIsReplying = false;
                        // 麦克风默认关闭，用户手动点击按钮开启
                        runOnUiThread(() -> {
                            setSendButtonLoading(false);
                            sendMicStatus(false);  // 告知后端麦克风关闭
                        });
                    }else if ("done".equals(type)) {
                         int doneSentenceId = json.optInt("sentence_id");
                        // 通知 AVSyncPlayer 句子结束（触发水位线不足时的播放启动）
                        if (avSyncPlayer != null) avSyncPlayer.onSentenceDone(doneSentenceId);
                    }

                } catch (JSONException e) {
                    // 保留你原来的异常日志
                    Log.e("MYTEST", "后端消息解析失败", e);
                }
            }

            //录音的
            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull ByteString bytes) {

                // P1/B1: 只做一次拷贝，type flag 保留在头部交给 AVSyncPlayer 解析
                byte[] raw = bytes.toByteArray();
                binaryMsgCount++;
                if (BuildConfig.DEBUG && binaryMsgCount % 50 == 1) {
                    Log.d("BINARY", "收到二进制数据 #" + binaryMsgCount + "，长度=" + raw.length);
                }

                if (raw.length == 0) return;

                int typeFlag = raw[0] & 0xFF;
                // ★ B2: 使用异步入队，将帧处理从 WebSocket reader 线程卸载到独立线程，
                // 确保 reader 能全速从 TCP buffer 读取数据，消除锁竞争和 I/O 阻塞
                if (typeFlag == 0x01) {  // 音频
                    if (avSyncPlayer != null) avSyncPlayer.onAudioDataAsync(raw);
                } else if (typeFlag == 0x03) {  // H.264 视频
                    if (avSyncPlayer != null) avSyncPlayer.onVideoDataAsync(raw);
                } else {
                    Log.w("BINARY", "未知 typeFlag: 0x" + Integer.toHexString(typeFlag));
                }
                // 每收到一帧就重置"显示待机"定时器：帧流持续=正在说话；停流后延时恢复待机
                scheduleIdleShowAfterDrain();
            }

            @Override
            public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                Log.e("MYTEST", "WebSocket 关闭: " + reason);
                wsConnected = false;
                webSocketClient = null;  // 清空引用，允许重新连接
                runOnUiThread(() -> {
                    stopHeartbeat();
                    stopRecord();  // 停止录音
                    Toast.makeText(ChatActivity.this, "连接已关闭", Toast.LENGTH_SHORT).show();
                });
            }


            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, Response response) {
                super.onFailure(webSocket, t, response);
                Log.e("MYTEST", "❌ WebSocket 报错：" + t.getMessage() + " wsConnected=" + wsConnected);
                boolean wasConnected = wsConnected;
                wsConnected = false;
                webSocketClient = null;  // 清空引用，允许重新连接
                runOnUiThread(() -> {
                    stopHeartbeat();
                    stopRecord();
                    if (wasConnected) {
                        // 连接已建立后断开，不是连接失败
                        Toast.makeText(ChatActivity.this, "连接已断开", Toast.LENGTH_SHORT).show();
                    } else {
                        // 真正的连接失败
                        String msg = t.getMessage();
                        Toast.makeText(ChatActivity.this, "连接失败: " + (msg != null ? msg : "网络异常"), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }


    private void sendInterrupt(){
        if(webSocketClient==null) return;
        try{
            JSONObject interrupt=new JSONObject();
            interrupt.put("type","interrupt");
            webSocketClient.send(interrupt.toString());
            Log.d("MYTEST","发送打断指令：interrupt");
        }catch(Exception e){
            Log.e("MYTEST", "构造 interrupt 失败", e);
        }
        // 通知 AVSyncPlayer 中断当前播放
        if (avSyncPlayer != null) avSyncPlayer.interrupt();
    }


    private void startTypeWriter(){
        typeWriterHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(displayedLength<fullAiText.length()){
                    //每次只增加一个字符
                    displayedLength++;
                    String displayedText=fullAiText.substring(0,displayedLength);
                    // 更新字幕中最后一行的 AI 内容
                    int lastAiPos = subtitleBuilder.lastIndexOf("AI：");
                    if (lastAiPos != -1) {
                        String prefix = subtitleBuilder.substring(0, lastAiPos + 3);
                        subtitleBuilder.setLength(0);
                        subtitleBuilder.append(prefix).append(displayedText);
                    } else {
                        subtitleBuilder.append(displayedText);
                    }
                    tvSubtitle.setText(subtitleBuilder.toString());
                    // 流式输出时：最新行始终显示在字幕区中间，方便阅读
                    scrollSubtitleToCenter();
                    // 如果还没显示完，继续下一个字符
                    if (displayedLength < fullAiText.length()) {
                        typeWriterHandler.postDelayed(this, 150); // 可调整速度，单位毫秒
                    }
                }
            }
        },30);
    }

    private boolean isRecordReady() {
        return  audioRecord != null && webSocketClient != null;
    }

   private void startRecord() {
        Log.d("MYTEST", "===== 点击开始录音 =====");
        // 防重复启动、空指针检查
        if (isRecording || audioRecord == null || webSocketClient == null) {
            Log.w("MYTEST", "录音启动失败：isRecording=" + isRecording + ", audioRecord=" + audioRecord + ", webSocket=" + webSocketClient);
            return;
        }

        isRecording = true;
       sendMicStatus(true);
       captureEmotionFrame();  // 事件触发：开始说话时抓表情
       try {
            // 启动录音
            audioRecord.startRecording();
            Log.d("MYTEST", "===== 录音启动成功，参数：16000Hz 单声道 16bit =====");
            Log.d("MYTEST", "系统推荐Buffer大小：" + bufferSize);

            // 子线程执行录音（不能在主线程）
            recordThread =  new Thread(() -> {
                byte[] buffer = new byte[bufferSize];
                while (isRecording) {
                    // 读取音频数据（阻塞式，填满buffer才返回）
                    int len = audioRecord.read(buffer, 0, bufferSize);

                    // 打印读取到的字节数（现在一定会有输出！）
                    Log.d("MYTEST", "读取到字节数：" + len);

                    // 数据有效，发送给后端
                    if (len > 0 && webSocketClient != null) {
                        // OkHttp WebSocket 发送二进制数据（正确写法）
                        webSocketClient.send(okio.ByteString.of(buffer, 0, len));
                        Log.d("MYTEST", "已发送音频数据，长度：" + len);
                    } else if (len == AudioRecord.ERROR_INVALID_OPERATION) {
                        Log.e("MYTEST", "录音读取错误：ERROR_INVALID_OPERATION");
                    } else if (len == AudioRecord.ERROR_BAD_VALUE) {
                        Log.e("MYTEST", "录音读取错误：ERROR_BAD_VALUE");
                    }
                }
            });
            recordThread.start();

        } catch (Exception e) {
            Log.e("MYTEST", "录音启动异常", e);
            isRecording = false;
        }
    }

    private void stopRecord(){
        isRecording=false;
        sendMicStatus(false);
        if(audioRecord!=null){
            audioRecord.stop();
        }
        if (recordThread != null) {
            try {
                recordThread.join(500); // 等待最多 500ms
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            recordThread = null;
        }
        captureEmotionFrame();  // 事件触发：停止说话时抓表情
    }

    //用户说的话
    private void addUserMessage(String text){
        if(text==null || text.isEmpty()) return;
        runOnUiThread(()->{
            subtitleBuilder.append(LINE_SEPARATOR).append("我：").append(text);
            tvSubtitle.setText(subtitleBuilder.toString());
            // 用户消息：滚动到底部
            tvSubtitle.post(() -> scrollSubtitleToBottom());
        });
    }
    /**
     * 滚动字幕到中间：最新一行显示在字幕区的垂直中间位置
     */
    private void scrollSubtitleToCenter() {
        android.text.Layout layout = tvSubtitle.getLayout();
        if (layout == null || layout.getLineCount() == 0) return;
        int lastLine = layout.getLineCount() - 1;
        int lastLineTop = layout.getLineTop(lastLine);
        int viewHeight = tvSubtitle.getHeight();
        int scrollY = Math.max(0, lastLineTop - viewHeight / 2);
        tvSubtitle.scrollTo(0, scrollY);
    }

    /**
     * 滚动字幕到底部：最后一行显示在字幕区最下方
     */
    private void scrollSubtitleToBottom() {
        android.text.Layout layout = tvSubtitle.getLayout();
        if (layout == null || layout.getLineCount() == 0) return;
        int totalHeight = layout.getLineTop(layout.getLineCount());
        int viewHeight = tvSubtitle.getHeight();
        int scrollY = Math.max(0, totalHeight - viewHeight);
        tvSubtitle.scrollTo(0, scrollY);
    }

    private void stopHeartbeat(){
        if(heartbeatExecutor!=null){
            heartbeatExecutor.shutdownNow();
            heartbeatExecutor=null;
        }
    }

    @Override
    public void onBackPressed() {
        // 按返回键时也要断开 WebSocket，然后回到 HistoryActivity
        setResult(RESULT_OK);
        // 清理 WebSocket 和资源（精简版，onDestroy 会兜底）
        if (webSocketClient != null) {
            webSocketClient.close(1000, "用户返回");
            webSocketClient = null;
        }
        wsConnected = false;
        stopHeartbeat();
        stopArrivalMonitoring();
        if (avSyncPlayer != null) {
            avSyncPlayer.interrupt();
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (audioRecord == null) {
                initAudio();
            }
            if (webSocketClient == null) {
                initWebSocket();
            }
        }
        // 恢复 GPS 路线到达监控
        RoutePlanVO currentRoute = routeTimeline != null ? routeTimeline.getCurrentRoute() : null;
        if (currentRoute != null && currentRoute.hasStops()) {
            startArrivalMonitoring(currentRoute);
        }
        // 恢复待机视频播放（仅当其可见时）
        if (idlePlayer != null && idleVideoView != null && idleVideoView.getVisibility() == View.VISIBLE) {
            idlePlayer.setPlayWhenReady(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopArrivalMonitoring();
        if (idlePlayer != null) idlePlayer.setPlayWhenReady(false);
    }

    //页面销毁时释放资源
    @Override
    protected void onDestroy() {
        stopHeartbeat();
        stopArrivalMonitoring();
        super.onDestroy();
        isRecording = false;
        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }
        if (webSocketClient != null) {
            webSocketClient.close(1000,"用户正常退出");
            webSocketClient = null;
        }

        if (avSyncPlayer != null) {
            avSyncPlayer.release();
            avSyncPlayer = null;
        }
        cancelIdleShowTimer();
        if (idlePlayer != null) {
            idlePlayer.release();
            idlePlayer = null;
        }
    }
    private void initView() {
        ivMic = findViewById(R.id.btn_mic);
        previewView = findViewById(R.id.preview_view_camera);
        ivCapture = findViewById(R.id.btn_capture);
        tvSubtitle = findViewById(R.id.tv_subtitle);
        tvSubtitle.setMovementMethod(new ScrollingMovementMethod());
        ivSubtitleBtn = findViewById(R.id.tv_subtitle_btn);
        // 初始化默认提示
        subtitleBuilder.append("对话记录：");
        tvSubtitle.setText(subtitleBuilder.toString());
        etMessage = findViewById(R.id.et_message);
        btnSend = findViewById(R.id.btn_send);
        ivCamera = findViewById(R.id.btn_camera);
        ivCamera.setOnClickListener(v -> toggleCamera());

        // 结束对话按钮
        btnEndChat = findViewById(R.id.btn_end_chat);
        btnEndChat.setOnClickListener(v -> endChatAndGoBack());
        tvDigitalHuman = findViewById(R.id.tv_digital_human);
        avSyncPlayer = new AVSyncPlayer(tvDigitalHuman);
        avSyncPlayer.setSubtitleCallback(text -> {
            // 字幕更新回调（如需要）
        });

        // 待机视频：帧流停止后延时显示（避免句间短暂空档误判）
        idleVideoView = findViewById(R.id.idle_video);
        idleShowHandler = new Handler(Looper.getMainLooper());
        idleShowRunnable = () -> {
            // 仅当本轮文本已结束（responseDone）且帧流已停才显示待机，避免说话中途误显
            if (aiRoundTextDone) showIdleVideo();
        };
        // 数字人开口 → 隐藏待机；数字人停止/被打断 → 显示待机
        avSyncPlayer.setRenderStateListener(new AVSyncPlayer.RenderStateListener() {
            @Override
            public void onFrameRendered() {
                runOnUiThread(() -> {
                    aiRoundTextDone = false;
                    cancelIdleShowTimer();
                    if (idleCurrentlyVisible) hideIdleVideo();  // 去重：只在可见时才隐藏
                });
            }
            @Override
            public void onRenderStop() {
                runOnUiThread(() -> showIdleVideo());
            }
        });

        // 路线数轴
        routeTimeline = findViewById(R.id.route_timeline);
        routeTimeline.setOnGenerateClickListener(() -> {
            if (webSocketClient != null && wsConnected) {
                try {
                    JSONObject msg = new JSONObject();
                    msg.put("type", "routeGenerate");
                    webSocketClient.send(msg.toString());
                    Log.d(TAG, "发送路线生成请求: routeGenerate");
                } catch (JSONException e) {
                    Log.e(TAG, "构造 routeGenerate 失败", e);
                }
            } else {
                Toast.makeText(this, "WebSocket 未连接", Toast.LENGTH_SHORT).show();
            }
        });
        routeTimeline.setOnCloseClickListener(() -> {
            new AlertDialog.Builder(ChatActivity.this)
                    .setTitle("关闭路线")
                    .setMessage("确认关闭当前 AI 推荐路线？")
                    .setPositiveButton("确认", (d, w) -> sendRouteClose())
                    .setNegativeButton("取消", null)
                    .show();
        });
        routeTimeline.setOnStopClickListener(stop -> {
            if (stop.isCurrent()) {
                // 当前站 → 确认到达
                new AlertDialog.Builder(ChatActivity.this)
                        .setTitle("到达确认")
                        .setMessage("确认已到达「" + stop.getName() + "」？")
                        .setPositiveButton("确认到达", (d, w) -> sendRouteArrive(stop.getStopIndex()))
                        .setNegativeButton("取消", null)
                        .show();
            } else if (stop.isUpcoming()) {
                Toast.makeText(this, "请先到达「" + stop.getName() + "」", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "已到达: " + stop.getName(), Toast.LENGTH_SHORT).show();
            }
        });
        tvDigitalHuman.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture st, int width, int height) {
                if (avSyncPlayer != null) {
                    // ★ 必须在创建 Surface 前设置缓冲区大小，否则 MediaCodec 输出帧
                    // 无法正确渲染到 TextureView（某些设备上画面完全不显示）
                    st.setDefaultBufferSize(AVSyncPlayer.VIDEO_WIDTH, AVSyncPlayer.VIDEO_HEIGHT);
                    avSyncPlayer.onSurfaceReady(new Surface(st));
                    avSyncPlayer.updateTransform();
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture st, int width, int height) {
                if (avSyncPlayer != null) avSyncPlayer.updateTransform();
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture st) {
                if (avSyncPlayer != null) avSyncPlayer.onSurfaceDestroyed();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture st) {
            }
        });
    }

    // ====================== 路线相关 ======================

    /**
     * 1.16.1 恢复当前激活路线（WebSocket 连接成功后调用）
     */
    private void loadCurrentRoute() {
        if (attractionId == null || attractionId.isEmpty()) return;
        ApiService.getInstance().getCurrentRoute(attractionId).enqueue(new retrofit2.Callback<BaseResponse<RoutePlanVO>>() {
            @Override
            public void onResponse(retrofit2.Call<BaseResponse<RoutePlanVO>> call,
                                   retrofit2.Response<BaseResponse<RoutePlanVO>> response) {
                BaseResponse<RoutePlanVO> body = response.body();
                if (body != null && body.getCode() == 1 && body.getData() != null) {
                    RoutePlanVO route = body.getData();
                    runOnUiThread(() -> {
                        routeTimeline.setRoute(route);
                        startArrivalMonitoring(route);
                        Log.d(TAG, "路线恢复成功: " + route.getTitle() + " stops=" + route.getStops().size());
                    });
                } else {
                    Log.d(TAG, "无激活路线，data=" + (body != null ? body.getData() : "null"));
                    // 不显示数轴，用户可手动点击"AI路线"按钮生成
                }
            }

            @Override
            public void onFailure(retrofit2.Call<BaseResponse<RoutePlanVO>> call, Throwable t) {
                Log.e(TAG, "路线恢复请求失败: " + t.getMessage());
            }
        });
    }

    // ====================== 数字人待机视频 ======================

    /**
     * 1.17 拉取管理员上传的原始视频地址，开始静音循环待机播放（WS 连接成功后调用）。
     * 无数字人 / 拉取失败时静默跳过，不影响主流程。
     */
    private void loadIdleVideo() {
        if (attractionId == null || attractionId.isEmpty()) return;
        ApiService.getInstance().getDigitalHuman(attractionId).enqueue(new retrofit2.Callback<BaseResponse<DigitalHuman>>() {
            @Override
            public void onResponse(retrofit2.Call<BaseResponse<DigitalHuman>> call,
                                   retrofit2.Response<BaseResponse<DigitalHuman>> response) {
                BaseResponse<DigitalHuman> body = response.body();
                if (body != null && body.getCode() == 1 && body.getData() != null
                        && body.getData().getVideoUrl() != null && !body.getData().getVideoUrl().isEmpty()) {
                    idleVideoUrl = body.getData().getVideoUrl();
                    runOnUiThread(() -> startIdlePlayer(idleVideoUrl));
                    Log.d(TAG, "待机视频地址: " + idleVideoUrl);
                } else {
                    Log.d(TAG, "无数字人原始视频，跳过待机视频");
                }
            }

            @Override
            public void onFailure(retrofit2.Call<BaseResponse<DigitalHuman>> call, Throwable t) {
                Log.e(TAG, "待机视频地址获取失败: " + t.getMessage());
            }
        });
    }

    /** 懒创建 ExoPlayer（静音循环），加载原始视频并显示待机画面。 */
    private void startIdlePlayer(String url) {
        if (idleVideoView == null || url == null || url.isEmpty()) return;
        if (idlePlayer == null) {
            idlePlayer = new ExoPlayer.Builder(this).build();
            idleVideoView.setPlayer(idlePlayer);
            idlePlayer.setRepeatMode(Player.REPEAT_MODE_ALL);
            idlePlayer.setVolume(0f);  // 待机静音，避免与 AI 语音串音
            idlePlayer.addListener(new Player.Listener() {
                @Override
                public void onPlayerError(@NonNull PlaybackException error) {
                    Log.e(TAG, "待机视频播放错误: " + error.getMessage());
                    runOnUiThread(() -> hideIdleVideo());
                }
            });
        }
        idlePlayer.setMediaItem(MediaItem.fromUri(url));
        idlePlayer.prepare();
        idlePlayer.setPlayWhenReady(true);
        showIdleVideo();
    }

    /** 淡入显示待机视频（盖住数字人区域的空白/冻结帧）。 */
    private void showIdleVideo() {
        if (idleVideoView == null || idlePlayer == null) return;
        if (idleCurrentlyVisible) return;  // 去重
        idleVideoView.animate().cancel();
        if (idleVideoView.getVisibility() != View.VISIBLE) {
            idleVideoView.setAlpha(0f);
            idleVideoView.setVisibility(View.VISIBLE);
        }
        idlePlayer.setPlayWhenReady(true);
        idleVideoView.animate().alpha(1f).setDuration(CROSSFADE_MS).start();
        idleCurrentlyVisible = true;
    }

    /** 淡出隐藏待机视频（露出下层正在说话的数字人）。 */
    private void hideIdleVideo() {
        if (idleVideoView == null) return;
        if (!idleCurrentlyVisible) return;  // 去重
        if (idleVideoView.getVisibility() != View.VISIBLE) return;
        idleVideoView.animate().cancel();
        idleVideoView.animate().alpha(0f).setDuration(CROSSFADE_MS).withEndAction(() -> {
            idleVideoView.setVisibility(View.GONE);
            if (idlePlayer != null) idlePlayer.setPlayWhenReady(false);
        }).start();
        idleCurrentlyVisible = false;
    }

    /** 重置"帧流停止后显示待机"定时器（每收到一帧调用一次）。 */
    private void scheduleIdleShowAfterDrain() {
        if (idleShowHandler == null || idleShowRunnable == null) return;
        idleShowHandler.removeCallbacks(idleShowRunnable);
        idleShowHandler.postDelayed(idleShowRunnable, IDLE_SHOW_DELAY_MS);
    }

    private void cancelIdleShowTimer() {
        if (idleShowHandler != null && idleShowRunnable != null) {
            idleShowHandler.removeCallbacks(idleShowRunnable);
        }
    }

    /**
     * 1.16.2 上报到达某地标（手动点选兜底）
     */
    private void sendRouteArrive(int stopIndex) {
        if (webSocketClient == null || !wsConnected) {
            Toast.makeText(this, "WebSocket 未连接", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "routeArrive");
            msg.put("stopIndex", stopIndex);
            webSocketClient.send(msg.toString());
            Log.d(TAG, "发送到达上报: stopIndex=" + stopIndex);
        } catch (JSONException e) {
            Log.e(TAG, "构造 routeArrive 失败", e);
        }
    }

    /**
     * 1.16.2 关闭并清除当前路线
     */
    private void sendRouteClose() {
        if (webSocketClient == null || !wsConnected) {
            Toast.makeText(this, "WebSocket 未连接", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "routeClose");
            webSocketClient.send(msg.toString());
            Log.d(TAG, "发送关闭路线: routeClose");
        } catch (JSONException e) {
            Log.e(TAG, "构造 routeClose 失败", e);
        }
    }

    // ====================== GPS 自动到达判定 ======================

    /**
     * 启动 GPS 连续定位，检测是否到达当前地标
     */
    private void startArrivalMonitoring(RoutePlanVO route) {
        RouteStopVO currentStop = route.getCurrentStop();
        if (currentStop == null) {
            // 所有站都已到达
            stopArrivalMonitoring();
            return;
        }
        // 该站已上报过就不重复监控
        if (currentStop.getStopIndex() == lastAutoArrivedStopIndex) return;

        // 没有有效坐标，只能靠手动
        if (currentStop.getLongitude() == null || currentStop.getLatitude() == null) {
            Log.d(TAG, "当前站无坐标，跳过GPS判定: " + currentStop.getName());
            return;
        }

        // 先停旧监控再开新的（routeUpdate 会触发重启）
        stopArrivalMonitoring();
        gpsNearbyCount = 0;
        arrivalLocationManager = new LocationManager();

        try {
            arrivalLocationManager.startContinuousLocation(this, new LocationManager.OnLocationListener() {
                @Override
                public void onLocationSuccess(double lat, double lng, String address) {
                    if (currentStop.getLongitude() == null || currentStop.getLatitude() == null) return;

                    double distance = haversineDistance(lat, lng,
                            currentStop.getLatitude(), currentStop.getLongitude());

                    if (distance < ARRIVAL_DISTANCE_M) {
                        gpsNearbyCount++;
                        Log.d(TAG, "GPS到达检测: 距「" + currentStop.getName() + "」"
                                + String.format("%.0f", distance) + "m, 连续" + gpsNearbyCount + "次");
                        if (gpsNearbyCount >= ARRIVAL_CONFIRM_COUNT) {
                            Log.i(TAG, "GPS自动到达: " + currentStop.getName());
                            lastAutoArrivedStopIndex = currentStop.getStopIndex();
                            gpsNearbyCount = 0;
                            runOnUiThread(() -> {
                                Toast.makeText(ChatActivity.this,
                                        "已自动到达「" + currentStop.getName() + "」", Toast.LENGTH_SHORT).show();
                                sendRouteArrive(currentStop.getStopIndex());
                            });
                        }
                    } else {
                        gpsNearbyCount = 0;  // 离开范围，重置计数
                    }
                }

                @Override
                public void onLocationError(String error) {
                    // GPS 失败不影响手动判定，仅记录日志
                    Log.w(TAG, "GPS定位失败: " + error);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "启动GPS连续定位失败: " + e.getMessage());
        }
    }

    private void stopArrivalMonitoring() {
        if (arrivalLocationManager != null) {
            arrivalLocationManager.stopLocation();
            arrivalLocationManager = null;
        }
        gpsNearbyCount = 0;
        lastAutoArrivedStopIndex = -1;
    }

    /**
     * Haversine 公式计算两点距离（米），避免额外 SDK 依赖
     */
    private double haversineDistance(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371000; // 地球半径（米）
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

}