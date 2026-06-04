package com.example.digitaltourguide.model.admin;

import com.google.gson.annotations.SerializedName;

public class HotFaqItem {
    @SerializedName("question")
    private String question;

    @SerializedName("count")
    private int count;

    public String getQuestion() { return question; }
    public int getCount() { return count; }
}
