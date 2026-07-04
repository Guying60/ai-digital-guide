package com.guying.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体类：面部表情情感分析记录表 (tb_face_emotion_record)
 * 与现有文本情感分析表 tb_ai_experience_analysis 相互独立，仅记录视觉大模型对面部帧的分类结果。
 * 原始人脸图像不入库、不落 OSS。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("tb_face_emotion_record")
public class FaceEmotionRecord implements Serializable {

    /**
     * 主键 ID (bigint, auto_increment)
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 游客 ID (bigint)
     */
    private Long userId;

    /**
     * 景点 ID (bigint)
     */
    private Long attractionId;

    /**
     * 会话 ID (varchar(64))，取自 WebSocket ChatSessionContext.conversationId()
     */
    private String conversationId;

    /**
     * 表情 (tinyint)，对应 ExpressionEnum.code：0喜悦 1惊讶 2中性 3困惑 4厌恶 5愤怒 6悲伤
     */
    private Integer expression;

    /**
     * 置信度 (double)，大模型返回 0~1
     */
    private Double confidence;

    /**
     * 额外结构化字段 (varchar(500))，JSON 串，如 {"reason":"..."}
     */
    private String detail;

    /**
     * 创建时间 (datetime)
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
