package com.guying.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.repository.AbstractRepository;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.guying.common.constants.RedisConstants;
import com.guying.common.result.ScrollResult;
import com.guying.context.UserContext;
import com.guying.converter.UserConverter;
import com.guying.converter.UserTourHistoryConverter;
import com.guying.exception.ServiceException;
import com.guying.mapper.UserMapper;
import com.guying.mapper.UserTourHistoryMapper;
import com.guying.pojo.dto.LoginDTO;
import com.guying.pojo.dto.RegisterDTO;
import com.guying.pojo.dto.UserInfoDTO;
import com.guying.pojo.dto.UserTourHistoryPageQueryDTO;
import com.guying.pojo.entity.User;
import com.guying.pojo.entity.UserTourHistory;
import com.guying.pojo.vo.UserInfoVO;
import com.guying.pojo.vo.UserLoginVO;
import com.guying.pojo.vo.UserRegisterVO;
import com.guying.pojo.vo.UserTourHistoryPageVO;
import com.guying.service.UserService;
import com.guying.utils.JwtUtil;
import com.guying.utils.PasswordUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.guying.common.constants.RedisConstants.*;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private UserConverter userConverter;
    @Autowired
    private UserTourHistoryMapper userTourHistoryMapper;


    /**
     * 登录
     * @param loginDto
     * @return
     */
    @Override
    public UserLoginVO login(LoginDTO loginDto) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, loginDto.getUsername())
                .select(User::getId, User::getUsername, User::getPassword,
                        User::getNickname, User::getAge, User::getGender, User::getAvatarUrl);
        User user = userMapper.selectOne(queryWrapper);
        if (user == null || !StringUtils.hasText(user.getPassword())
                || !PasswordUtil.matches(loginDto.getPassword(), user.getPassword())) {
            throw new ServiceException("用户名或密码错误");
        }
        String uuid = UUID.randomUUID().toString();
        //生成JWT令牌，将uuid作为claims的值
        Map<String, Object> claims = new HashMap<>();
        claims.put("uuid", uuid);
        String token = jwtUtil.createToken(user.getId().toString(), claims);
        stringRedisTemplate.opsForValue().set(USER_LOGIN_KEY +uuid, "1",LOGIN_TOKEN_EXPIRE_TIME, TimeUnit.MILLISECONDS);
        UserLoginVO userLoginVO = userConverter.toLoginVO(user);
        userLoginVO.setToken(token);
        return userLoginVO;
    }

    /**
     * 更新用户信息
     * @param userInfoDto
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateUserInfo(UserInfoDTO userInfoDto) {
        Long userId = UserContext.getUserId();
        User user = userConverter.toUser(userInfoDto);
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getId, userId);
        userMapper.update(user, queryWrapper);
        //检查并删除用户设定
        String key = USER_INFO_KEY + userId;
        if (stringRedisTemplate.hasKey(key)){
            stringRedisTemplate.delete(key);
        }
    }



    @Override
    public UserInfoVO getUserInfo() {
        // 主键查询本就很快，无需缓存；个人信息缓存由 AI 提示词流程(UserInternalServiceImpl)单独维护
        User user = userMapper.selectById(UserContext.getUserId());
        if (user == null) {
            throw new ServiceException("用户不存在");
        }
        return userConverter.toUserInfoVO(user);
    }

    @Override
    public UserRegisterVO userRegister(RegisterDTO registerDTO) {
        if (!registerDTO.getPassword().equals(registerDTO.getConfirmPassword())) {
            throw new ServiceException("两次输入的密码不一致");
        }
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, registerDTO.getUsername());
        if (userMapper.selectCount(queryWrapper) > 0) {
            throw new ServiceException("用户名已存在");
        }
        User user = userConverter.toUser(registerDTO);
        user.setPassword(PasswordUtil.encode(registerDTO.getPassword()));
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("用户名已存在");
        }
        return userConverter.toUserRegisterVO(user);
    }

    @Override
    public void saveUserTourHistory(UserTourHistory userTourHistory) {
        userTourHistoryMapper.insert(userTourHistory);
    }

    @Override
    public void upsertUserTourHistory(UserTourHistory userTourHistory) {
        LambdaQueryWrapper<UserTourHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserTourHistory::getUserId, userTourHistory.getUserId())
               .eq(UserTourHistory::getConversationId, userTourHistory.getConversationId());
        UserTourHistory existing = userTourHistoryMapper.selectOne(wrapper);
        if (existing != null) {
            // 已存在 → 更新 messageCount；tourStatus 只升不降（0→1→2，不允许 2→1 或 1→0）
            if (userTourHistory.getMessageCount() != null) {
                existing.setMessageCount(userTourHistory.getMessageCount());
            }
            if (userTourHistory.getTourStatus() != null) {
                Integer cur = existing.getTourStatus();
                Integer incoming = userTourHistory.getTourStatus();
                if (cur == null || incoming > cur) {
                    existing.setTourStatus(incoming);
                }
            }
            userTourHistoryMapper.updateById(existing);
        } else {
            // 不存在 → 插入新记录
            userTourHistoryMapper.insert(userTourHistory);
        }
    }

    @Override
    public void deleteUserTourHistoryByConversation(Long userId, String conversationId) {
        LambdaQueryWrapper<UserTourHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserTourHistory::getUserId, userId)
               .eq(UserTourHistory::getConversationId, conversationId);
        userTourHistoryMapper.delete(wrapper);
    }

}
