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
import com.example.digitaltourguide.utils.ImageUtils;
import com.example.digitaltourguide.utils.SpUtils;
import com.google.common.util.concurrent.ListenableFuture;

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
    private int currentLensFacing = CameraSelector.LENS_FACING_BACK;  // 当前使用的摄像头，默认后置
    private static final String TAG="ChatActivity";
    private String conversationId,attractionId;
    private ImageAnalysis imageAnalysis;          // 图像分析用例
    private Handler autoFrameHandler;             // 定时任务处理器
    private Runnable autoFrameRunnable;           // 定时任务
    private Bitmap latestFrameBitmap;             // 最新的一帧
    private final Object frameLock = new Object(); // 线程安全锁
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
    private int binaryMsgCount = 0;           // 二进制消息计数器，用于限频日志
    private volatile boolean isAutoFrameRunning = false;

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
                //开始录音前，如果数字人正在播放，先打断
                if(avSyncPlayer != null && avSyncPlayer.isPlaying()){
                    sendInterrupt();
                    stopPlayback();
                }
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
            String text=etMessage.getText().toString().trim();
            if(text.isEmpty()){
                Toast.makeText(this,"请输入文本",Toast.LENGTH_SHORT).show();
                return;
            }
            //发送前，如果数字人正在播放，就打断
            if(avSyncPlayer != null && avSyncPlayer.isPlaying()){
                sendInterrupt();
                stopPlayback();
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
        ivCapture.setOnClickListener(v->{takePhoto();});
        checkCameraPermissionAndStart();
    }

    private void stopPlayback() {
        if (avSyncPlayer != null) {
            avSyncPlayer.interrupt();
        }
        Log.d("MYTEST", "已主动停止播放，等待下次交互");
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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    // 启动相机拍照
    private void startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
                isCameraOpen = true;
                if (webSocketClient != null) {
                    startAutoFrameSend();
                    sendCameraStatus("on");   // 告知后端摄像头已开启
                }
                // 新增：显示预览画面
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
        stopAutoFrameSend();
        synchronized (frameLock) {
            if (latestFrameBitmap != null && !latestFrameBitmap.isRecycled()) {
                latestFrameBitmap.recycle();
                latestFrameBitmap = null;
            }
        }
        isCameraOpen = false;
        sendCameraStatus("off");
        Log.d("MYTEST", "摄像头已关闭");

        // 新增：隐藏预览画面
        if (previewView != null) {
            previewView.setVisibility(View.GONE);
        }
    }

    private void sendCameraStatus(String status) {
        if(webSocketClient==null){
            Log.w("MYTEST","websocket未连接，无法发送相机状态");
            return;
        }
        try {
            JSONObject json=new JSONObject();
            json.put("type","camera");
            json.put("status", status);
            webSocketClient.send(json.toString());
            Log.d("MYTEST", "发送相机状态: " + status);
        } catch (JSONException e) {
            Log.e("MYTEST", "构造相机状态消息失败", e);
        }
    }

    //摄像头开关
    private void toggleCamera() {
        if (isCameraOpen) {
            stopCamera();
        } else {
            startCamera();   // 重新启动
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

    private void startAutoFrameSend() {
        if(autoFrameHandler==null){
            autoFrameHandler=new Handler(Looper.getMainLooper());
        }
        if(autoFrameRunnable!=null) return;
        isAutoFrameRunning = true;
        autoFrameRunnable=new Runnable() {
            @Override
            public void run() {
                //获取最新帧
                Bitmap frame = null;
                synchronized (frameLock) {
                    if (latestFrameBitmap != null && !latestFrameBitmap.isRecycled()) {
                        frame = latestFrameBitmap.copy(latestFrameBitmap.getConfig(), false);
                    }
                }
                if(frame!=null){
                    //压缩图片
                    Bitmap scaled= ImageUtils.compressBitmap(frame,1280,720);
                    String base64=ImageUtils.bitmapToBase64(scaled);
                    //发送到后端
                    sendPhotoToServer(base64);
                    frame.recycle();
                    if(scaled!=frame) scaled.recycle();
                }
                //每隔1.5秒执行一次
                if(autoFrameHandler!=null && isAutoFrameRunning){
                    autoFrameHandler.postDelayed(this,1500);
                }
            }
        };
        autoFrameHandler.post(autoFrameRunnable);
    }
    private void stopAutoFrameSend(){
        isAutoFrameRunning = false;
        if(autoFrameHandler!=null){
            autoFrameHandler.removeCallbacksAndMessages(null);
            autoFrameHandler=null;
        }
        autoFrameRunnable=null;
        synchronized (frameLock) {
            if (latestFrameBitmap != null && !latestFrameBitmap.isRecycled()) {
                latestFrameBitmap.recycle();
                latestFrameBitmap = null;
            }
        }
    }

    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        return imageProxy.toBitmap();
    }

    private void takePhoto(){
        if (imageCapture == null) return;
        // 拍照并获取Bitmap
        imageCapture.takePicture(ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        Log.d("MYTEST", "✅ 拍照成功");

                        // 1. 转Bitmap
                        Bitmap bitmap = image.toBitmap();
                        // 2. 调用工具类：按文档要求缩放到1280×720以内
                        Bitmap scaledBitmap = ImageUtils.compressBitmap(bitmap, 1280, 720);
                        // 3. 调用工具类：按文档要求80质量转Base64（NO_WRAP，无前缀）
                        String base64 = ImageUtils.bitmapToBase64(scaledBitmap);

                        // 4.保存到系统相册
                       // saveBitmapToGallery(bitmap);

                        // 5. 释放资源
                        image.close();
                        bitmap.recycle();
                        if (scaledBitmap != bitmap) {
                            scaledBitmap.recycle();
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        super.onError(exception);
                        Log.e("MYTEST", "拍照失败", exception);
                        Toast.makeText(ChatActivity.this, "拍照失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void checkCameraPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        } else {
            Log.d("MYTEST", "已有相机权限 → 启动相机预览");
            startCamera();
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

    private void sendPhotoToServer(String base64) {
        if (webSocketClient == null) {
            Log.e("MYTEST", "❌ 发送失败：WebSocket 未连接！");
            return;
        }
        if (base64 == null || base64.isEmpty()) {
            return;
        }

        //Log.d("MYTEST", "准备上传照片，base64 长度：" + base64.length());

        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "photo");
            msg.put("photo", base64);
            String jsonStr = msg.toString();

           // Log.d("MYTEST", "✅ 构造JSON成功，长度：" + jsonStr.length());

            webSocketClient.send(jsonStr);


            //Log.d("MYTEST", "✅ 照片发送成功");

        } catch (JSONException e) {
            Log.e("MYTEST", "❌ JSON构造失败：" + e.getMessage());
            runOnUiThread(()-> Toast.makeText(this, "图片上传失败", Toast.LENGTH_SHORT).show());
        }

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
                startCamera();
            } else {
                Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show();
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
        Log.d("TEST_TOKEN", "token ="+token);
        String userId = SpUtils.getUserId(this);

        String wsUrl = "wss://ai.guying.xyz/ai-project/chat" + "?attractionId=" + attractionId;

        // 2. 构建 OkHttp 客户端（支持 wss 安全协议）
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)   // WebSocket 不设读超时，靠心跳保活
                .writeTimeout(30, TimeUnit.SECONDS)
                .addNetworkInterceptor(chain -> {
                    Request original = chain.request();
                    // 去掉压缩扩展头，强制后端不使用压缩
                    Request newRequest = original.newBuilder()
                            .removeHeader("Sec-WebSocket-Extensions")
                            .build();
                    return chain.proceed(newRequest);
                })
                .build();

        // 3. 构建请求（Header 完全正确）
        Request request = new Request.Builder()
                .url(wsUrl)
                .addHeader("Authorization", "Bearer " + token)  // 空格一定有！
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
                Log.d("MYTEST", "收到后端消息：" + text);
                try {
                    JSONObject json = new JSONObject(text);
                    String type = json.optString("type");

                    if("ready".equals(type)){
                        Log.d("MYTEST", "✅ 服务端就绪，数字人待机");
                    }
                    // 保留你原来的 requestPhoto 逻辑
                   else if ("requestPhoto".equals(type)) {
                        Log.d("MYTEST", "收到拍照请求，启动相机");
                        // 你的拍照代码注释暂时保留，后面再打开
                        runOnUiThread(() -> checkCameraPermissionAndStart());
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
                        runOnUiThread(() -> {
                            aiIsReplying = false;
                        });
                    } else if ("pong".equals(type)) {
                        Log.d("MYTEST", "✅ 心跳回复正常");
                    } else if ("error".equals(type)) {
                        String errorMsg = json.optString("text");
                        Log.d("MYTEST", "❌ 后端报错：" + errorMsg);
                    }else if ("allDone".equals(type)) {
                        Log.d("MYTEST", "收到 allDone，后端已就绪");
                        // 清空队列，准备下一次对话
                        if (avSyncPlayer != null) avSyncPlayer.onConversationEnd();
                        // 麦克风默认关闭，用户手动点击按钮开启
                        runOnUiThread(() -> {
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

                byte[] payload = bytes.toByteArray();
                binaryMsgCount++;
                // ★ 高频二进制消息（25fps 视频+音频），每 50 条才打一次日志，避免 logcat I/O 阻塞 WebSocket 线程
                if (binaryMsgCount % 50 == 1) {
                    Log.d("BINARY", "收到二进制数据 #" + binaryMsgCount + "，长度=" + payload.length);
                }

                if (payload.length == 0) return;

                // 1. 读取首字节标识位
                int typeFlag = payload[0] & 0xFF;
                byte[] frameData = new byte[payload.length - 1];
                System.arraycopy(payload, 1, frameData, 0, frameData.length);

                // 2. 交给 AVSyncPlayer 处理（内部分拣到 AudioQueue / VideoQueue）
                if (typeFlag == 0x01) {  // 音频
                    if (avSyncPlayer != null) avSyncPlayer.onAudioData(frameData);
                } else if (typeFlag == 0x03) {  // H.264 视频
                    if (avSyncPlayer != null) avSyncPlayer.onVideoData(frameData);
                } else {
                    Log.w("BINARY", "未知 typeFlag: 0x" + Integer.toHexString(typeFlag));
                }
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
                    tvSubtitle.scrollTo(0, tvSubtitle.getLineHeight() * tvSubtitle.getLineCount());
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
    }

    //用户说的话
    private void addUserMessage(String text){
        if(text==null || text.isEmpty()) return;
        runOnUiThread(()->{
            subtitleBuilder.append(LINE_SEPARATOR).append("我：").append(text);
            tvSubtitle.setText(subtitleBuilder.toString());
            //自动滚到底部
            tvSubtitle.scrollTo(0,tvSubtitle.getLineHeight()*tvSubtitle.getLineCount());
        });
    }
    private void stopHeartbeat(){
        if(heartbeatExecutor!=null){
            heartbeatExecutor.shutdownNow();
            heartbeatExecutor=null;
        }
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
        //如果相机已经初始化且websocket已连接，则启动自动发送
        if(imageAnalysis!=null && webSocketClient!=null){
            startAutoFrameSend();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAutoFrameSend();
    }

    //页面销毁时释放资源
    @Override
    protected void onDestroy() {
        stopAutoFrameSend();
        stopHeartbeat();
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
        tvDigitalHuman = findViewById(R.id.tv_digital_human);
        avSyncPlayer = new AVSyncPlayer(tvDigitalHuman);
        avSyncPlayer.setSubtitleCallback(text -> {
            // 字幕更新回调（如需要）
        });
        tvDigitalHuman.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture st, int width, int height) {
                if (avSyncPlayer != null) {
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
}