package com.guying.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TaskStatusEnum {
    PROCESSING("文件解析中"),
    SUCCESS("解析成功"),
    FAILED("解析失败");

    private final String description;


}