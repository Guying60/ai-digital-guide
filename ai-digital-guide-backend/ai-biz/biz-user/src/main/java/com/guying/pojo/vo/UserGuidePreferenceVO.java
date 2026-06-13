package com.guying.pojo.vo;

import lombok.Data;

/**
 * 用户导览偏好VO
 */
@Data
public class UserGuidePreferenceVO {

    private Long id;
    private Integer guideStyle;
    private String guideStyleName;
    private Integer guideDepth;
    private String guideDepthName;
    private String interests;
    private Integer travelPurpose;
    private String travelPurposeName;
    private String specialRequirements;
}
