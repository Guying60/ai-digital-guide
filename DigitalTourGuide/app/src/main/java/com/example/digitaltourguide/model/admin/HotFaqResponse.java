package com.example.digitaltourguide.model.admin;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class HotFaqResponse {
    @SerializedName("code")
    private int code;

    @SerializedName("msg")
    private String msg;

    @SerializedName("data")
    private List<HotFaqItem> data;

    public int getCode() { return code; }
    public String getMsg() { return msg; }
    public List<HotFaqItem> getData() { return data; }
}
