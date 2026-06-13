package com.guying.common.enums.guide;

import com.guying.common.enums.IEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 讲解风格（单选）
 */
@Getter
@AllArgsConstructor
public enum GuideStyle implements IEnum<Integer> {

    PROFESSIONAL(1, "专业讲解"),
    STORYTELLING(2, "故事化讲解"),
    RELAXED_HUMOR(3, "轻松幽默"),
    CHILDREN_MODE(4, "儿童模式");

    private final int code;
    private final String desc;

    public static GuideStyle fromCode(int code) {
        for (GuideStyle e : values()) {
            if (e.code == code) return e;
        }
        return null;
    }

    public static GuideStyle fromDesc(String desc) {
        for (GuideStyle e : values()) {
            if (e.desc.equals(desc)) return e;
        }
        return null;
    }
}
