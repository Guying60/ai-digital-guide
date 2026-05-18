package com.guying.service.impl;

import com.guying.common.enums.GenderEnum;
import com.guying.pojo.entity.User;
import com.guying.service.UserService;
import com.guying.user.service.UserInternalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class UserInternalServiceImpl implements UserInternalService {
    @Autowired
    private UserService userService;


    @Override
    public Map<String,String> getUserInfo(Long userId) {
        User user = userService.getById(userId);
        GenderEnum genderEnum = user.getGender() == null
                ? GenderEnum.UNKNOWN
                : GenderEnum.fromCode(user.getGender());
        return Map.of("gender", genderEnum.getDesc(),
                "age", user.getAge() == null ? "未知" : user.getAge().toString(),
                "userSetting", user.getUserSetting() == null ? "未知" : user.getUserSetting(),
                "nickname", user.getNickname() == null ? "未知" : user.getNickname());
    }

}
