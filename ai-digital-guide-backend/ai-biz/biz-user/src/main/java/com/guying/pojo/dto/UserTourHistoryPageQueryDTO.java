package com.guying.pojo.dto;


import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserTourHistoryPageQueryDTO {

    @Size(max = 20, message = "不能超过20个字符")
    private String keyWord;

    private Integer type;

    private String lastId;
    private Integer pageSize = 6;

}
