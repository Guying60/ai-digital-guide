package com.guying.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum GenderEnum {

    FEMALE(0, "女"),
    MALE(1, "男"),
    UNKNOWN(2, "未知");

    private final int code;
    private final String desc;

    public static GenderEnum fromDesc(String desc) {
        for (GenderEnum e : values()) {
            if (e.desc.equals(desc)) return e;
        }
        return UNKNOWN;
    }

    public static GenderEnum fromCode(int code) {
        for (GenderEnum e : values()) {
            if (e.code == code) return e;
        }
        return UNKNOWN;
    }
}
