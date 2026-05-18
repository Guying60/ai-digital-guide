package com.guying.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum FocusEnum {

    FOOD(0, "餐饮"),
    TICKET(1, "票务"),
    GUIDE(2, "导览"),
    TRAFFIC(3, "交通"),
    PARKING(4, "停车"),
    LODGING(5, "住宿"),
    SCENIC(6, "景点体验"),
    COMPLAINT(7, "投诉建议"),
    OTHER(8, "其他");

    private final int code;
    private final String desc;

    public static FocusEnum fromDesc(String desc) {
        for (FocusEnum e : values()) {
            if (e.desc.equals(desc)) return e;
        }
        return OTHER;
    }

    public static FocusEnum fromCode(int code) {
        for (FocusEnum e : values()) {
            if (e.code == code) return e;
        }
        return OTHER;
    }
}