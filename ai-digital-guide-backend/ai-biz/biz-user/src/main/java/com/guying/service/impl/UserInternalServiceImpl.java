package com.guying.service.impl;

import com.guying.common.constants.RedisConstants;
import com.guying.common.enums.GenderEnum;
import com.guying.common.enums.guide.GuideDepth;
import com.guying.common.enums.guide.GuideStyle;
import com.guying.common.enums.guide.Interest;
import com.guying.common.enums.guide.TravelPurpose;
import com.guying.pojo.entity.User;
import com.guying.pojo.entity.UserGuidePreference;
import com.guying.service.UserGuidePreferenceService;
import com.guying.service.UserService;
import com.guying.user.service.UserInternalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class UserInternalServiceImpl implements UserInternalService {
    @Autowired
    private UserService userService;
    @Autowired
    private UserGuidePreferenceService preferenceService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Map<String,String> getUserInfo(Long userId) {
        User user = userService.getById(userId);
        GenderEnum genderEnum = user.getGender() == null
                ? GenderEnum.UNKNOWN
                : GenderEnum.fromCode(user.getGender());

        // 所有字段先给默认值，避免提示词模板变量为空
        Map<String, String> map = new HashMap<>();
        map.put("gender", String.valueOf(genderEnum.getCode()));
        map.put("age", user.getAge() == null ? "未知" : user.getAge().toString());
        map.put("nickname", user.getNickname() == null ? "未知" : user.getNickname());
        map.put("guideStyle", "未知");
        map.put("guideDepth", "未知");
        map.put("interests", "未知");
        map.put("travelPurpose", "未知");
        map.put("specialRequirements", "无");

        UserGuidePreference pref = preferenceService.lambdaQuery()
                .eq(UserGuidePreference::getUserId, userId).one();
        if (pref != null) {
            if (pref.getGuideStyle() != null) {
                GuideStyle style = GuideStyle.fromCode(pref.getGuideStyle());
                if (style != null) map.put("guideStyle", style.getDesc());
            }
            if (pref.getGuideDepth() != null) {
                GuideDepth depth = GuideDepth.fromCode(pref.getGuideDepth());
                if (depth != null) map.put("guideDepth", depth.getDesc());
            }
            if (pref.getInterests() != null && !pref.getInterests().isBlank()) {
                String interestLabels = Arrays.stream(pref.getInterests().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(name -> {
                            Interest interest = Interest.fromDesc(name);
                            return interest != null ? interest.getDesc() : null;
                        })
                        .filter(l -> l != null)
                        .collect(Collectors.joining("、"));
                if (!interestLabels.isEmpty()) map.put("interests", interestLabels);
            }
            if (pref.getTravelPurpose() != null) {
                TravelPurpose purpose = TravelPurpose.fromCode(pref.getTravelPurpose());
                if (purpose != null) map.put("travelPurpose", purpose.getDesc());
            }
            if (pref.getSpecialRequirements() != null && !pref.getSpecialRequirements().isBlank()) {
                map.put("specialRequirements", pref.getSpecialRequirements());
            }
        }

        // 缓存完整用户信息（含偏好），供 DynamicPromptService 读取
        String cacheKey = RedisConstants.USER_INFO_KEY + userId;
        stringRedisTemplate.opsForHash().putAll(cacheKey, map);
        stringRedisTemplate.expire(cacheKey, RedisConstants.USER_INFO_EXPIRE_TIME, TimeUnit.HOURS);

        return map;
    }

}
