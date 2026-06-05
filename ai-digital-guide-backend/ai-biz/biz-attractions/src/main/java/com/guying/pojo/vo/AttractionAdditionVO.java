package com.guying.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AttractionAdditionVO {
    private Long id;
    private String attractionName;
    private String coverUrl;
    private Integer type;
}
