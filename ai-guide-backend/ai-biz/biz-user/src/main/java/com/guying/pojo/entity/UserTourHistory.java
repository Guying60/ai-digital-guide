package com.guying.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("tb_user_tour_history")
public class UserTourHistory {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;


    private Long attractionId;


    private String conversationId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;


    private String attractionName;

    private String coverUrl;

    private Integer type;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer score;

    private String feedbackText;

}
