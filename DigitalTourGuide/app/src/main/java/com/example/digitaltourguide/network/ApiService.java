package com.example.digitaltourguide.network;

import com.example.digitaltourguide.model.BaseResponse;
import com.example.digitaltourguide.model.LoginRequest;
import com.example.digitaltourguide.model.RegisterRequest;
import com.example.digitaltourguide.model.user.AttractionPage;
import com.example.digitaltourguide.model.user.ChatHistoryItem;
import com.example.digitaltourguide.model.user.DeleteResponse;
import com.example.digitaltourguide.model.user.EvaluateRequest;
import com.example.digitaltourguide.model.user.GuidePreference;
import com.example.digitaltourguide.model.user.GuidePreferenceRequest;
import com.example.digitaltourguide.model.user.HistoryResponse;
import com.example.digitaltourguide.model.user.RegisterResponse;
import com.example.digitaltourguide.model.user.ReviewPage;
import com.example.digitaltourguide.model.user.SubmitReviewRequest;
import com.example.digitaltourguide.model.user.UpdateUserRequest;
import com.example.digitaltourguide.model.user.RoutePlanVO;
import com.example.digitaltourguide.model.user.UserLoginData;

import java.util.List;

import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    static ApiService getInstance(){
        return RetrofitClient.getApiService();
    }

    //1.1
    @POST("users/register")
    Call<BaseResponse<RegisterResponse>> register(
            @Body RegisterRequest request
    );
    //1.2
    @POST("users/login")
    Call<BaseResponse<UserLoginData>> login(@Body LoginRequest request);
    //1.3
    @GET("users/userInfo")
    Call<BaseResponse<UpdateUserRequest>> getUserInfo(

    );
    //1.4修改用户信息接口
    @PUT("users")
    Call<ResponseBody> updateUserInfo(
            @Header("Authorization") String auth,
            @Body UpdateUserRequest request
    );

    //1.5弹窗景点列表（根据距离游标分页）
    @GET("users/attractions")
    Call<AttractionPage> getAttractions(
            @Query("city") String city,
            @Query("userLongitude") Double userLongitude,
            @Query("userLatitude") Double userLatitude,
            @Query("keyWord") String keyWord,
            @Query("lastDistance") Double lastDistance,
            @Query("lastId") String lastId,
            @Query("pageSize") int pageSize);

    //1.6获取主页景点
    @GET("users/tourHistory")
    Call<HistoryResponse> getTourHistory(
            @Query("keyWord") String keyWord,
            @Query("type") Integer type,
            @Query("city") String city,
            @Query("lastId") String lastId,
            @Query("pageSize") int pageSize
    );
    //1.7删除卡片
    @DELETE("users/tourHistory/delete/{id}")
    Call<DeleteResponse> deleteTourHistory(
            @Path("id") String id
    );
    //1.8
    @GET("users/chat-history/{conversationId}")
    Call<BaseResponse<List<ChatHistoryItem>>> getChatHistory(
            @Path("conversationId") String conversationId
    );
    //1.9
    @DELETE("users/chat-history/{conversationId}")
    Call<DeleteResponse> deleteAiHistory(
            @Path("conversationId") String conversationId
    );
    //1.10
    @POST("users/tourHistory/evaluate")
    Call<BaseResponse> evaluateTourHistory(
            @Header("Authorization") String authorization,
            @Body EvaluateRequest request
    );
    //1.11
    @Multipart
    @POST("users/file/avatar")
    Call<BaseResponse<String>> uploadAvatar(
            @Part MultipartBody.Part file
    );

    //1.12 保存或更新导览偏好
    @PUT("users/guide-preference")
    Call<BaseResponse<Void>> saveGuidePreference(
            @Body GuidePreferenceRequest request
    );

    //1.13 查询导览偏好
    @GET("users/guide-preference")
    Call<BaseResponse<GuidePreference>> getGuidePreference();

    //==================================================================
    // 1.13 获取我的评价列表（游标分页）
    //==================================================================
    @GET("users/reviews")
    Call<BaseResponse<ReviewPage>> getReviews(
            @Query("lastId") String lastId,
            @Query("pageSize") Integer pageSize,
            @Query("status") Integer status
    );

    //==================================================================
    // 1.14 提交评价
    //==================================================================
    @POST("users/reviews/submit")
    Call<BaseResponse<Void>> submitReview(
            @Body SubmitReviewRequest request
    );

    //==================================================================
    // 1.15 删除评价
    //==================================================================
    @DELETE("users/reviews/{reviewId}")
    Call<BaseResponse<Void>> deleteReview(
            @Path("reviewId") String reviewId
    );
    //==================================================================
    // 1.16.1 恢复当前激活路线（REST）
    //==================================================================
    @GET("users/route/current")
    Call<BaseResponse<RoutePlanVO>> getCurrentRoute(
            @Query("attractionId") String attractionId
    );
}

