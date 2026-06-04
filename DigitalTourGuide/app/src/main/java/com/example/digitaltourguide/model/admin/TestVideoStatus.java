package com.example.digitaltourguide.model.admin;

public class TestVideoStatus {
    private String status;   // "PROCESSING", "SUCCESS", "FAILED"
    private String videoUrl; // 成功时返回的播放地址

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }
}
