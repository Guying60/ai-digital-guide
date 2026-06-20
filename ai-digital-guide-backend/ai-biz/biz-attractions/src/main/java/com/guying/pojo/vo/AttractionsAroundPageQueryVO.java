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
    /**
     * 距用户的距离(米)
     */
    private Double distance;

}
