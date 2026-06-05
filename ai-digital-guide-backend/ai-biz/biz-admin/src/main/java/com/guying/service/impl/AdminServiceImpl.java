package com.guying.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.guying.exception.ServiceException;
import com.guying.pojo.dto.RegisterDTO;
import com.guying.pojo.vo.AdminLoginVO;
import com.guying.pojo.vo.AdminRegisterVO;
import com.guying.utils.JwtUtil;
import com.guying.utils.PasswordUtil;
import com.guying.converter.AdminConverter;
import com.guying.mapper.AdminMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.guying.pojo.dto.LoginDTO;
import com.guying.pojo.entity.Admin;
import com.guying.service.AdminService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.guying.common.constants.RedisConstants.*;

@Service
public class AdminServiceImpl extends ServiceImpl<AdminMapper, Admin> implements AdminService {
    @Autowired
    private AdminMapper adminMapper;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private AdminConverter adminConverter;



    @Override
    public AdminLoginVO login(LoginDTO loginDto) {
        LambdaQueryWrapper<Admin> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Admin::getUsername, loginDto.getUsername());
        Admin admin = adminMapper.selectOne(queryWrapper);
//        if (admin == null || !PasswordUtil.matches(loginDto.getPassword(), admin.getPassword())) {
//            throw new ServiceException("用户名或密码错误");
//        }
        String uuid = UUID.randomUUID().toString();
        //生成JWT令牌，将uuid作为claims的值
        Map<String, Object> claims = new HashMap<>();
        claims.put("uuid", uuid);
        String token = jwtUtil.createToken(admin.getId().toString(), claims);
        stringRedisTemplate.opsForValue().set(ADMIN_LOGIN_KEY +uuid, "1",LOGIN_TOKEN_EXPIRE_TIME, TimeUnit.MILLISECONDS);
        AdminLoginVO adminLoginVO = adminConverter.toLoginVO(admin);
        adminLoginVO.setToken(token);
        return adminLoginVO;
    }

    @Override
    public AdminRegisterVO register(RegisterDTO registerDto) {
        if (!registerDto.getPassword().equals(registerDto.getConfirmPassword())) {
            throw new ServiceException("两次输入的密码不一致");
        }
        Admin admin = adminConverter.toAdmin(registerDto);
        admin.setPassword(PasswordUtil.encode(registerDto.getPassword()));
        try {
            adminMapper.insert(admin);
        } catch (Exception e) {
            throw new ServiceException("注册失败，用户名已存在");
        }
        return adminConverter.toRegisterVO(admin);
    }
}
