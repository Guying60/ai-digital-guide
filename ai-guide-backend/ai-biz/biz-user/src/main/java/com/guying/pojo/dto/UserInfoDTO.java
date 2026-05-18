package com.guying.pojo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserInfoDTO {


    @Pattern(regexp = "^.{1,20}$", message = "昵称长度必须在1-20个字符之间")
    private String nickname;



    @Pattern(regexp = "^[\\s\\S]{0,100}$", message = "用户设定不能超过100个字符")
    private String userSetting;


    @Min(value = 0, message = "性别值不合法")
    @Max(value = 2, message = "性别值不合法")
    private Integer gender;



    @Min(value = 0, message = "年龄不能小于0")
    @Max(value = 120, message = "年龄不合法")
        private Integer age;
}
