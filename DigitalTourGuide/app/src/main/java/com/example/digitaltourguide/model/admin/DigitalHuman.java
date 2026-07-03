package com.example.digitaltourguide.model.admin;

public class DigitalHuman {

    private String id;
    private String videoUrl;
    private String audioUrl;
    private String attractionId;

    public DigitalHuman() {
    }

    public DigitalHuman(String id, String videoUrl, String audioUrl, String attractionId) {
        this.id = id;
        this.videoUrl = videoUrl;
        this.audioUrl = audioUrl;
        this.attractionId = attractionId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public String getAttractionId() {
        return attractionId;
    }

    public void setAttractionId(String attractionId) {
        this.attractionId = attractionId;
    }
}
