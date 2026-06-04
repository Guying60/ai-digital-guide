package com.example.digitaltourguide.model;

import android.app.Application;

import com.example.digitaltourguide.network.RetrofitClient;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        RetrofitClient.init(this);
    }
}
