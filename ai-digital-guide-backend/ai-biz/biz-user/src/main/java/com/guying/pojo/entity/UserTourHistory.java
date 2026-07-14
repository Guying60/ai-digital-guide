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

    /**
     * 市(冗余自景点,用于地区筛选)
     */
    private String city;

    /**
     * 本会话对话消息条数（用户提问 + AI 回复），断开连接时写入
     */
    private Integer messageCount;

    /**
     * 会话状态：0-进行中 1-已结束 2-已评价
     */
    private Integer tourStatus;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
