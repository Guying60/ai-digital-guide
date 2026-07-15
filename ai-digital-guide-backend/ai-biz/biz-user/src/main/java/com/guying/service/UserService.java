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

    /**
     * Upsert 游览历史记录：按 userId+conversationId 查，存在则更新（messageCount 更新，
     * tourStatus 只升不降），不存在则插入。
     */
    void upsertUserTourHistory(UserTourHistory userTourHistory);

    /** 按 userId + conversationId 删除游览历史记录（无效会话清理） */
    void deleteUserTourHistoryByConversation(Long userId, String conversationId);

    /** 按 userId + conversationId 查询游览历史记录，不存在返回 null */
    UserTourHistory getUserTourHistoryByConversation(Long userId, String conversationId);
}
