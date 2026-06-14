package com.guying.pojo.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserReviewVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long attractionId;

    private String attractionName;

    private String coverUrl;

    private BigDecimal rating;

    private String content;

    private List<String> tags;

    /**
     * 0=待评价 1=已评价
     */
    private Integer status;

    private LocalDateTime createTime;
}
