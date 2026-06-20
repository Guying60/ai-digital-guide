package com.guying.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AttractionDetailVO {
    private Long id;
    private String attractionName;
    private String coverUrl;
    private Integer type;

    private Double longitude;
    private Double latitude;
    private String province;
    private String city;
    private String district;
    private String adcode;
}
