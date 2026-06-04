package com.example.digitaltourguide.repository;

import android.text.TextUtils;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.digitaltourguide.model.BaseResponse;
import com.example.digitaltourguide.model.admin.DigitalHuman;
import com.example.digitaltourguide.model.admin.TestVideoStatus;
import com.example.digitaltourguide.network.AdminApiService;
import com.example.digitaltourguide.network.RetrofitClient;
import com.google.gson.JsonElement;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AIHumanRepository {
    private AdminApiService apiService;

    public AIHumanRepository(){
        apiService= RetrofitClient.getAdminApiService();
    }

    //上传视频
    public LiveData<BaseResponse<String>> uploadVideo(String attractionId, MultipartBody.Part filePart){
        MutableLiveData<BaseResponse<String>> ld=new MutableLiveData<>();
        apiService.uploadDigitalHumanVideo(filePart,attractionId)
                .enqueue(new Callback<BaseResponse<String>>() {
                    @Override
                    public void onResponse(Call<BaseResponse<String>> call, Response<BaseResponse<String>> response) {
                        ld.postValue(response.body());
                    }

                    @Override
                    public void onFailure(Call<BaseResponse<String>> call, Throwable t) {
                        BaseResponse<String> errorResp = new BaseResponse<>();
                        errorResp.setCode(500);
                        errorResp.setMsg("网络错误：" + t.getMessage());
                        ld.postValue(errorResp);
                    }
                });
        return ld;
    }

    // 预加载状态轮询（2.15）
    public LiveData<BaseResponse<String>> getPreloadStatus(String attractionId) {
        MutableLiveData<BaseResponse<String>> ld = new MutableLiveData<>();
        apiService.getPreloadStatus(attractionId).enqueue(new Callback<BaseResponse<String>>() {
            @Override
            public void onResponse(Call<BaseResponse<String>> call, Response<BaseResponse<String>> response) {
                ld.postValue(response.body());
            }
            @Override
            public void onFailure(Call<BaseResponse<String>> call, Throwable t) {
                BaseResponse<String> errorResp = new BaseResponse<>();
                errorResp.setCode(500);
                errorResp.setMsg("网络错误：" + t.getMessage());
                ld.postValue(errorResp);
            }
        });
        return ld;
    }

    // 生成测试视频（2.19）
    public LiveData<BaseResponse<Void>> generateTestVideoManual(String attractionId, String text) {
        MutableLiveData<BaseResponse<Void>> ld = new MutableLiveData<>();
        Map<String, String> body = new HashMap<>();
        if (!TextUtils.isEmpty(text)) body.put("text", text);
        apiService.generateTestVideoRaw(attractionId, body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String rawJson = response.body().string();
                        JSONObject root = new JSONObject(rawJson);
                        int code = root.optInt("code");
                        String msg = root.optString("msg");
                        BaseResponse<Void> result = new BaseResponse<>();
                        result.setCode(code);
                        result.setMsg(msg);
                        ld.postValue(result);
                    } catch (Exception e) {
                        BaseResponse<Void> error = new BaseResponse<>();
                        error.setCode(500);
                        error.setMsg("解析失败：" + e.getMessage());
                        ld.postValue(error);
                    }
                } else {
                    BaseResponse<Void> error = new BaseResponse<>();
                    error.setCode(response.code());
                    error.setMsg("请求失败，code=" + response.code());
                    ld.postValue(error);
                }
            }
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                BaseResponse<Void> error = new BaseResponse<>();
                error.setCode(500);
                error.setMsg("网络错误：" + t.getMessage());
                ld.postValue(error);
            }
        });
        return ld;
    }

    // 测试视频状态轮询（2.20）
    public LiveData<BaseResponse<TestVideoStatus>> getTestVideoStatusManual(String attractionId) {
        Log.e("POLL", "进入手动解析 onResponse");
        MutableLiveData<BaseResponse<TestVideoStatus>> ld = new MutableLiveData<>();
        apiService.getTestVideoStatus(attractionId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String rawJson = response.body().string();
                        Log.d("POLL", rawJson);
                        // 手动解析 JSON
                        JSONObject root = new JSONObject(rawJson);
                        int code = root.optInt("code");
                        String msg = root.optString("msg");
                        JSONObject dataObj = root.optJSONObject("data");
                        TestVideoStatus statusObj = new TestVideoStatus();
                        if (dataObj != null) {
                            statusObj.setStatus(dataObj.optString("status"));
                            statusObj.setVideoUrl(dataObj.optString("videoUrl", null));
                        } else {
                            // data 可能是字符串 "PROCESSING"
                            String dataStr = root.optString("data");
                            if (!TextUtils.isEmpty(dataStr) && !"null".equals(dataStr)) {
                                statusObj.setStatus(dataStr);
                                statusObj.setVideoUrl(null);
                            } else {
                                statusObj.setStatus("UNKNOWN");
                            }
                        }
                        BaseResponse<TestVideoStatus> result = new BaseResponse<>();
                        result.setCode(code);
                        result.setMsg(msg);
                        result.setData(statusObj);
                        ld.postValue(result);
                    } catch (Exception e) {
                        e.printStackTrace();
                        BaseResponse<TestVideoStatus> error = new BaseResponse<>();
                        error.setCode(500);
                        error.setMsg("解析失败：" + e.getMessage());
                        ld.postValue(error);
                    }
                } else {
                    BaseResponse<TestVideoStatus> error = new BaseResponse<>();
                    error.setCode(response.code());
                    error.setMsg("请求失败，code=" + response.code());
                    ld.postValue(error);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                BaseResponse<TestVideoStatus> error = new BaseResponse<>();
                error.setCode(500);
                error.setMsg("网络错误：" + t.getMessage());
                ld.postValue(error);
            }
        });
        return ld;
    }

    // 新增或修改数字人
    public LiveData<BaseResponse<DigitalHuman>> upsertDigitalHuman(DigitalHuman digitalHuman) {
        Log.e("ManageAIHumanActivity", "准备发起新增/修改数字人请求，digitalHuman=" + digitalHuman);
        MutableLiveData<BaseResponse<DigitalHuman>> ld = new MutableLiveData<>();
        apiService.upsertDigitalHuman(digitalHuman).enqueue(new Callback<BaseResponse<DigitalHuman>>() {
            @Override
            public void onResponse(Call<BaseResponse<DigitalHuman>> call, Response<BaseResponse<DigitalHuman>> response) {
                Log.e("UPLOAD", "onResponse 被调用，code=" + response.code());
                Log.d("upload", "code=" + response.code() + " body=" + response.body());
                ld.postValue(response.body());
            }

            @Override
            public void onFailure(Call<BaseResponse<DigitalHuman>> call, Throwable t) {
                Log.e("UPLOAD", "onFailure 被调用", t);
                BaseResponse<DigitalHuman> errorResp = new BaseResponse<>();
                errorResp.setCode(500);
                errorResp.setMsg("网络错误：" + t.getMessage());
                ld.postValue(errorResp);
            }
        });
        return ld;
    }

    // 查询
    public LiveData<BaseResponse<DigitalHuman>> query(String aid) {
        MutableLiveData<BaseResponse<DigitalHuman>> ld = new MutableLiveData<>();
        apiService.queryDigitalHuman(aid).enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<BaseResponse<DigitalHuman>> call, Response<BaseResponse<DigitalHuman>> resp) {
                ld.postValue(resp.body());
            }
            @Override
            public void onFailure(Call<BaseResponse<DigitalHuman>> call, Throwable t) {
                ld.postValue(null);
            }
        });
        return ld;
    }

    // 删除
    public LiveData<BaseResponse<Void>> delete(String id) {
        MutableLiveData<BaseResponse<Void>> ld = new MutableLiveData<>();
        apiService.deleteDigitalHuman(id).enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<BaseResponse<Void>> call, Response<BaseResponse<Void>> resp) {
                ld.postValue(resp.body());
            }
            @Override
            public void onFailure(Call<BaseResponse<Void>> call, Throwable t) {
                ld.postValue(null);
            }
        });
        return ld;
    }
}
