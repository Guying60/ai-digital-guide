package com.guying.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 面部表情情感分类枚举。
 * 供视觉大模型对面部帧做情感分类后，将文字描述转换为 code 落库使用。
 */
@Getter
@AllArgsConstructor
public enum ExpressionEnum {

    JOY(0, "喜悦"),
    SURPRISE(1, "惊讶"),
    NEUTRAL(2, "中性"),
    CONFUSION(3, "困惑"),
    DISGUST(4, "厌恶"),
    ANGER(5, "愤怒"),
    SADNESS(6, "悲伤");

    private final int code;
    private final String desc;

    /** 供 AI 返回文字时转换用，未匹配兜底为中性 */
    public static ExpressionEnum fromDesc(String desc) {
        if (desc == null) return NEUTRAL;
        for (ExpressionEnum e : values()) {
            if (e.desc.equals(desc)) return e;
        }
        return NEUTRAL;
    }

    public static ExpressionEnum fromCode(int code) {
        for (ExpressionEnum e : values()) {
            if (e.code == code) return e;
        }
        return NEUTRAL;
    }
}
