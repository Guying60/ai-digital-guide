package com.example.digitaltourguide.network;

import com.example.digitaltourguide.model.admin.AttractionListData;
import com.example.digitaltourguide.model.LoginRequest;
import com.example.digitaltourguide.model.admin.AddAttractionRequest;
import com.example.digitaltourguide.model.admin.AdminAttraction;
import com.example.digitaltourguide.model.BaseResponse;
import com.example.digitaltourguide.model.RegisterRequest;
import com.example.digitaltourguide.model.admin.BatchDeleteRequest;
import com.example.digitaltourguide.model.admin.ChatTrendData;
import com.example.digitaltourguide.model.admin.DigitalHuman;
import com.example.digitaltourguide.model.admin.EmotionTrendData;
import com.example.digitaltourguide.model.admin.FileItem;
import com.example.digitaltourguide.model.admin.FileUploadResponse;
import com.example.digitaltourguide.model.admin.FocusCardData;
import com.example.digitaltourguide.model.admin.HotFaqResponse;
import com.example.digitaltourguide.model.admin.SatisfactionTrendVO;
import com.example.digitaltourguide.model.admin.SuggestionData;
import com.example.digitaltourguide.model.admin.TestVideoStatus;
import com.example.digitaltourguide.model.user.RegisterResponse;
import com.example.digitaltourguide.model.admin.AdminLoginResponse.AdminLoginData;
import com.google.gson.JsonElement;

import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.HTTP;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface AdminApiService {

    static AdminApiService getInstance(){
        return RetrofitClient.getAdminApiService();
    }

    //2.1
    @POST("admins/register")
    Call<BaseResponse<RegisterResponse>> register(
            @Body RegisterRequest request
    );
    //2.2
    @POST("admins/login")
    Call<BaseResponse<AdminLoginData>> login(
            @Body LoginRequest request
    );
    //2.3添加景点
    @POST("admins/attractions")
    Call<BaseResponse<AdminAttraction>> addAttraction(
            @Header("Authorization") String token,
            @Body AddAttractionRequest request
    );
    //2.4更新景点
    @PUT("admins/attractions")
    Call<BaseResponse<AdminAttraction>> updateAttraction(
            @Header("Authorization") String token,
            @Body AddAttractionRequest request
    );
    //2.5删除景点
    @DELETE("admins/attractions/{attractionId}")
    Call<BaseResponse<Void>> deleteAttraction(
            @Header("Authorization") String token,
            @Path("attractionId") String attractionId
    );

    //2.16上传封面
    @Multipart
    @POST("admins/file/cover")
    Call<BaseResponse<String>> uploadCover(
            @Header("Authorization") String token,
            @Part MultipartBody.Part file
    );

    //2.6 批量删除
    @HTTP(method = "DELETE",path="admins/attractions/batch", hasBody = true)
    Call<BaseResponse<Void>> batchDeleteAttractions(
            @Body BatchDeleteRequest request
    );

    //2.7
    @GET("admins/attractions")
    Call<BaseResponse<AttractionListData>> getAttractionList(
            @Header("Authorization") String token,
            @Query("keyWord") String keyWord,
            @Query("type") Integer type,
            @Query("city") String city,
            @Query("lastId") String lastId,
            @Query("pageSize") int pageSize
    );

    //2.8
    @GET("admins/attractions/{attractionId}")
    Call<BaseResponse<AdminAttraction>> getAttractionDetail(
            @Header("Authorization") String token,
            @Path("attractionId") String attractionId
    );
    //2.9
    @Multipart
    @POST("admins/file/doc")
    Call<BaseResponse<FileUploadResponse>> uploadFileToOSS(
            @Header("Authorization") String token,
            @Query("attractionId") String attractionId,
            @Part MultipartBody.Part file,
            @Part("type") RequestBody type
    );
    //2.10轮询
    @GET("admins/attractions/documents/{taskId}")
    Call<BaseResponse<String>> checkDocumentStatus(
            @Header("Authorization") String token,
            @Path("taskId") String taskId
    );
    //2.11文件回显
    @GET("admins/attractions/documents/{attractionId}")
    Call<BaseResponse<List<FileItem>>> getFileList(
            @Header("Authorization") String token,
            @Path("attractionId") String attractionId
    );
    //2.12删除文件
    @DELETE("admins/attractions/documents/{fileId}")
    Call<BaseResponse<Void>> deleteDocument(
            @Header("Authorization") String token,
            @Path("fileId") String fileId
    );
    // 2.13 新增/修改
    @POST("admins/attractions/digital-human")
    Call<BaseResponse<DigitalHuman>>  upsertDigitalHuman(
            @Body DigitalHuman digitalHuman
    );
    // 2.14 查询
    @GET("admins/attractions/digital-human/{attractionId}")
    Call<BaseResponse<DigitalHuman>> queryDigitalHuman(
            @Path("attractionId") String attractionId
    );
    // 2.16 删除
    @DELETE("admins/attractions/digital-human/{id}")
    Call<BaseResponse<Void>> deleteDigitalHuman(
            @Path("id") String id
    );

    // 2.15 预加载状态轮询
    @GET("admins/attractions/digital-human/preload-status/{attractionId}")
    Call<BaseResponse<String>> getPreloadStatus(@Path("attractionId") String attractionId);

    //2.18
    //上传视频
    @Multipart
    @POST("admins/file/video")
    Call<BaseResponse<String>> uploadDigitalHumanVideo(
            @Part MultipartBody.Part file,
            @Query("attractionId") String attractionId
    );

    // 2.19 生成测试视频
    @POST("admins/attractions/digital-human/test-video/{attractionId}")
    Call<ResponseBody> generateTestVideoRaw(@Path("attractionId") String attractionId, @Body Map<String, String> body);

    // 2.20 测试视频状态轮询
    @GET("admins/attractions/digital-human/test-video-status/{attractionId}")
    Call<ResponseBody> getTestVideoStatus(
            @Path("attractionId") String attractionId
    );

    //3.1柱状图
    @GET("admins/stat/faq/{attractionId}")
    Call<HotFaqResponse> getHotFaq(
            @Path("attractionId") String attractionId,
            @Query("days") int days
    );
    //3.2获取聊天服务统计
    @GET("admins/stat/chat-trend/{attractionId}")
    Call<BaseResponse<ChatTrendData>> getChatTrend(
            @Header("Authorization") String token,
            @Path("attractionId") String attractionId,
            @Query("days") int days
    );
    //3.3旅客满意度
    @GET("admins/stat/satisfaction-trend/{attractionId}")
    Call<BaseResponse<SatisfactionTrendVO>> getSatisfactionTrend(
            @Header("Authorization") String token,
            @Path("attractionId") String attractionId,
            @Query("days") int days
    );
    //4.1图表
    @GET("admins/analysis/emtion-trend/{attractionId}")
    Call<BaseResponse<EmotionTrendData>> getEmotionTrend(
            @Path("attractionId") String attractionId,
            @Query("days") int days
    );
    //4.2第一栏卡片
    @GET("admins/analysis/emotion-focus-card/{attractionId}")
    Call<BaseResponse<FocusCardData>> getEmotionFocusCard(
            @Path("attractionId") String attractionId,
            @Query("days") int days
    );
    //4.3 ai建议
    @GET("admins/analysis/ai-service-suggestion/{attractionId}")
    Call<BaseResponse<SuggestionData>> getAiServiceSuggestion(
            @Path("attractionId") String attractionId,
            @Query("type") int type
    );

}
