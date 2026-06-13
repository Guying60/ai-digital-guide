package com.guying.pojo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户导览偏好DTO
 */
@Data
public class UserGuidePreferenceDTO {

    /**
     * 讲解风格code：1-专业讲解 2-故事化讲解 3-轻松幽默 4-儿童模式
     */
    @Min(1)
    @Max(4)
    private Integer guideStyle;

    /**
     * 讲解深度code：1-简洁速览 2-标准导览 3-深度文化解析
     */
    @Min(1)
    @Max(3)
    private Integer guideDepth;

    /**
     * 兴趣偏好，多选以英文逗号拼接枚举name
     * 示例："HISTORY_CULTURE,ARCHITECTURE_ART"
     */
    private String interests;

    /**
     * 出游目的code：1-学习知识 2-亲子陪伴 3-拍照打卡 4-休闲放松
     */
    @Min(1)
    @Max(4)
    private Integer travelPurpose;

    /**
     * 特殊要求，最长100字
     */
    @Size(max = 100, message = "特殊要求不能超过100字")
    private String specialRequirements;
}
