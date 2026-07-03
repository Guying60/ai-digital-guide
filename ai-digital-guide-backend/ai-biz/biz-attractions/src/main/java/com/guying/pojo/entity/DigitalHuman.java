package com.guying.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 数字人配置实体类
 * 对应表：tb_digital_human
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tb_digital_human")
public class DigitalHuman {


    /**
     * 主键 ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 数字人驱动视频 OSS 地址
     */
    private String videoUrl;

    /**
     * 数字人音色样本音频 OSS 地址
     */
    private String audioUrl;


    /**
     * 所属景区 ID
     */
    private Long attractionId;

    /**
     * 创建者（管理员）ID
     */
    private Long adminId;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}