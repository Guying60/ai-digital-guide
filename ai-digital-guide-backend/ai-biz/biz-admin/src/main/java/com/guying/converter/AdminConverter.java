package com.guying.converter;

import com.guying.pojo.dto.RegisterDTO;
import com.guying.pojo.vo.AdminRegisterVO;
import org.mapstruct.Mapper;
import com.guying.pojo.dto.LoginDTO;
import com.guying.pojo.entity.Admin;
import com.guying.pojo.vo.AdminLoginVO;

@Mapper(componentModel = "spring")
public interface AdminConverter {
    Admin toAdmin(LoginDTO loginDTO);
    AdminLoginVO toLoginVO(Admin admin);

    Admin toAdmin(RegisterDTO registerDto);


    AdminRegisterVO toRegisterVO(Admin admin);
}
