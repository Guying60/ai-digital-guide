package com.guying.common.enums.guide;

import com.guying.common.enums.IEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 兴趣偏好（多选）
 */
@Getter
@AllArgsConstructor
public enum Interest implements IEnum<Integer> {

    HISTORY_CULTURE(1, "历史文化"),
    ARCHITECTURE_ART(2, "建筑艺术"),
    ROYAL_STORIES(3, "皇家故事"),
    CULTURAL_RELICS(4, "文物收藏"),
    NATURAL_ECOLOGY(5, "自然生态"),
    PHOTOGRAPHY(6, "摄影打卡"),
    MYTHOLOGY(7, "神话传说"),
    FOLK_CUSTOMS(8, "民俗风情"),
    FOOD_CULTURE(9, "美食文化"),
    INTANGIBLE_HERITAGE(10, "非遗文化");

    private final int code;
    private final String desc;

    public static Interest fromCode(int code) {
        for (Interest e : values()) {
            if (e.code == code) return e;
        }
        return null;
    }

    public static Interest fromDesc(String desc) {
        for (Interest e : values()) {
            if (e.desc.equals(desc)) return e;
        }
        return null;
    }
}
