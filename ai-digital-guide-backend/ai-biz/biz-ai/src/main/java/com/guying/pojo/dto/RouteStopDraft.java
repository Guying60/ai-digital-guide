package com.guying.pojo.dto;

import lombok.Data;

/**
 * LLM 结构化输出的单个地标草稿（仅 LLM 负责的字段）。
 */
@Data
public class RouteStopDraft {

    /** 地标展示名 */
    private String name;

    /** 更利于地图检索的具体地点名 */
    private String searchKeyword;

    /** 推荐理由 */
    private String recommendReason;

    /** 建议游览时长（分钟） */
    private Integer estimatedMinutes;
}
