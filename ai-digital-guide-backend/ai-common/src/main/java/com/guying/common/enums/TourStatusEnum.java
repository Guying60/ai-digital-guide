package com.guying.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 游览历史会话状态
 */
@Getter
@AllArgsConstructor
public enum TourStatusEnum implements IEnum<Integer> {
    IN_PROGRESS(0, "进行中"),
    ENDED(1, "已结束"),
    RATED(2, "已评价");

    private final int code;
    private final String desc;
}
