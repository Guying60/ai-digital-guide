package com.example.digitaltourguide.model.user;

/**
 * 导览偏好 — 响应模型（1.13 查询接口返回）
 */
public class GuidePreference {
    private String id;
    private Integer guideStyle;
    private String guideStyleName;
    private Integer guideDepth;
    private String guideDepthName;
    private String interests;
    private Integer travelPurpose;
    private String travelPurposeName;
    private String specialRequirements;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Integer getGuideStyle() { return guideStyle; }
    public void setGuideStyle(Integer guideStyle) { this.guideStyle = guideStyle; }
    public String getGuideStyleName() { return guideStyleName; }
    public void setGuideStyleName(String guideStyleName) { this.guideStyleName = guideStyleName; }
    public Integer getGuideDepth() { return guideDepth; }
    public void setGuideDepth(Integer guideDepth) { this.guideDepth = guideDepth; }
    public String getGuideDepthName() { return guideDepthName; }
    public void setGuideDepthName(String guideDepthName) { this.guideDepthName = guideDepthName; }
    public String getInterests() { return interests; }
    public void setInterests(String interests) { this.interests = interests; }
    public Integer getTravelPurpose() { return travelPurpose; }
    public void setTravelPurpose(Integer travelPurpose) { this.travelPurpose = travelPurpose; }
    public String getTravelPurposeName() { return travelPurposeName; }
    public void setTravelPurposeName(String travelPurposeName) { this.travelPurposeName = travelPurposeName; }
    public String getSpecialRequirements() { return specialRequirements; }
    public void setSpecialRequirements(String specialRequirements) { this.specialRequirements = specialRequirements; }
}