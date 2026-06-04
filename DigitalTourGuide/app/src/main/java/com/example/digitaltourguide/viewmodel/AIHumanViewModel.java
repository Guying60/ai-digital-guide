package com.example.digitaltourguide.viewmodel;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.digitaltourguide.model.BaseResponse;
import com.example.digitaltourguide.model.admin.DigitalHuman;
import com.example.digitaltourguide.model.admin.TestVideoStatus;
import com.example.digitaltourguide.repository.AIHumanRepository;

import java.security.DigestException;

import okhttp3.MultipartBody;

public class AIHumanViewModel extends ViewModel {
    private AIHumanRepository  repository;

    // 为了更好的观察者模式，我们改用单一 LiveData（避免重复 observe 问题）
    private MutableLiveData<BaseResponse<DigitalHuman>> queryResult = new MutableLiveData<>();
    private MutableLiveData<BaseResponse<Void>> deleteResult = new MutableLiveData<>();
    // 新增/修改结果
    private MutableLiveData<BaseResponse<DigitalHuman>> upsertResult = new MutableLiveData<>();
    // 上传视频结果
    private MutableLiveData<BaseResponse<String>> uploadResult = new MutableLiveData<>();
    // 预加载状态轮询
    private MutableLiveData<BaseResponse<String>> preloadStatusResult = new MutableLiveData<>();
    // 生成测试视频
    private MutableLiveData<BaseResponse<Void>> generateTestVideoResult = new MutableLiveData<>();
    // 测试视频状态轮询
    private MutableLiveData<BaseResponse<TestVideoStatus>> testVideoStatusResult = new MutableLiveData<>();
    private MutableLiveData<BaseResponse<TestVideoStatus>> testVideoStatusLiveData = new MutableLiveData<>();
    public AIHumanViewModel(){
        repository=new AIHumanRepository();
    }


    // 观察者使用这个 LiveData
    public LiveData<BaseResponse<TestVideoStatus>> getTestVideoStatusLiveData() {
        return testVideoStatusLiveData;
    }

    public LiveData<BaseResponse<TestVideoStatus>> getTestVideoStatus(String attractionId) {
        // 调用 repository 获取数据，并更新到 LiveData
        repository.getTestVideoStatusManual(attractionId).observeForever(response -> {
            testVideoStatusLiveData.postValue(response);
        });
        return testVideoStatusLiveData;
    }

    public LiveData<BaseResponse<TestVideoStatus>> getTestVideoStatusResult() {
        return testVideoStatusResult;
    }

    public void fetchTestVideoStatus(String attractionId) {
        Log.e("POLL", "fetchTestVideoStatus 被调用，attractionId=" + attractionId);
        repository.getTestVideoStatusManual(attractionId).observeForever(response -> {
            testVideoStatusResult.postValue(response);
        });
    }
    public LiveData<BaseResponse<Void>> getGenerateTestVideoResult() {
        return generateTestVideoResult;
    }
    public void generateTestVideo(String attractionId, String text) {
        repository.generateTestVideoManual(attractionId, text).observeForever(response -> {
            generateTestVideoResult.postValue(response);
        });
    }
    public LiveData<BaseResponse<String>> getPreloadStatusResult() {
        return preloadStatusResult;
    }
    public void getPreloadStatus(String attractionId) {
        repository.getPreloadStatus(attractionId).observeForever(response -> {
            preloadStatusResult.postValue(response);
        });
    }
    public LiveData<BaseResponse<DigitalHuman>> getUpsertResult() { return upsertResult; }
    public LiveData<BaseResponse<String>> getUploadResult() { return uploadResult; }
    public LiveData<BaseResponse<DigitalHuman>> getQueryResult() {return queryResult;}

    public LiveData<BaseResponse<Void>> getDeleteResult() {return deleteResult;}

    // 上传视频
    public void uploadVideo(String attractionId, MultipartBody.Part filePart) {
        repository.uploadVideo(attractionId, filePart).observeForever(response -> {
            uploadResult.postValue(response);
        });
    }

    // 新增/修改数字人
    public void upsertDigitalHuman(DigitalHuman digitalHuman) {
        repository.upsertDigitalHuman(digitalHuman).observeForever(response -> {
            upsertResult.postValue(response);
        });
    }

    // 查询数字人（接口2.13）
    public void queryDigitalHuman(String attractionId) {
        repository.query(attractionId).observeForever(response -> {
            queryResult.postValue(response);
        });
    }

    // 删除数字人（接口2.14）
    public void deleteDigitalHuman(String digitalHumanId) {
        repository.delete(digitalHumanId).observeForever(response -> {
            deleteResult.postValue(response);
        });
    }
}
