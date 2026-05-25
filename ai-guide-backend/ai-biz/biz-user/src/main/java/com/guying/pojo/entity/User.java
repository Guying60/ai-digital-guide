package com.guying.pojo.entity;


import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("tb_user")
public class User {

    /**
     * 用户ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /**
     * 用户账号
     */
    private String username;
    /**
     * 用户密码
     */
    @TableField(select = false)
    private String password;

    /**
     * 用户头像
     */
    private String avatarUrl;

    /**
     * 用户性别
     */
    private Integer gender;//0:女; 1:男; 2:未知
    /**
     * 用户年龄
     */
    private Integer age;
    /**
     * 用户昵称
     */
    private String nickname;
    /**
     * 用户设定
     */
    private String userSetting;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;


}
