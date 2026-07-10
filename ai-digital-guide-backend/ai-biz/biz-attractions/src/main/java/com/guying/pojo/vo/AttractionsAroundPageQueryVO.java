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

    /**
     * 平均分(全量口径,ROUND(AVG,1)),无评论时为 null
     */
    private Double rating;
    /**
     * 评论数(全量口径),无评论时为 0
     */
    private Integer reviewCount;

}
