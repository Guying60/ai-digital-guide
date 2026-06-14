package com.guying.common.enums.guide;

import com.guying.common.enums.IEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 出游目的（单选）
 */
@Getter
@AllArgsConstructor
public enum TravelPurpose implements IEnum<Integer> {

    LEARNING(1, "学习知识"),
    FAMILY_OUTING(2, "亲子陪伴"),
    PHOTOGRAPHY_CHECKIN(3, "拍照打卡"),
    LEISURE(4, "休闲放松");

    private final int code;
    private final String desc;

    public static TravelPurpose fromCode(int code) {
        for (TravelPurpose e : values()) {
            if (e.code == code) return e;
        }
        return null;
    }

    public static TravelPurpose fromDesc(String desc) {
        for (TravelPurpose e : values()) {
            if (e.desc.equals(desc)) return e;
        }
        return null;
    }
}
