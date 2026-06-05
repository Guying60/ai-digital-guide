package com.guying.converter;

import com.guying.pojo.dto.RegisterDTO;
import com.guying.pojo.dto.UserInfoDTO;
import com.guying.pojo.entity.User;
import com.guying.pojo.vo.UserInfoVO;
import com.guying.pojo.vo.UserLoginVO;
import com.guying.pojo.vo.UserRegisterVO;
import org.mapstruct.Mapper;

import java.util.Map;

@Mapper(componentModel = "spring")
public interface UserConverter {
    User toUser(UserInfoDTO userInfoDto);
    UserLoginVO toLoginVO(User user);


    UserInfoVO toUserInfoVO(User user);

    User toUser(RegisterDTO registerDTO);

    UserRegisterVO toUserRegisterVO(User user);

    UserInfoVO toUserInfoVO(Map<Object, Object> entries);
}