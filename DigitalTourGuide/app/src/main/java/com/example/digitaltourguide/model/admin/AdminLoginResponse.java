package com.example.digitaltourguide.model.admin;

public class AdminLoginResponse {
    private int code;
    private String msg;
    private AdminLoginData data;

    public static class AdminLoginData {
        private String id;       // 管理员ID
        private String token;         // 令牌

        public String getId() { return id; }
        public void setId(String adminId) { this.id = adminId; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }
    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }
    public AdminLoginData getData() { return data; }
    public void setData(AdminLoginData data) { this.data = data; }
}
