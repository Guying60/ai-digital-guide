package com.guying.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserInfoVO {

    private String nickname;
    private String userSetting;
    private String avatarUrl;
    private Integer gender;
    private Integer age;
}
