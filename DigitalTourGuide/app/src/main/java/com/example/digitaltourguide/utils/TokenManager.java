package com.example.digitaltourguide.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class TokenManager {
    private static final String PREFS_NAME = "auth_prefs";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_TYPE = "user_type"; // "admin" 或 "user"
    private static final String KEY_EXPIRE_TIME = "expire_time"; // 时间戳（毫秒）
    private static volatile TokenManager instance;
    private SharedPreferences sharedPreferences;

    private TokenManager(Context context){
        try {
            MasterKey masterKey=new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            sharedPreferences= EncryptedSharedPreferences.create(
                    context,PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            // 降级为普通 SharedPreferences（生产环境不建议，仅示例）
            sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    public static TokenManager getInstance(Context context){
        if(instance==null){
            synchronized (TokenManager.class){
                if(instance==null){
                    instance=new TokenManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    //保存登录信息
    public void saveLoginInfo(String accessToken,String refreshToken,String userType,long expiresIn){
        long expireTime=System.currentTimeMillis()+expiresIn*1000;
        sharedPreferences.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putString(KEY_USER_TYPE, userType)
                .putLong(KEY_EXPIRE_TIME, expireTime)
                .apply();
    }
    // 获取 Access Token
    public String getAccessToken() {
        return sharedPreferences.getString(KEY_ACCESS_TOKEN, null);
    }
    // 获取 Refresh Token
    public String getRefreshToken() {
        return sharedPreferences.getString(KEY_REFRESH_TOKEN, null);
    }
    // 获取用户类型
    public String getUserType() {
        return sharedPreferences.getString(KEY_USER_TYPE, null);
    }
    //检查token是否有效
    public boolean isTokenValid(){
        String token=getAccessToken();
        if(token==null || token.isEmpty()) return false;
        long expireTime=sharedPreferences.getLong(KEY_EXPIRE_TIME,-1);
        return expireTime!=-1 && System.currentTimeMillis()<expireTime;
    }
    //清除登录信息，退出登陆时调用
    public void clear(){
        sharedPreferences.edit().clear().apply();
    }
}
