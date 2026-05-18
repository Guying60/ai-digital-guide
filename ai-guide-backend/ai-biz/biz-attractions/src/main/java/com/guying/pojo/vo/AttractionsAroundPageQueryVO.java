package com.guying.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AttractionsAroundPageQueryVO {
    private Long id;
    private String coverUrl;
    private String attractionName;
    private String distance;

}
