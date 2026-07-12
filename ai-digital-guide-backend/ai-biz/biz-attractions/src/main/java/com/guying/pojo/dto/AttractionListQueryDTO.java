package com.guying.pojo.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AttractionListQueryDTO {
    @Size(max = 20, message = "不能超过20个字符")
    private String keyWord;

    private Integer type;

    /**
     * 按城市(地区)筛选,可空
     */
    private String city;

    private String lastId;
    private Integer pageSize = 6;

    /**
     * 按 updateTime 排序方向：desc（默认，最新在前）/ asc（最早在前）
     */
    private String sortOrder = "desc";

}
