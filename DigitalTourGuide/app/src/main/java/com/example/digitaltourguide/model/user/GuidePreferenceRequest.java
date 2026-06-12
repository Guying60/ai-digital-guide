package com.example.digitaltourguide.model.user;

/**
 * 导览偏好 — 请求模型（1.12 保存/更新接口请求体）
 */
public class GuidePreferenceRequest {
    private Integer guideStyle;
    private Integer guideDepth;
    private String interests;
    private Integer travelPurpose;
    private String specialRequirements;

    public GuidePreferenceRequest() {}

    public GuidePreferenceRequest(Integer guideStyle, Integer guideDepth,
                                  String interests, Integer travelPurpose,
                                  String specialRequirements) {
        this.guideStyle = guideStyle;
        this.guideDepth = guideDepth;
        this.interests = interests;
        this.travelPurpose = travelPurpose;
        this.specialRequirements = specialRequirements;
    }

    public Integer getGuideStyle() { return guideStyle; }
    public void setGuideStyle(Integer guideStyle) { this.guideStyle = guideStyle; }
    public Integer getGuideDepth() { return guideDepth; }
    public void setGuideDepth(Integer guideDepth) { this.guideDepth = guideDepth; }
    public String getInterests() { return interests; }
    public void setInterests(String interests) { this.interests = interests; }
    public Integer getTravelPurpose() { return travelPurpose; }
    public void setTravelPurpose(Integer travelPurpose) { this.travelPurpose = travelPurpose; }
    public String getSpecialRequirements() { return specialRequirements; }
    public void setSpecialRequirements(String specialRequirements) { this.specialRequirements = specialRequirements; }
}