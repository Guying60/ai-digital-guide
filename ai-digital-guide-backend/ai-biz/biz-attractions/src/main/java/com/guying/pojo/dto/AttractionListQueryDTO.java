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

    private String lastId;
    private Integer pageSize = 6;

}
