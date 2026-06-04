package com.example.digitaltourguide.utils;

import android.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;

public class RawLoggingInterceptor implements Interceptor {
    private static final String TAG = "RawRequest";

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();

        Log.d(TAG, "--> " + request.method() + " " + request.url());

        // 打印所有请求头
        for (String name : request.headers().names()) {
            Log.d(TAG, name + ": " + request.headers().get(name));
        }

        // 打印请求体
        RequestBody body = request.body();
        if (body != null) {
            Buffer buffer = new Buffer();
            body.writeTo(buffer);
            String bodyStr = buffer.readString(StandardCharsets.UTF_8);
            Log.d(TAG, "Body: " + bodyStr);
        }

        Log.d(TAG, "--> END " + request.method());

        return chain.proceed(request);
    }
}