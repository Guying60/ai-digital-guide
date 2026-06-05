package com.guying.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.guying.common.result.ScrollResult;
import com.guying.pojo.dto.LoginDTO;
import com.guying.pojo.dto.RegisterDTO;
import com.guying.pojo.dto.UserInfoDTO;
import com.guying.pojo.dto.UserTourHistoryPageQueryDTO;
import com.guying.pojo.entity.User;
import com.guying.pojo.entity.UserTourHistory;
import com.guying.pojo.vo.UserInfoVO;
import com.guying.pojo.vo.UserLoginVO;
import com.guying.pojo.vo.UserRegisterVO;
import jakarta.validation.Valid;

public interface UserService extends IService<User> {

    UserLoginVO login(LoginDTO loginDto);

    void updateUserInfo(UserInfoDTO userInfoDto);


    UserInfoVO getUserInfo();


    UserRegisterVO userRegister(RegisterDTO registerDTO);

    void saveUserTourHistory(UserTourHistory userTourHistory);
}
