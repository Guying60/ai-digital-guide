package com.example.digitaltourguide.model;

public class BaseResponse<T> {
    private int code;
    private String msg;
    private T data;

    // getter & setter
    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }
    public T getData() {return data;}

    public void setData(T data) {this.data = data;}
}
