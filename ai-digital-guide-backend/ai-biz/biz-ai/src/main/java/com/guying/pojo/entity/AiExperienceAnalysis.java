package com.guying.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体类：AI 体验分析表 (tb_ai_experience_analysis)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("tb_ai_experience_analysis")
public class AiExperienceAnalysis implements Serializable {


    /**
     * 主键 ID (bigint, auto_increment)
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 专注度 (tinyint)
     */
    private Integer focus;

    /**
     * 情绪 (tinyint)
     */
    private Integer emotion;

    /**
     * 创建时间 (datetime)
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 会话 ID (varchar(64))
     */
    private String conversationId;


    /**
     * 用户 ID (bigint)
     */
    private Long userId;

    /**
     * 景点ID
     *
     */
    private Long attractionId;

    /**
     * 被分析的游客原话（冗余存储，供负面样本直接取用，避免反查 chat memory 产生笛卡尔积）
     */
    private String message;


}