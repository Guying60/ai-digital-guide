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

    /**
     * 按城市(地区)筛选,可空
     */
    private String city;

    private String lastId;
    private Integer pageSize = 6;

    /**
     * 按 createTime（上次对话时间）排序：desc（默认，最新在前）/ asc（最早在前）
     */
    private String sortOrder = "desc";

}
