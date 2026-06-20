package com.guying.pojo.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AttractionListVO {
    private Long id;
    private String attractionName;
    private String coverUrl;
    private Integer type;
    private String city;
}
