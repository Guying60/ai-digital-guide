package com.guying.pojo.entity;


import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("tb_attraction")
public class Attraction {
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String attractionName;

    private String coverUrl;
    /**
     * 0:主题乐园; 1: 博物馆与展馆; 2:自然公园; 3: 风景名胜与休闲度假; 4: 历史文化; 5: 古镇水乡; 6: 动植物园与水族馆; 7: 现代地标;
     */
    private Integer type;

    /**
     * 经度(GCJ-02 高德坐标系)
     */
    private Double longitude;
    /**
     * 纬度(GCJ-02 高德坐标系)
     */
    private Double latitude;

    private String province;
    /**
     * 市(地区筛选键)
     */
    private String city;
    private String district;
    /**
     * 高德区域编码
     */
    private String adcode;

    private Long adminId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 距离用户的距离(米),非数据库字段,由附近景点查询动态计算
     */
    @TableField(exist = false)
    private Double distance;

}
