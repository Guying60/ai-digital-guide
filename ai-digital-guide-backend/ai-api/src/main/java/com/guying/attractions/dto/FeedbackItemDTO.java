package com.guying.attractions.dto;

import lombok.Data;

/**
 * 游客评价反馈条目（供 AI 运营建议分析用）。
 * rating 为评分（1-5），content 为评价文本。
 */
@Data
public class FeedbackItemDTO {
    private Integer rating;
    private String content;
}
