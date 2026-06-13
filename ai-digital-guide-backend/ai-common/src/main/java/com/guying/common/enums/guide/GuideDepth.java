package com.guying.common.enums.guide;

import com.guying.common.enums.IEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 讲解深度（单选）
 */
@Getter
@AllArgsConstructor
public enum GuideDepth implements IEnum<Integer> {

    QUICK_BROWSE(1, "简洁速览"),
    STANDARD(2, "标准导览"),
    DEEP_CULTURE(3, "深度文化解析");

    private final int code;
    private final String desc;

    public static GuideDepth fromCode(int code) {
        for (GuideDepth e : values()) {
            if (e.code == code) return e;
        }
        return null;
    }

    public static GuideDepth fromDesc(String desc) {
        for (GuideDepth e : values()) {
            if (e.desc.equals(desc)) return e;
        }
        return null;
    }
}
