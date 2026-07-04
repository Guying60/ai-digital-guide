package com.example.digitaltourguide.network;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.digitaltourguide.view.user.UserLoginActivity;
import com.example.digitaltourguide.utils.SpUtils;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
    private static final String BASE_URL = "https://ai.guying.xyz/ai-project/v1/";
    private static Retrofit retrofit;
    private static OkHttpClient okHttpClient;
    // 自定义 Application 类中需要保存全局 Context
    private static Context appContext;

    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    private static OkHttpClient getOkHttpClient() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        return new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .addInterceptor(new AuthInterceptor())
                .addInterceptor(logging)
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request request = chain.request();
                        // 只对景点接口打日志
                        if (request.url().toString().contains("/attractions")) {
                            Log.d("API_LOG", "--> " + request.method() + " " + request.url());
                        }
                        Response response = chain.proceed(request);
                        if (request.url().toString().contains("/attractions")) {
                            String body = response.peekBody(Long.MAX_VALUE).string();
                            Log.d("API_LOG", "<-- " + response.code() + " " + body);
                        }
                        return response;
                    }
                })
               /* .addInterceptor(new Interceptor() {                        // 2. 清理多余 Header
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request original = chain.request();
                        Request.Builder builder = original.newBuilder();

                        if (original.header("Authorization") != null) {
                            // 已经有 Authorization 头，直接放行
                            return chain.proceed(original);
                        }

                        // 移除所有 Header，只保留 Authorization 和 Content-Type
                        builder.removeHeader("User-Agent");
                        builder.removeHeader("Accept");
                        builder.removeHeader("Accept-Encoding");
                        builder.removeHeader("Connection");
                        // 如果还有其他 Header，也一并移除

                        // 确保 Content-Type 正确
                        builder.header("Content-Type", "application/json; charset=UTF-8");

                        Request cleaned = builder.build();
                        return chain.proceed(cleaned);
                    }
                })*/
                .build();
    }
    //认证拦截器
    private static class AuthInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request original = chain.request();

            // 强制删除可能存在的空 Authorization 头
            Request.Builder builder = original.newBuilder().removeHeader("Authorization");

            String url = original.url().toString();
            String token = null;

            if (url.contains("/admins/")) {
                token = SpUtils.getAdminToken(appContext);
                Log.d("RetrofitClient", "管理员 Token = " + (token == null ? "null" : token.substring(0, Math.min(10, token.length())) + "..."));
            } else {
                // 其他所有接口（/users/...、/tourHistory... 等）都用用户 token
                token = SpUtils.getUserToken(appContext);
            }

            // 关键修改：打印完整 token 以便对比
            Log.d("AuthInterceptor", "原始 token: '" + token + "'");

            if (token != null && !token.isEmpty()) {
                if (!token.startsWith("Bearer ")) {
                    token = "Bearer " + token;
                }
                builder.header("Authorization", token);
                Log.d("AuthInterceptor", "最终 Authorization: " + token);
            } else {
                Log.w("AuthInterceptor", "Token 为空！URL: " + url);
            }

            Request newRequest = builder.build();
            Log.d("RetrofitClient", "最终请求头 Authorization: " + newRequest.header("Authorization"));

            return chain.proceed(newRequest);
        }
    }
    public static Retrofit getInstance() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(getOkHttpClient())
                    .addConverterFactory(GsonConverterFactory.create()) // 解析JSON
                    .build();
        }
        return retrofit;
    }


    // 获取管理员API服务
    public static AdminApiService getAdminApiService() {
        return getInstance().create(AdminApiService.class);
    }


    //快速获取api实例
    public static ApiService getApiService(){
        return getInstance().create(ApiService.class);
    }
    //Token 过期拦截器：当服务器返回 401 时，清除本地 Token 并跳转登录页
    private static class TokenExpiredInterceptor implements Interceptor{

        @NonNull
        @Override
        public Response intercept(@NonNull Chain chain) throws IOException {
            Request request=chain.request();
            Response response=chain.proceed(request);
            if(response.code()==401){
                //切换到主线程执行跳转
                new Handler(Looper.getMainLooper()).post(()->{
                    if(appContext!=null){
                        // 清除本地存储的 Token
                        SpUtils.clearAdminInfo(appContext);
                        SpUtils.saveUserToken(appContext, "");
                        SpUtils.saveUserId(appContext, "");
                        // 跳转到登录页
                        Intent intent = new Intent(appContext, UserLoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        appContext.startActivity(intent);
                    }
                });
            }
            return response;
        }
    }
}
