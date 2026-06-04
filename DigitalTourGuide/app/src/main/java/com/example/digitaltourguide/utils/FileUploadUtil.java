package com.example.digitaltourguide.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;
import android.provider.MediaStore;

import com.example.digitaltourguide.model.BaseResponse;
import com.example.digitaltourguide.model.admin.FileUploadResponse;
import com.example.digitaltourguide.network.AdminApiService;
import com.example.digitaltourguide.view.admin.ScenicEditActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public abstract class FileUploadUtil {
    private static final String TAG = "FileUploadUtil";
    private static final int REQUEST_SELECT_FILE = 1001;
    private Activity activity;
    private String attractionId;
    private String token; // 登录token
    private int fileIndex; // 当前上传的文件索引(1-4)
    private Context mContext;
    public abstract void onUploadSuccess(String ossUrl,String taskId);
    public abstract void onUploadFailure(Exception e);

    //构造器
    public FileUploadUtil(Activity activity, String token,String attractionId,  int fileIndex) {
        this.activity = activity;
        this.token = token;
        this.attractionId = attractionId;
        this.fileIndex = fileIndex;
        this.mContext = activity;
    }

    // 构造方法传入Context
    public FileUploadUtil(Context context) {
        this.mContext = context;
        if (context instanceof Activity) {
            this.activity = (Activity) context;
        }
        this.token = SpUtils.getAdminToken(context);
        this.fileIndex = 1; // 默认值
    }

    //1.打开文件选择器
    public void openFileChooser(){
        Intent intent=new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*"); //所有文件类型
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        //过滤仅支持的文件格式
        intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{
           "application/msword", //.doc
           "application/vnd.openxmlformats-officedocument.wordprocessingml.document", //.doc
           "application/pdf" //pdf.
        });
        activity.startActivityForResult(intent,REQUEST_SELECT_FILE);
    }

    //2.处理文件选择器结果
    public void handleFileResult(int requestCode,int resultCode,Intent data){
        if(requestCode==REQUEST_SELECT_FILE && resultCode==Activity.RESULT_OK){
            Uri uri=data.getData();
            if(uri!=null){
                //检验文件格式
                String mimeType=activity.getContentResolver().getType(uri);
                if(isValidFileType(mimeType)){
                    //转为file对象
                    File file=uriToFile(uri);
                    if(file!=null){
                        // 通知 Activity 文件已选择，存储到本地列表，并更新 UI
                        if (activity instanceof ScenicEditActivity) {
                            ((ScenicEditActivity) activity).onFileSelected(fileIndex, file);
                        }
                        // 这里不自动上传，等保存时统一上传
                    } else {
                        Toast.makeText(activity, "文件转换失败", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(activity, "仅支持.doc/.pdf/.docx格式", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    //文件上传
    public void uploadFileToOss(File file) {
        if (token == null) token = SpUtils.getAdminToken(mContext);
        if (attractionId == null || attractionId.isEmpty()) {
            if (activity != null) {
                Toast.makeText(activity, "景点ID缺失，无法上传", Toast.LENGTH_SHORT).show();
            }
            onUploadFailure(new Exception("景点ID缺失"));
            return;
        }
        Log.d(TAG, "上传文件，attractionId=" + attractionId);
        MediaType mediaType;
        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".pdf")) {
            mediaType = MediaType.parse("application/pdf");
        } else if (fileName.endsWith(".doc")) {
            mediaType = MediaType.parse("application/msword");
        } else if (fileName.endsWith(".docx")) {
            mediaType = MediaType.parse("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        } else {
            mediaType = MediaType.parse("application/octet-stream");
        }
        RequestBody fileBody = RequestBody.create(mediaType, file);
        MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", file.getName(), fileBody);
        RequestBody typePart = RequestBody.create(MediaType.parse("text/plain"), "file");

        AdminApiService.getInstance()
                .uploadFileToOSS("Bearer " + token,attractionId,filePart, typePart)
                .enqueue(new Callback<BaseResponse<FileUploadResponse>>() {
                    @Override
                    public void onResponse(Call<BaseResponse<FileUploadResponse>> call, Response<BaseResponse<FileUploadResponse>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            BaseResponse<FileUploadResponse> result = response.body();
                            if (result.getCode() == 1 && result.getData() != null) {
                                String ossUrl = result.getData().getOssUrl();   // 从这里获取
                                String taskId = result.getData().getTaskId();   // 如果需要可以保存
                                Toast.makeText(activity, "文件上传成功", Toast.LENGTH_SHORT).show();
                                updateUIAfterSuccess(ossUrl, file.getName(), getFileType(file.getName()));
                                onUploadSuccess(ossUrl,taskId);
                                Log.d(TAG, "上传响应: code=" + result.getCode() + ", msg=" + result.getMsg());
                                if (result.getData() != null) {
                                    Log.d(TAG, "ossUrl=" + result.getData().getOssUrl() + ", taskId=" + result.getData().getTaskId());
                                }
                            } else {
                                // 失败处理
                                onUploadFailure(new Exception("Upload failed: " + result.getMsg()));
                            }
                        } else {
                            // 失败处理
                            onUploadFailure(new Exception("HTTP error: " + response.code()));
                        }
                    }
                    @Override
                    public void onFailure(Call<BaseResponse<FileUploadResponse>> call, Throwable t) {
                        Toast.makeText(activity, "网络异常: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                        onUploadFailure(new Exception(t));
                    }
                });
    }

    //更新ui
    private void updateUIAfterSuccess(String ossUrl, String fileName, String fileType) {
        if (activity instanceof ScenicEditActivity) {
            ((ScenicEditActivity) activity).updateFileUI(fileIndex, ossUrl, fileName, fileType,attractionId);
        }
    }


    public static String getFileType(String fileName){
        if(fileName.endsWith(".doc")) return "doc";
        if(fileName.endsWith(".docx")) return "docx";
        if(fileName.endsWith(".pdf")) return "pdf";
        return "other";
    }
    private boolean isValidFileType(String mimeType) {
        return mimeType!=null && (
                mimeType.equals("application/msword") ||
                        mimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") ||
                        mimeType.equals("application/pdf")
        );
    }

    private File uriToFile(Uri uri){
        String originalFileName = getFileName(uri);  // 应包含扩展名
        try {
            InputStream is=activity.getContentResolver().openInputStream(uri);
            //创建临时文件
            File file=new File(activity.getCacheDir(),getFileName(uri));
            FileOutputStream fos=new FileOutputStream(file);
            byte[] buffer=new byte[1024];
            int len;
            while((len=is.read(buffer))!=-1){
                fos.write(buffer,0,len);
            }
            fos.close();
            is.close();
            return file;
        } catch (Exception e) {
            Log.e(TAG, "Uri转File失败: ", e);
            return null;
        }
    }

    private String getFileName(Uri uri){
        String fileName="file_"+System.currentTimeMillis();
        if(uri.getScheme().equals("content")){
            try(Cursor cursor=activity.getContentResolver().query(uri,null,null,null,null)){
                if(cursor!=null && cursor.moveToFirst()){
                    int nameIndex=cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                    if(nameIndex!=-1){
                        fileName= cursor.getString(nameIndex);
                    }
                }
            }
        }
        if(fileName==null){
            fileName=uri.getPath();
            int cut=fileName.lastIndexOf('/');
            if(cut!=-1) fileName=fileName.substring(cut+1);
        }
        return fileName;
    }



}
