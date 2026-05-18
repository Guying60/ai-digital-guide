package com.guying.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SuggestionTypeEnum {
    WEEKLY(0, "近7天"),
    MONTHLY(1, "近30天");

    private final int code;
    private final String desc;
}