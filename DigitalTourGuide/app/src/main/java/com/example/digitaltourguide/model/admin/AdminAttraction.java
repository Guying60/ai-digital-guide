package com.example.digitaltourguide.model.admin;

public class AdminAttraction {
    //回显用
    private String id;
    private String coverUrl;
    private String attractionName;
    private Integer type;
    private String attractionContent;

    // Getter & Setter
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAttractionContent() {
        return attractionContent;
    }

    public void setAttractionContent(String attractionContent) {
        this.attractionContent = attractionContent;
    }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public String getAttractionName() { return attractionName; }
    public void setAttractionName(String attractionName) { this.attractionName = attractionName; }
    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }
}
