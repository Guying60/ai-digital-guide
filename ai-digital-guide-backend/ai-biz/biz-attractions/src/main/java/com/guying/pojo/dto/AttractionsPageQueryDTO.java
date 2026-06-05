package com.guying.pojo.dto;


import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AttractionsPageQueryDTO {

    @Pattern(regexp = "^{0,20}$", message = "不能超过20个字符")
    private String keyWord;


    private String lastId;
    private Integer pageSize = 6;

}
