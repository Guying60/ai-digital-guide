package com.guying.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum EmotionEnum {

    POSITIVE(0, "正面"),
    NEUTRAL(1, "中性"),
    NEGATIVE(2, "负面");

    private final int code;
    private final String desc;

    // 供 AI 返回文字时转换用
    public static EmotionEnum fromDesc(String desc) {
        for (EmotionEnum e : values()) {
            if (e.desc.equals(desc)) return e;
        }
        return NEUTRAL; // 兜底
    }

    public static EmotionEnum fromCode(int code) {
        for (EmotionEnum e : values()) {
            if (e.code == code) return e;
        }
        return NEUTRAL;
    }
}
