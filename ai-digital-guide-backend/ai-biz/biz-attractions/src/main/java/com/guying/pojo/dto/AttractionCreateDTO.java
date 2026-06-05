package com.guying.pojo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AttractionCreateDTO {

    private String coverUrl;
    @Pattern(regexp = "^[\u4e00-\u9fa5a-zA-Z0-9]{2,20}$", message = "必须中英文，数字2到20个字")
    private String attractionName;

    /**
     * 0:主题乐园; 1: 博物馆与展馆; 2:自然公园; 3: 风景名胜与休闲度假; 4: 历史文化; 5: 古镇水乡; 6: 动植物园与水族馆; 7: 现代地标;
     */
    @Min(value = 0, message = "请选择正确的类型")
    @Max(value = 7, message = "请选择正确的类型")
    private Integer type;
}
