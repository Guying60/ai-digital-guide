package com.example.digitaltourguide.view.admin;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.digitaltourguide.R;
import com.example.digitaltourguide.databinding.ActivityScenicEditBinding;
import com.example.digitaltourguide.model.LocationManager;
import com.example.digitaltourguide.model.admin.AddAttractionRequest;
import com.example.digitaltourguide.model.admin.AdminAttraction;
import com.example.digitaltourguide.model.BaseResponse;
import com.example.digitaltourguide.model.admin.FileItem;
import com.example.digitaltourguide.network.RetrofitClient;
import com.example.digitaltourguide.utils.FileUploadUtil;
import com.example.digitaltourguide.utils.SpUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ScenicEditActivity extends AppCompatActivity {
    private Handler pollHandler=new Handler();//用于切换线程
    private Runnable pollRunnable;
    private SparseArray<String> fileIdMap=new SparseArray<>();
    private int pendingUploadCount = 0;   // 待上传的文件数量
    private int uploadedCount = 0;        // 已上传成功的文件数量
    private boolean isWaitingForUpload = false; // 是否处于等待上传完成的状态
    // 当前景点信息（回显后存储）
    private AdminAttraction currentAttraction;
    // 封面URL（上传后赋值）
    private String coverUrl = "";
    private String currentAttractionId,token;
    private ImageView ivCover;
    private Button btnSave;
    private LinearLayout llUpload;
    private TextView tvAIHuman,tvEditCover;
    private ImageView ivBack;
    private Spinner spinnerType;
    private EditText etAttractionName;
    private boolean isCoverChanged = false; // 封面是否修改
    private boolean isFileChanged = false;
    private boolean isNameChanged = false;
    private boolean isContentChanged = false;
    private boolean isTypeChanged = false;
    private boolean isLocationChanged = false;   // 位置是否修改
    // 位置信息
    private Double savedLongitude, savedLatitude;
    private String savedProvince, savedCity, savedDistrict, savedAdcode;
    // 位置显示控件
    private TextView tvLongitude, tvLatitude, tvLocationAddress;
    int currentFileIndex=0;
    private List<File> fileList = new ArrayList<>();
    private String selectedCoverUrl; // 选中的图片本地路径/上传后的coverUrl
    private static final String TAG="ScenicEditActivity";
    private static final int REQUEST_LOCATION_PERMISSION = 2001;
    private ActivityScenicEditBinding binding;
    private ActivityResultLauncher<Intent> mapPickerLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityScenicEditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initView();
        initTypeSpinner();//初始化
        updateSaveButtonState();//初始化按钮

        token = SpUtils.getAdminToken(this);
        Log.d(TAG,"token:"+token);
        String passedId = getIntent().getStringExtra("attraction_id");
        if (!TextUtils.isEmpty(passedId)) {
            this.currentAttractionId = passedId;   // 赋给成员变量
        } else {
            this.currentAttractionId = null;       // 明确为新增模式
        }
        String adminId=SpUtils.getAdminId(this);
        Log.d(TAG,"attractionId:"+currentAttractionId);
        Log.d(TAG,"attractionId:"+adminId);
        fetchAttractionData();//数据回显

        fileList.add(null);
        fileList.add(null);
        fileList.add(null);
        fileList.add(null);

        tvEditCover.setOnClickListener(v -> checkPermissionAndOpenGallery());

        btnSave.setOnClickListener(v -> saveAttractionToServer());


        tvAIHuman.setOnClickListener(v->{
            Intent intent = new Intent(ScenicEditActivity.this, ManageAIHumanActivity.class);
            intent.putExtra("attraction_id", currentAttractionId);
            startActivity(intent);
            overridePendingTransition(R.anim.sibling_fade_in, R.anim.sibling_fade_out);
        });

        // 地图选点结果回调
        mapPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        savedLatitude = data.getDoubleExtra("latitude", 0);
                        savedLongitude = data.getDoubleExtra("longitude", 0);
                        savedProvince = data.getStringExtra("province");
                        savedCity = data.getStringExtra("city");
                        savedDistrict = data.getStringExtra("district");
                        savedAdcode = data.getStringExtra("adcode");
                        String address = data.getStringExtra("address");

                        if (tvLongitude != null) tvLongitude.setText(String.format("%.6f", savedLongitude));
                        if (tvLongitude != null) tvLongitude.setTextColor(getColor(R.color.black));
                        if (tvLatitude != null) tvLatitude.setText(String.format("%.6f", savedLatitude));
                        if (tvLatitude != null) tvLatitude.setTextColor(getColor(R.color.black));
                        tvLocationAddress.setText(address != null && !address.isEmpty()
                                ? address
                                : (savedProvince + " " + savedCity + " " + savedDistrict));
                        tvLocationAddress.setTextColor(getColor(R.color.black));

                        isLocationChanged = true;
                        updateSaveButtonState();
                        Toast.makeText(ScenicEditActivity.this, "位置已选择", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void uploadSingleFile(int fileIndex,File file){
        if (currentAttractionId == null || currentAttractionId.isEmpty()) {
            Toast.makeText(this, "景点ID未加载，请稍后重试", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(TAG, "准备上传文件，attractionId=" + currentAttractionId);
        FileUploadUtil util=new FileUploadUtil(this, token, currentAttractionId, fileIndex) {
            @Override
            public void onUploadFailure(Exception e) {
                Toast.makeText(ScenicEditActivity.this, "文件上传失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onUploadSuccess(String ossUrl,String taskId) {
                //startPollingTask(taskId);
                Toast.makeText(ScenicEditActivity.this, "文件上传成功", Toast.LENGTH_SHORT).show();
            }
        };
        util.uploadFileToOss(file);
    }

    private void startPollingTask(String taskId) {
        // 停止之前的轮询（如果有）
        if (pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
        }
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                RetrofitClient.getAdminApiService()
                        .checkDocumentStatus("Bearer " + token, taskId)
                        .enqueue(new Callback<BaseResponse<String>>() {
                            @Override
                            public void onResponse(Call<BaseResponse<String>> call, Response<BaseResponse<String>> response) {
                                if (response.isSuccessful() && response.body() != null) {
                                    BaseResponse<String> result = response.body();
                                    if (result.getCode() == 1 && result.getData() != null) {
                                        String status = result.getData();
                                        switch (status) {
                                            case "PROCESSING":
                                                // 继续轮询，2秒后再查
                                                pollHandler.postDelayed(pollRunnable, 2000);
                                                break;
                                            case "SUCCESS":
                                                Toast.makeText(ScenicEditActivity.this, "解析成功", Toast.LENGTH_SHORT).show();
                                                fetchFileList(); // 刷新文件列表
                                                break;
                                            case "FAILED":
                                                Toast.makeText(ScenicEditActivity.this, "解析失败", Toast.LENGTH_SHORT).show();
                                                fetchFileList(); // 刷新文件列表
                                                break;
                                            default:
                                                break;
                                        }
                                    } else {
                                        // 未知错误，停止轮询
                                        Toast.makeText(ScenicEditActivity.this, "解析状态查询失败：" + result.getMsg(), Toast.LENGTH_SHORT).show();
                                    }
                                } else {
                                    // 请求失败，停止轮询
                                    Toast.makeText(ScenicEditActivity.this, "解析状态查询网络错误", Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onFailure(Call<BaseResponse<String>> call, Throwable t) {
                                Toast.makeText(ScenicEditActivity.this, "解析状态查询失败：" + t.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        };
        // 开始第一次轮询（立即执行）
        pollHandler.post(pollRunnable);
    }
    //上传文件后ui更新
    public void updateFileUI(int fileIndex,String ossUrl,String fileName,String fileType,String fileId){
        if (fileIndex == 0) return;
        if(fileId!=null && !fileId.isEmpty()){
            fileIdMap.put(fileIndex,fileId);
        }
        // 获取对应槽位的控件
        TextView tvFileName = findViewById(getResources().getIdentifier("file_name_" + fileIndex, "id", getPackageName()));
        ImageView ivDelete = findViewById(getResources().getIdentifier("iv_delete_" + fileIndex, "id", getPackageName()));
        ImageView ivFileIcon = findViewById(getResources().getIdentifier("iv_file_icon_" + fileIndex, "id", getPackageName()));

        if (tvFileName != null) {
            tvFileName.setText(fileName);
            tvFileName.setTextColor(getColor(R.color.black)); // 正常颜色
        }
        if (ivDelete != null) {
            ivDelete.setVisibility(View.VISIBLE);
        }
        if (ivFileIcon != null) {
            ivFileIcon.setVisibility(View.VISIBLE);
            ivFileIcon.setImageResource(fileType.equals("pdf") ? R.drawable.ic_pdf : R.drawable.ic_word);
        }

        // 处理等待上传计数（如果仍在批量上传流程中）
        if (isWaitingForUpload) {
            uploadedCount++;
            if (uploadedCount >= pendingUploadCount) {
                isWaitingForUpload = false;
                for (int i = 0; i < fileList.size(); i++) fileList.set(i, null);
                isFileChanged = false;
                updateSaveButtonState();
                fetchAttractionData();
            }
        }
    }

    //删除后恢复ui
    private void resetFileUI(int fileIndex) {
        // 弹窗确认删除
        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("确定要删除这个文件吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                            String fileId=fileIdMap.get(fileIndex);
                            if(fileId!=null && !fileId.isEmpty()){
                                deleteFileFromServer(fileId,fileIndex);
                            }else{
                                removeLocalFile(fileIndex);
                            }
        })
                .setNegativeButton("取消", null)
                .show();
    }
    private void deleteFileFromServer(String fileId,int fileIndex){
        RetrofitClient.getAdminApiService()
                .deleteDocument("Bearer " + token, fileId)
                .enqueue(new Callback<BaseResponse<Void>>() {
                    @Override
                    public void onResponse(Call<BaseResponse<Void>> call, Response<BaseResponse<Void>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            BaseResponse<Void> result = response.body();
                            if (result.getCode() == 1) {
                                Toast.makeText(ScenicEditActivity.this, "删除成功", Toast.LENGTH_SHORT).show();
                                removeLocalFile(fileIndex);
                                fileIdMap.remove(fileIndex);
                            } else {
                                Toast.makeText(ScenicEditActivity.this, "删除失败：" + result.getMsg(), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(ScenicEditActivity.this, "删除失败，请重试", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<BaseResponse<Void>> call, Throwable t) {
                        Toast.makeText(ScenicEditActivity.this, "网络错误：" + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }


    private void removeLocalFile(int fileIndex) {
        if (fileIndex - 1 < fileList.size()) {
            fileList.set(fileIndex - 1, null);
        }
        fileIdMap.remove(fileIndex);

        // 恢复 UI
        TextView tvFileName = findViewById(getResources().getIdentifier("file_name_" + fileIndex, "id", getPackageName()));
        ImageView ivDelete = findViewById(getResources().getIdentifier("iv_delete_" + fileIndex, "id", getPackageName()));
        ImageView ivFileIcon = findViewById(getResources().getIdentifier("iv_file_icon_" + fileIndex, "id", getPackageName()));

        if (tvFileName != null) {
            tvFileName.setText("暂无文件");
        }
        if (ivDelete != null) {
            ivDelete.setVisibility(View.GONE);
        }
        if (ivFileIcon != null) {
            ivFileIcon.setVisibility(View.GONE);
        }

        isFileChanged = true;
        updateSaveButtonState();
        Toast.makeText(this, "已删除文件", Toast.LENGTH_SHORT).show();
    }

    public void onFileSelected(int index, File file) {
        // 直接调用已有的上传方法
        uploadSingleFile(index, file);
    }

    //处理文件选择结果
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void fetchAttractionData() {
        if (TextUtils.isEmpty(currentAttractionId)) {
            // 新增模式：清空表单，按钮文字改为“添加景点”
            etAttractionName.setText("");
            spinnerType.setSelection(0);
            isCoverChanged = false;
            isFileChanged = false;
            isNameChanged = false;
            isTypeChanged = false;
                                isLocationChanged = false;
            updateSaveButtonState();
            btnSave.setText("添加景点");
            // 如果封面没有上传，按钮可能不可用，但这里让按钮可用（后续保存时会校验封面）
            btnSave.setEnabled(true);
            return;
        }
        // 2. 用Retrofit发起请求（严格使用Retrofit的Callback，不是OkHttp的）
        RetrofitClient.getAdminApiService()
                .getAttractionDetail("Bearer " + token,currentAttractionId)
                .enqueue(new retrofit2.Callback<BaseResponse<AdminAttraction>>() { // 必须是retrofit2.Callback！
                    @Override
                    public void onResponse(retrofit2.Call<BaseResponse<AdminAttraction>> call,
                                           retrofit2.Response<BaseResponse<AdminAttraction>> response) {

                        if (isFinishing() || isDestroyed()) return;

                        if (!response.isSuccessful()) {
                            Toast.makeText(ScenicEditActivity.this, "请求失败：" + response.code(), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        BaseResponse<AdminAttraction> result = response.body();
                        if (result == null) {
                            Toast.makeText(ScenicEditActivity.this, "服务器返回空数据", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Log.d(TAG, "业务code：" + result.getCode());
                        Log.d(TAG, "返回消息：" + result.getMsg());

                        if (response.isSuccessful() && response.body() != null) {
                            if (result.getCode() == 1 && result.getData() != null) {
                                currentAttraction = result.getData();
                                currentAttractionId=currentAttraction.getId();
                                Log.d(TAG,"景点id："+currentAttractionId);
                                  fetchFileList();
                                coverUrl = currentAttraction.getCoverUrl();
                                if(coverUrl!=null && !coverUrl.isEmpty()){
                                    selectedCoverUrl=coverUrl;
                                    isCoverChanged=false;
                                }
                                etAttractionName.setText(currentAttraction.getAttractionName());
                                // 填充UI
                                Glide.with(getApplicationContext())
                                        .load(coverUrl)
                                        .placeholder(R.drawable.ic_add)
                                        .into(ivCover);
                               spinnerType.setSelection(currentAttraction.getType());

                                // 回显位置信息
                                savedLongitude = currentAttraction.getLongitude();
                                savedLatitude = currentAttraction.getLatitude();
                                savedProvince = currentAttraction.getProvince();
                                savedCity = currentAttraction.getCity();
                                savedDistrict = currentAttraction.getDistrict();
                                savedAdcode = currentAttraction.getAdcode();
                                if (savedLongitude != null && savedLatitude != null) {
                                    if (tvLongitude != null) tvLongitude.setText(String.format("%.6f", savedLongitude));
                                    if (tvLongitude != null) tvLongitude.setTextColor(getColor(R.color.black));
                                    if (tvLatitude != null) tvLatitude.setText(String.format("%.6f", savedLatitude));
                                    if (tvLatitude != null) tvLatitude.setTextColor(getColor(R.color.black));
                                    String addr = savedProvince != null ? savedProvince : "";
                                    addr += savedCity != null ? " " + savedCity : "";
                                    addr += savedDistrict != null ? " " + savedDistrict : "";
                                    tvLocationAddress.setText(addr.trim());
                                    tvLocationAddress.setTextColor(getColor(R.color.black));
                                }

                                // 重置所有修改标志
                                isCoverChanged = false;
                                isFileChanged = false;
                                isNameChanged = false;
                                isContentChanged = false;
                                isTypeChanged = false;
                                isLocationChanged = false;
                                updateSaveButtonState();
                            }// 2. 无景点（code=400）：弹窗提示msg
                            else if (result.getCode() == 400) {
                                new AlertDialog.Builder(ScenicEditActivity.this)
                                        .setTitle("提示")
                                        .setMessage(result.getMsg())
                                        .setPositiveButton("确定", null)
                                        .show();
                                etAttractionName.setText("");
                                spinnerType.setSelection(0);

                                isCoverChanged = false;
                                isFileChanged = false;
                                isNameChanged = false;
                                isContentChanged = false;
                                isTypeChanged = false;
                                isLocationChanged = false;
                                updateSaveButtonState();
                                btnSave.setText("添加景点");
                                btnSave.setEnabled(false);
                            }
                        }
                    }

                    @Override
                    public void onFailure(retrofit2.Call<BaseResponse<AdminAttraction>> call, Throwable t) {
                        if (token.isEmpty() ) {
                            Toast.makeText(ScenicEditActivity.this, "登录信息失效，请重新登录", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void fetchFileList() {
        if (currentAttractionId == null || currentAttractionId.isEmpty()) {
            Log.e(TAG, "currentAttractionId 为空，无法获取文件列表");
            return;
        }
        Log.d(TAG, "fetchFileList, currentAttractionId=" + currentAttractionId);
        RetrofitClient.getAdminApiService()
                .getFileList("Bearer " + token,currentAttractionId)
                .enqueue(new Callback<BaseResponse<List<FileItem>>>() {
                    @Override
                    public void onResponse(Call<BaseResponse<List<FileItem>>> call,
                                           Response<BaseResponse<List<FileItem>>> response) {
                        if (isFinishing() || isDestroyed()) return;
                        Log.d(TAG, "文件回显 response.code() = " + response.code());
                        if (response.isSuccessful() && response.body() != null) {
                            BaseResponse<List<FileItem>> result = response.body();
                            Log.d(TAG, "文件回显 result.code = " + result.getCode());
                            Log.d(TAG, "文件回显 result.msg = " + result.getMsg());
                            if(result.getData()!=null){
                                Log.d(TAG,"文件回显data size="+result.getData().size());
                            }else{
                                Log.d(TAG,"文件回显data为null");
                            }
                            if (result.getData() != null && !result.getData().isEmpty()) {
                                List<FileItem> files = result.getData();
                                int fileIndex = 0;
                                for (int i = 0; i < files.size() && i < 4; i++) {
                                    FileItem item = files.get(i);
                                    updateFileUI(fileIndex + 1, item.getOssUrl(),
                                            item.getFileName(), item.getFileType(),String.valueOf(item.getId()));
                                    fileIndex++;
                                }
                            }else{
                                Log.d(TAG,"文件列表为空或data为null");
                            }
                        }else {
                            Log.e(TAG,"文件回显请求失败："+response.code());
                        }
                    }
                    @Override
                    public void onFailure(Call<BaseResponse<List<FileItem>>> call, Throwable t) {
                        // 必须实现 onFailure 处理网络错误
                        Toast.makeText(ScenicEditActivity.this, "请求失败：" + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveAttractionToServer() {
        String attractionName = etAttractionName.getText().toString().trim();
        int type = spinnerType.getSelectedItemPosition();

        if (selectedCoverUrl == null || selectedCoverUrl.isEmpty()) {
            Toast.makeText(this, "请选择封面图片", Toast.LENGTH_SHORT).show();
            return;
        }
        if (attractionName.isEmpty()) {
            Toast.makeText(this, "请输入景点名称", Toast.LENGTH_SHORT).show();
            return;
        }


        Call<BaseResponse<AdminAttraction>> call;
        if (currentAttractionId != null && !currentAttractionId.isEmpty()) {
            // 更新模式：使用 PUT 接口，必须传 id
            AddAttractionRequest request = new AddAttractionRequest(
                    currentAttractionId,
                    selectedCoverUrl,
                    attractionName,
                    type
            );
            fillLocationData(request);
            call = RetrofitClient.getAdminApiService().updateAttraction("Bearer " + token, request);
        } else {
            // 新增模式：使用 POST 接口，不传 id
            AddAttractionRequest request = new AddAttractionRequest(
                    selectedCoverUrl,
                    attractionName,
                    type
            );
            fillLocationData(request);
            call = RetrofitClient.getAdminApiService().addAttraction("Bearer " + token, request);
        }

        call.enqueue(new retrofit2.Callback<BaseResponse<AdminAttraction>>() {
            @Override
            public void onResponse(Call<BaseResponse<AdminAttraction>> call, Response<BaseResponse<AdminAttraction>> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful() && response.body() != null) {
                    BaseResponse<AdminAttraction> result = response.body();
                    if (result.getCode() == 1 && result.getData() != null) {
                        AdminAttraction data = result.getData();
                        // 新增成功后，更新 currentAttractionId
                        if (currentAttractionId == null || currentAttractionId.isEmpty()) {
                            currentAttractionId = data.getId();
                            SpUtils.saveAttractionId(ScenicEditActivity.this,currentAttractionId);
                        }
                        Toast.makeText(ScenicEditActivity.this, "保存成功", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);  // 设置成功结果
                        finish();
                    } else {
                        Toast.makeText(ScenicEditActivity.this, "保存失败：" + result.getMsg(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(ScenicEditActivity.this, "保存失败，请重试", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<BaseResponse<AdminAttraction>> call, Throwable t) {
                Toast.makeText(ScenicEditActivity.this, "网络错误：" + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 把位置数据填入请求体
     */
    private void fillLocationData(AddAttractionRequest req) {
        req.setLongitude(savedLongitude);
        req.setLatitude(savedLatitude);
        req.setProvince(savedProvince);
        req.setCity(savedCity);
        req.setDistrict(savedDistrict);
        req.setAdcode(savedAdcode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchDeviceLocation(); // 授权成功，重新获取定位
            } else {
                Toast.makeText(this, "需要定位权限才能获取坐标", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void checkPermissionAndOpenGallery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+：需要 READ_MEDIA_IMAGES 权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else {
            // Android 12 及以下：无需运行时权限，直接打开相册
            openGallery();
        }
    }
    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    openGallery();
                }
            }
    );

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        galleryLauncher.launch(intent);
    }
    private final ActivityResultLauncher<Intent> galleryLauncher=registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result->{
                if(result.getResultCode()==RESULT_OK && result.getData()!=null){
                    Uri imageUri=result.getData().getData();
                    if (imageUri != null) {
                        // 1. 校验 MIME 类型
                        String mimeType = getContentResolver().getType(imageUri);
                        if (!"image/jpeg".equals(mimeType) && !"image/png".equals(mimeType)) {
                            Toast.makeText(this, "仅支持 JPG 或 PNG 格式的图片", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        // 2. 显示预览
                        ivCover.setImageURI(imageUri);
                        // 3. 转换为 File 并上传
                        File coverFile = uriToFile(imageUri,mimeType);
                        if (coverFile != null) {
                            uploadCoverToOss(coverFile);
                        } else {
                            Toast.makeText(this, "无法获取图片文件", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
    );

    private File uriToUploadFile(Uri uri) {
        String fileName = "file_" + System.currentTimeMillis();
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex);
                }
            }
        } catch (Exception ignored) {}
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            File file = new File(getCacheDir(), fileName);
            FileOutputStream fos = new FileOutputStream(file);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
            fos.close();
            is.close();
            return file;
        } catch (Exception e) {
            Log.e(TAG, "Uri转File失败: ", e);
            return null;
        }
    }

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                Uri uri = result.getData().getData();
                if (uri == null) return;
                String mimeType = getContentResolver().getType(uri);
                if (!"application/msword".equals(mimeType)
                        && !"application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(mimeType)
                        && !"application/pdf".equals(mimeType)) {
                    Toast.makeText(this, "仅支持.doc/.pdf/.docx格式", Toast.LENGTH_SHORT).show();
                    return;
                }
                File file = uriToUploadFile(uri);
                if (file == null) {
                    Toast.makeText(this, "文件转换失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                uploadSingleFile(currentFileIndex, file);
            });

    // 封面图片Uri转File
    private File uriToFile(Uri uri, String mimeType) {
        String extension;
        if ("image/jpeg".equals(mimeType)) {
            extension = ".jpg";
        } else if ("image/png".equals(mimeType)) {
            extension = ".png";
        } else {
            return null;
        }
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            File file = new File(getCacheDir(), "cover_" + System.currentTimeMillis() + extension);
            FileOutputStream fos = new FileOutputStream(file);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
            fos.close();
            is.close();
            return file;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    // 上传封面到 OSS，成功后更新 selectedCoverUrl
    private void uploadCoverToOss(File coverFile) {
        /*if (currentAttractionId == null || currentAttractionId.isEmpty()) {
            Toast.makeText(this, "景点ID未加载，无法上传封面", Toast.LENGTH_SHORT).show();
            return;
        }*/

        String name = coverFile.getName().toLowerCase();
        Toast.makeText(this, "封面上传中...", Toast.LENGTH_SHORT).show();

        MediaType mediaType = name.endsWith(".png") ? MediaType.parse("image/png") : MediaType.parse("image/jpeg");
        // 构建请求体
        RequestBody requestFile = RequestBody.create(mediaType, coverFile);
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", coverFile.getName(), requestFile);

        RetrofitClient.getAdminApiService()
                .uploadCover("Bearer " + token,body)
                .enqueue(new retrofit2.Callback<BaseResponse<String>>() {
                    @Override
                    public void onResponse(retrofit2.Call<BaseResponse<String>> call, retrofit2.Response<BaseResponse<String>> response) {
                        Log.d(TAG, "onResponse 被调用，response.code = " + response.code());
                        if (response.isSuccessful() && response.body() != null) {
                            BaseResponse<String> result = response.body();
                            if (result.getCode() == 1 && result.getData() != null) {
                                String ossUrl = result.getData();
                                selectedCoverUrl = ossUrl;
                                isCoverChanged = true;
                                updateSaveButtonState();
                                Toast.makeText(ScenicEditActivity.this, "封面上传成功", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(ScenicEditActivity.this, "封面上传失败：" + result.getMsg(), Toast.LENGTH_SHORT).show();
                                updateSaveButtonState();
                            }
                        } else {
                            Toast.makeText(ScenicEditActivity.this, "封面上传失败，请重试", Toast.LENGTH_SHORT).show();
                            updateSaveButtonState();
                        }
                    }

                    @Override
                    public void onFailure(retrofit2.Call<BaseResponse<String>> call, Throwable t) {
                        Log.d(TAG, "onFailure 被调用，t = " + t.getMessage());
                        Toast.makeText(ScenicEditActivity.this, "网络错误：" + t.getMessage(), Toast.LENGTH_SHORT).show();
                        updateSaveButtonState();
                    }
                });
    }

    //按钮状态修改
    private void updateSaveButtonState() {
        boolean hasAnyChange = isCoverChanged || isFileChanged || isNameChanged || isContentChanged || isTypeChanged || isLocationChanged;
        btnSave.setEnabled(hasAnyChange);
        btnSave.setAlpha(hasAnyChange ? 1.0f : 0.5f);
    }

    /**
     * 获取设备 GPS 位置并逆地理编码获取省市/区/adcode
     */
    private void fetchDeviceLocation() {
        // 高德 SDK 需要同时检查精确定位和粗略定位权限
        boolean hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!hasFine || !hasCoarse) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    REQUEST_LOCATION_PERMISSION);
            return;
        }
        // 检查系统定位服务是否开启
        android.location.LocationManager locMgr = (android.location.LocationManager)
                getSystemService(LOCATION_SERVICE);
        if (locMgr != null && !locMgr.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                && !locMgr.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
            Toast.makeText(this, "请先开启GPS或网络定位服务", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "正在获取位置...", Toast.LENGTH_SHORT).show();
        LocationManager lm = new LocationManager();
        try {
            lm.startDetailLocation(getApplicationContext(), new LocationManager.OnDetailLocationListener() {
                @Override
                public void onLocationSuccess(double latitude, double longitude,
                                              String province, String city, String district,
                                              String adcode, String address) {
                    runOnUiThread(() -> {
                        savedLongitude = longitude;
                        savedLatitude = latitude;
                        savedProvince = province;
                        savedCity = city;
                        savedDistrict = district;
                        savedAdcode = adcode;

                        if (tvLongitude != null) tvLongitude.setText(String.format("%.6f", longitude));
                        if (tvLongitude != null) tvLongitude.setTextColor(getColor(R.color.black));
                        if (tvLatitude != null) tvLatitude.setText(String.format("%.6f", latitude));
                        if (tvLatitude != null) tvLatitude.setTextColor(getColor(R.color.black));
                        tvLocationAddress.setText((address != null && !address.isEmpty())
                                ? address : (province + " " + city + " " + district));
                        tvLocationAddress.setTextColor(getColor(R.color.black));

                        isLocationChanged = true;
                        updateSaveButtonState();
                        Toast.makeText(ScenicEditActivity.this, "位置获取成功", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onLocationError(String error) {
                    Log.e(TAG, "定位失败详情: " + error);
                    runOnUiThread(() -> {
                        Toast.makeText(ScenicEditActivity.this, "定位失败: " + error + "\n请确保GPS已开启且网络正常", Toast.LENGTH_LONG).show();
                    });
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "定位启动异常", e);
            Toast.makeText(this, "定位启动失败: " + e.toString(), Toast.LENGTH_LONG).show();
        }
    }

    private void initTypeSpinner() {
        String[] typeNames = {
                "主题乐园", "博物馆与展馆", "自然公园",
                "风景名胜与休闲度假", "历史文化", "古镇水乡",
                "动植物园与水族馆", "现代地标"
        };

        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,typeNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerType.setAdapter(adapter);
    }

    private void initView() {
        ivCover = findViewById(R.id.iv_cover);
        tvEditCover = findViewById(R.id.tv_edit_cover);
        btnSave = findViewById(R.id.btn_save);
        spinnerType = findViewById(R.id.spinner_type);
        etAttractionName = findViewById(R.id.et_title);
        tvAIHuman=findViewById(R.id.tab_digital_edit);
        llUpload = findViewById(R.id.ll_upload);

        // 左上角返回箭头：回到点位管理页
        ivBack = findViewById(R.id.btn_back);
        ivBack.setOnClickListener(v -> goToPointManager());

        llUpload.setOnClickListener(v -> {
            if (currentAttractionId == null || currentAttractionId.isEmpty()) {
                Toast.makeText(this, "请等待景点加载完成", Toast.LENGTH_SHORT).show();
                return;
            }
            // 找到第一个空槽位（文件名显示”暂无文件”）
            int firstEmptySlot = findFirstEmptySlot();
            if (firstEmptySlot == -1) {
                Toast.makeText(this, "最多上传4个文件", Toast.LENGTH_SHORT).show();
                return;
            }
            currentFileIndex = firstEmptySlot;
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/pdf"
            });
            filePickerLauncher.launch(intent);
        });
        // 为每个删除按钮设置监听
        setupDeleteButton(R.id.iv_delete_1, 1);
        setupDeleteButton(R.id.iv_delete_2, 2);
        setupDeleteButton(R.id.iv_delete_3, 3);
        setupDeleteButton(R.id.iv_delete_4, 4);

        etAttractionName.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(android.text.Editable s) {
                isNameChanged = true;
                updateSaveButtonState();
            }
        });

        spinnerType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                isTypeChanged = true;
                updateSaveButtonState();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // 位置信息
        // tvLongitude / tvLatitude 已从布局移除，仅保留 tvLocationAddress
        tvLocationAddress = findViewById(R.id.tv_location_address);
        Button btnGetLocation = findViewById(R.id.btn_get_location);
        btnGetLocation.setOnClickListener(v -> fetchDeviceLocation());
        Button btnMapPicker = findViewById(R.id.btn_map_picker);
        btnMapPicker.setOnClickListener(v -> {
            Intent intent = new Intent(ScenicEditActivity.this, MapPickerActivity.class);
            if (savedLatitude != null && savedLongitude != null) {
                intent.putExtra("latitude", savedLatitude);
                intent.putExtra("longitude", savedLongitude);
            }
            mapPickerLauncher.launch(intent);
        });
    }

    private int findFirstEmptySlot() {
        for (int i = 1; i <= 4; i++) {
            TextView tvFileName = findViewById(getResources().getIdentifier("file_name_" + i, "id", getPackageName()));
            if (tvFileName != null && "暂无文件".equals(tvFileName.getText().toString())) {
                return i;
            }
        }
        return -1;
    }
    private void setupDeleteButton(int btnId, final int slotIndex) {
        ImageView ivDelete = findViewById(btnId);
        ivDelete.setOnClickListener(v -> resetFileUI(slotIndex));
    }

    private void goToPointManager() {
        Intent intent = new Intent(ScenicEditActivity.this, PointManagerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
        overridePendingTransition(R.anim.sibling_fade_in, R.anim.sibling_fade_out);
    }

    @Override
    public void onBackPressed() {
        goToPointManager();
    }
}
