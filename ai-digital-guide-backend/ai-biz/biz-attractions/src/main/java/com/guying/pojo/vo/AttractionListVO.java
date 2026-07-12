package com.guying.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class AttractionListVO {
    private Long id;
    private String attractionName;
    private String coverUrl;
    private Integer type;
    private String city;

    /**
     * 平均分(全量口径,ROUND(AVG,1)),无评论时为 null
     */
    private Double rating;
    /**
     * 评论数(全量口径),无评论时为 0
     */
    private Integer reviewCount;

    /**
     * 最近更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updateTime;
}
