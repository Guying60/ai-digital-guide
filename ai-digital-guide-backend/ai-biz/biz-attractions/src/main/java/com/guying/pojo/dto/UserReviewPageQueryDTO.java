package com.guying.pojo.dto;

import lombok.Data;

@Data
public class UserReviewPageQueryDTO {

    /**
     * 游标，上一页最后一条数据的 ID，首次请求不传
     */
    private String lastId;

    private Integer pageSize = 10;

    /**
     * null=全部, 0=待评价, 1=已评价
     */
    private Integer status;
}
