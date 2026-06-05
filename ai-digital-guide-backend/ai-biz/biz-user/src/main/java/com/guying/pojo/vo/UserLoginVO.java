package com.guying.pojo.vo;

import lombok.Data;

@Data
public class UserLoginVO {
    private Long id;
    private String nickName;
    private Integer age;
    /**
     * 性别 0:女 1:男 2:未知
     */
    private String avatarUrl;
    private Integer gender;
    private String token;
}
