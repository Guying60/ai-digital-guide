package com.guying.controller;

import com.guying.common.result.Result;
import com.guying.common.result.ScrollResult;
import com.guying.pojo.dto.LoginDTO;
import com.guying.pojo.dto.RegisterDTO;
import com.guying.pojo.dto.UserInfoDTO;
import com.guying.pojo.dto.UserTourHistoryPageQueryDTO;
import com.guying.pojo.vo.UserInfoVO;
import com.guying.pojo.vo.UserLoginVO;
import com.guying.pojo.vo.UserRegisterVO;
import com.guying.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@RestController
@Slf4j
@Valid
@Tag(name = "用户管理")
@RequestMapping("/v1/users")
public class UserController {
    @Autowired
    private UserService userService;



    /**
     * 登录
     * @param loginDto 登录参数
     * @return 登录结果
     */
    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<UserLoginVO> login(@RequestBody @Valid LoginDTO loginDto){
        log.info("loginDto:{}", loginDto);
        UserLoginVO userLoginVo = userService.login(loginDto);
        return Result.success(userLoginVo);
    }

    /**
     * 更新用户信息
     * @param userInfoDto
     * @return
     */
    @Operation(summary = "更新用户信息")
    @PutMapping()
    public Result updateUserInfo(@RequestBody @Valid UserInfoDTO userInfoDto){
        log.info("userInfoDto:{}", userInfoDto);
        userService.updateUserInfo(userInfoDto);
        return Result.success();
    }



    @Operation(summary = "获取用户信息")
    @GetMapping("/userInfo")
    public Result<UserInfoVO> getUserInfo() {
        UserInfoVO userInfoVO = userService.getUserInfo();
        return Result.success(userInfoVO);
    }

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<UserRegisterVO> userRegister(@RequestBody @Valid RegisterDTO registerDTO) {
        UserRegisterVO userRegisterVO = userService.userRegister(registerDTO);
        return Result.success(userRegisterVO);
    }



}
