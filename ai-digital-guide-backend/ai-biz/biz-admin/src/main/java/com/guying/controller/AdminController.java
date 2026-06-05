package com.guying.controller;

import com.guying.common.result.Result;
import com.guying.pojo.dto.RegisterDTO;
import com.guying.pojo.vo.AdminRegisterVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.guying.pojo.dto.LoginDTO;
import com.guying.pojo.vo.AdminLoginVO;
import com.guying.service.AdminService;

@RestController
@Slf4j
@Valid
@Tag(name = "管理员管理")
@RequestMapping("/v1/admins")
public class AdminController {


    @Autowired
    private AdminService adminService;



    @Operation(summary = "管理员登录")
    @PostMapping("/login")
    public Result<?> login(@RequestBody @Valid LoginDTO loginDto) {
        log.info("loginDto:{}", loginDto);
        AdminLoginVO adminLoginVO = adminService.login(loginDto);
        return Result.success(adminLoginVO);
    }

    @Operation(summary = "管理员注册")
    @PostMapping("/register")
    public Result<AdminRegisterVO> register(@RequestBody @Valid RegisterDTO registerDto) {
        AdminRegisterVO adminRegisterVO = adminService.register(registerDto);
        return Result.success(adminRegisterVO);
    }


}
