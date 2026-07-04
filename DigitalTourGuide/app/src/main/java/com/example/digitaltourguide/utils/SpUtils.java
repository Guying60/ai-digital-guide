package com.example.digitaltourguide.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class SpUtils {

    // 通用的 SP 名称（用户和管理员通用）
    private static final String SP_NAME = "app_config";
    private static SharedPreferences sp;
    private static final String PREF_NAME = "rated_conversations";
    private static final String KEY_RATED_SET = "rated_set";

    private static final String KEY_ADMIN_TOKEN_EXPIRE = "admin_token_expire";
    private static final String KEY_USER_TOKEN_EXPIRE = "user_token_expire";

    // 初始化（在Application或Activity中调用）
    public static void init(Context context) {
        if (sp == null) {
            sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        }
    }

    public static Set<String> getRatedConversationIds(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getStringSet(KEY_RATED_SET, new HashSet<>());
    }
    public static void addRatedConversation(Context context, String conversationId) {
        Set<String> set = getRatedConversationIds(context);
        Set<String> newSet = new HashSet<>(set);
        newSet.add(conversationId);
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_RATED_SET, newSet)
                .apply();
    }

    public static boolean isRated(Context context, String conversationId) {
        return getRatedConversationIds(context).contains(conversationId);
    }

    //最后登录的是哪个账号，下次就自动进入哪个账号
    public static void saveLastLoginType(Context context,String type){
        init(context);
        sp.edit().putString("last_login_type",type).apply();
    }
    public static String getLastLoginType(Context context) {
        init(context);
        return sp.getString("last_login_type", "");
    }

    // 保存管理员 Token 时同时保存过期时间（expiresIn 单位：秒）
    public static void saveAdminTokenWithExpire(Context context, String token, long expiresIn) {
        init(context);
        long expireTime = System.currentTimeMillis() + expiresIn * 1000;
        sp.edit()
                .putString("admin_token", token)
                .putLong(KEY_ADMIN_TOKEN_EXPIRE, expireTime)
                .apply();
    }

    // 保存用户 Token 时同时保存过期时间
    public static void saveUserTokenWithExpire(Context context, String token, long expiresIn) {
        init(context);
        long expireTime = System.currentTimeMillis() + expiresIn * 1000;
        sp.edit()
                .putString("token", token)
                .putLong(KEY_USER_TOKEN_EXPIRE, expireTime)
                .apply();
    }

    // 检查管理员 Token 是否有效（未过期）
    public static boolean isAdminTokenValid(Context context) {
        init(context);
        String token = sp.getString("admin_token", "");
        if (token.isEmpty()) return false;
        long expire = sp.getLong(KEY_ADMIN_TOKEN_EXPIRE, -1);
        return expire != -1 && System.currentTimeMillis() < expire;
    }

    // 检查用户 Token 是否有效
    public static boolean isUserTokenValid(Context context) {
        init(context);
        String token = sp.getString("token", "");
        if (token.isEmpty()) return false;
        long expire = sp.getLong(KEY_USER_TOKEN_EXPIRE, -1);
        return expire != -1 && System.currentTimeMillis() < expire;
    }

    public static void saveAdminId(Context context, String adminId) {
        init(context);
        sp.edit().putString("admin_id", adminId).apply();
    }

    // 新增：获取管理员ID
    public static String getAdminId(Context context) {
        init(context);
        return sp.getString("admin_id", "");
    }

    // 保存管理员账号（用户名）
    public static void saveAdminUsername(Context context, String username) {
        init(context);
        sp.edit().putString("admin_username", username).apply();
    }

    // 获取管理员账号（用户名）
    public static String getAdminUsername(Context context) {
        init(context);
        return sp.getString("admin_username", "");
    }
    // 存储管理员token
    public static void saveAdminToken(Context context, String token) {
        init(context);
        sp.edit().putString("admin_token", token).apply();
    }

    // 存储景点ID
    public static void saveAttractionId(Context context, String attractionId) {
        init(context);
        sp.edit().putString("attraction_id", attractionId).apply();
    }

    // 获取token
    public static String getAdminToken(Context context) {
        init(context);
        return sp.getString("admin_token", "");
    }

    // 获取景点ID
    public static String getAttractionId(Context context) {
        init(context);
        return sp.getString("attraction_id", "");
    }

    // 清空管理员登录信息（退出登录用）
    public static void clearAdminInfo(Context context) {
        init(context);
        sp.edit()
                .remove("admin_token")
                .remove(KEY_ADMIN_TOKEN_EXPIRE)
                .remove("admin_id")
                .remove("admin_username")
                .remove("last_login_type")
                .apply();
    }

    // 清空用户登录信息（退出登录用）
    public static void clearUserInfo(Context context) {
        init(context);
        sp.edit()
                .remove("token")
                .remove(KEY_USER_TOKEN_EXPIRE)
                .remove("user_id")
                .remove("last_login_type")
                .apply();
    }

    // ----------------------
    // Token 相关（通用）
    // ----------------------
    public static void saveUserToken(Context context, String token) {
        if (sp == null) {
            init(context);
        }
        sp.edit().putString("token", token).apply();
    }

    public static String getUserToken(Context context) {
        init(context);
        return sp.getString("token", "");
    }

    // ----------------------
    // 用户 ID（通用）
    // ----------------------
    public static void saveUserId(Context context, String userId) {
        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        sp.edit().putString("user_id", userId).apply();
    }

    public static String getUserId(Context context) {
        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        return sp.getString("user_id", "");
    }



    // ----------------------
    // 清除 Token（退出登录用）
    // ----------------------

    // ----------------------
    // 用户头像 URL（本地缓存，服务端 PUT 兜底）
    // ----------------------
    public static void saveUserAvatar(Context context, String avatarUrl) {
        init(context);
        sp.edit().putString("user_avatar", avatarUrl).apply();
    }

    public static String getUserAvatar(Context context) {
        init(context);
        return sp.getString("user_avatar", "");
    }

    // ----------------------
    // 表情采集隐私确认标志
    // ----------------------
    public static boolean isEmotionPrivacyAcknowledged(Context context) {
        init(context);
        return sp.getBoolean("emotion_privacy_acknowledged", false);
    }

    public static void setEmotionPrivacyAcknowledged(Context context) {
        init(context);
        sp.edit().putBoolean("emotion_privacy_acknowledged", true).apply();
    }

    public static String getWebSocketUrl(String userId) {
        // 这里直接写正确的基础地址
        String baseUrl = "wss://ai.guying.xyz/chat/";
        String attractionId = "1001";

        // 直接拼接，确保没有多余空格或符号
        return baseUrl + userId + "?attractionId=" + attractionId;
    }
}