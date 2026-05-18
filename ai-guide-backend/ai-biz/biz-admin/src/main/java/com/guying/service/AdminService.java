package com.guying.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.guying.pojo.dto.RegisterDTO;
import com.guying.pojo.vo.AdminLoginVO;
import com.guying.pojo.vo.AdminRegisterVO;
import com.guying.pojo.dto.LoginDTO;
import com.guying.pojo.entity.Admin;

public interface AdminService extends IService<Admin> {
    AdminLoginVO login(LoginDTO loginDto);

    AdminRegisterVO register(RegisterDTO registerDto);
}
