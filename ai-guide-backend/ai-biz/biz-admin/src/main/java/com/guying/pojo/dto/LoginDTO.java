package com.guying.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginDTO {
    @Pattern(regexp = "^[a-zA-Z0-9_-]{4,16}$", message = "账号格式不正确，需为4-16位字母、数字或下划线")
    @NotBlank(message = "账号不能为空")
    private String username;


    @Pattern(regexp = "^[a-zA-Z0-9!@#$%^&*()_+]{6,20}$", message = "密码长度必须为6-20位")
    @NotBlank(message = "密码不能为空")
    private String password;
}
