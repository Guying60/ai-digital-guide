package com.guying.pojo.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AttractionsPageQueryDTO {

    @Size(max = 20, message = "不能超过20个字符")
    private String keyWord;

    /**
     * 当前所在城市(地区筛选键),前端定位后传入
     */
    @NotBlank(message = "城市不能为空")
    private String city;

    /**
     * 用户当前经度(GCJ-02)
     */
    @NotNull(message = "经度不能为空")
    private Double userLongitude;

    /**
     * 用户当前纬度(GCJ-02)
     */
    @NotNull(message = "纬度不能为空")
    private Double userLatitude;

    /**
     * 游标:上一页最后一条的距离(米),首页不传
     */
    private Double lastDistance;

    /**
     * 游标:上一页最后一条的景点ID,与 lastDistance 配合做同距离时的二级游标
     */
    private String lastId;

    private Integer pageSize = 6;

}
