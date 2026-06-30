package com.example.digitaltourguide.model.user;

/**
 * 1.5 获取附近景点列表 响应体（非泛型，避免 Gson 反序列化问题）
 */
public class AttractionListResponse {
    private int code;
    private String msg;
    private AttractionPage data;

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }

    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }

    public AttractionPage getData() { return data; }
    public void setData(AttractionPage data) { this.data = data; }
}
