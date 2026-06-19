package com.guying.pojo.dto;

import lombok.Data;

import java.util.List;

/**
 * LLM 结构化输出的路线草稿（仅 LLM 负责的字段），后端再映射为 RoutePlanVO。
 */
@Data
public class RoutePlanDraft {

    /** 路线标题 */
    private String title;

    /** 路线总览说明 */
    private String summary;

    /** 有序地标 */
    private List<RouteStopDraft> stops;
}
