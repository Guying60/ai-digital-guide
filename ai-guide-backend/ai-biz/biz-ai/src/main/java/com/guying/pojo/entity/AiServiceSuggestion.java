package com.guying.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_ai_service_suggestion")
public class AiServiceSuggestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long attractionId;

    /**
     * 建议类型 0=近7天 1=近30天
     */
    private Integer type;

    /**
     * 简短总结
     */
    private String summary;

    /**
     * 完整服务建议
     */
    private String suggestion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}