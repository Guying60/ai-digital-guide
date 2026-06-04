package com.example.digitaltourguide.model.user;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class HistoryResponse {
    @SerializedName("code")
    public int code;
    @SerializedName("msg")
    public String msg;
    @SerializedName("data")
    public HistoryData data;

    public static class HistoryData {
        @SerializedName("list")
        public List<ScenicSpot> list;
        @SerializedName("nextLastId")
        public String nextLastId;
        @SerializedName("hasMore")
        public boolean hasMore;
    }
}
