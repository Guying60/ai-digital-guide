package com.guying.attractions.dto;

import lombok.Data;

/**
 * 景点评论聚合结果（全量口径，无时间窗口）。
 * avgScore    = ROUND(AVG(rating),1)，无评论时为 null。
 * reviewCount = 符合口径的评论数，无评论时不返回行。
 */
@Data
public class AttractionReviewAggregateDTO {
    private Long attractionId;
    private Double avgScore;
    private Long reviewCount;
}
