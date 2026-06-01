package com.guying.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.repository.AbstractRepository;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.guying.common.constants.RedisConstants;
import com.guying.common.enums.GenderEnum;
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
import static net.sf.jsqlparser.util.validation.metadata.NamedObject.user;

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
        queryWrapper.eq(User::getUsername, loginDto.getUsername());
        User user = userMapper.selectOne(queryWrapper);
        if (user == null || !PasswordUtil.matches(loginDto.getPassword(), user.getPassword())) {
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
        Long userId = UserContext.getUserId();
        String key = USER_INFO_KEY + userId;
        if (stringRedisTemplate.hasKey(key)){
            Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
            UserInfoVO userInfoVO = new UserInfoVO();
            userInfoVO.setNickname((String) entries.get("nickname"));
            userInfoVO.setUserSetting((String) entries.get("userSetting"));
            userInfoVO.setGender(GenderEnum.fromCode(Integer.parseInt((String) entries.get("gender"))).getCode());
            userInfoVO.setAge(Integer.valueOf((String) entries.get("age")));
            return userInfoVO;
        }else{
            User user = userMapper.selectById(userId);
            if (user == null) {
                throw new ServiceException("用户不存在");
            }
            GenderEnum genderEnum = user.getGender() == null
                    ? GenderEnum.UNKNOWN
                    : GenderEnum.fromCode(user.getGender());
            Map<String, Object> userMap = Map.of("gender", String.valueOf(genderEnum.getCode()),
                    "age", user.getAge().toString(),
                    "userSetting", user.getUserSetting() == null ? "未知" : user.getUserSetting(),
                    "nickname", user.getNickname() == null ? "未知" : user.getNickname());
            stringRedisTemplate.opsForHash().putAll(key, userMap);
            stringRedisTemplate.expire(key, USER_INFO_EXPIRE_TIME, TimeUnit.HOURS);
            return userConverter.toUserInfoVO(user);
        }
    }

    @Override
    public UserRegisterVO userRegister(RegisterDTO registerDTO) {
        if (!registerDTO.getPassword().equals(registerDTO.getConfirmPassword())) {
            throw new ServiceException("两次输入的密码不一致");
        }
        User user = userConverter.toUser(registerDTO);
        user.setPassword(PasswordUtil.encode(registerDTO.getPassword()));
        try {
            userMapper.insert(user);
        } catch (Exception e) {
            throw new ServiceException("注册失败,可能用户名已存在");
        }
        return userConverter.toUserRegisterVO(user);
    }

    @Override
    public void saveUserTourHistory(UserTourHistory userTourHistory) {
        userTourHistoryMapper.insert(userTourHistory);
    }

}
