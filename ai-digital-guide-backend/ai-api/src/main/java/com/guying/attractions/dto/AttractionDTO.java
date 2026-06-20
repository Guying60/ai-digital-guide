package com.guying.attractions.dto;

import lombok.Data;

@Data
public class AttractionDTO {

    private String attractionName;
    private String coverUrl;
    private Integer type;
    /**
     * 市(用于旅游历史地区筛选)
     */
    private String city;

}
