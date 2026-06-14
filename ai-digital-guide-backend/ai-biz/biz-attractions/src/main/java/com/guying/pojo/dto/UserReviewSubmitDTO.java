package com.guying.pojo.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class UserReviewSubmitDTO {

    @NotNull(message = "评价ID不能为空")
    private Long reviewId;

    @NotNull(message = "评分不能为空")
    @DecimalMin(value = "1.0", message = "评分最低1.0")
    @DecimalMax(value = "5.0", message = "评分最高5.0")
    private BigDecimal rating;

    @NotBlank(message = "评价内容不能为空")
    @Size(max = 500, message = "评价内容最多500字")
    private String content;

    @Size(max = 5, message = "标签最多5个")
    private List<String> tags;
}
