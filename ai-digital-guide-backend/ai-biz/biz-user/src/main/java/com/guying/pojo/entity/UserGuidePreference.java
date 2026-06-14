package com.guying.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.guying.common.enums.guide.GuideDepth;
import com.guying.common.enums.guide.GuideStyle;
import com.guying.common.enums.guide.Interest;
import com.guying.common.enums.guide.TravelPurpose;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户导览偏好
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("tb_user_guide_preference")
public class UserGuidePreference {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联用户ID
     */
    private Long userId;

    /**
     * 讲解风格code
     */
    private Integer guideStyle;

    /**
     * 讲解深度code
     */
    private Integer guideDepth;

    /**
     * 兴趣偏好，多选以英文逗号拼接枚举name存储
     * 示例："HISTORY_CULTURE,ARCHITECTURE_ART,ROYAL_STORIES"
     */
    private String interests;

    /**
     * 出游目的code
     */
    private Integer travelPurpose;

    /**
     * 特殊要求，最长100字，允许为空
     */
    private String specialRequirements;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 将偏好转换为系统提示词占位符对应的Map
     */
    public Map<String, String> toPromptMap() {
        Map<String, String> map = new HashMap<>();

        GuideStyle style = guideStyle != null ? GuideStyle.fromCode(guideStyle) : null;
        map.put("guideStyle", style != null ? style.getDesc() : "未设置");

        GuideDepth depth = guideDepth != null ? GuideDepth.fromCode(guideDepth) : null;
        map.put("guideDepth", depth != null ? depth.getDesc() : "未设置");

        if (interests != null && !interests.isEmpty()) {
            String interestLabels = Arrays.stream(interests.split(","))
                    .map(String::trim)
                    .map(name -> {
                        try {
                            return Interest.valueOf(name).getDesc();
                        } catch (IllegalArgumentException e) {
                            return null;
                        }
                    })
                    .filter(label -> label != null)
                    .collect(Collectors.joining("、"));
            map.put("interests", interestLabels.isEmpty() ? "未设置" : interestLabels);
        } else {
            map.put("interests", "未设置");
        }

        TravelPurpose purpose = travelPurpose != null ? TravelPurpose.fromCode(travelPurpose) : null;
        map.put("travelPurpose", purpose != null ? purpose.getDesc() : "未设置");

        map.put("specialRequirements", specialRequirements != null && !specialRequirements.isBlank()
                ? specialRequirements : "无");

        return map;
    }
}
