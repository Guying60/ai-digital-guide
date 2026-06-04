package com.example.digitaltourguide.model.admin;

public class DigitalHuman {

    private String id;
    private String ossUrl;
    private String attractionId;

    public DigitalHuman() {
    }

    public DigitalHuman(String id, String ossUrl, String attractionId) {
        this.id = id;
        this.ossUrl = ossUrl;
        this.attractionId = attractionId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOssUrl() {
        return ossUrl;
    }

    public void setOssUrl(String ossUrl) {
        this.ossUrl = ossUrl;
    }

    public String getAttractionId() {
        return attractionId;
    }

    public void setAttractionId(String attractionId) {
        this.attractionId = attractionId;
    }
}
