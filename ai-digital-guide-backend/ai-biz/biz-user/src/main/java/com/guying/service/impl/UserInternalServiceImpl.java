package com.guying.service.impl;

import com.guying.attractions.dto.AttractionDTO;
import com.guying.attractions.service.UserAttractionsInternalService;
import com.guying.common.constants.RedisConstants;
import com.guying.common.enums.GenderEnum;
import com.guying.common.enums.guide.GuideDepth;
import com.guying.common.enums.guide.GuideStyle;
import com.guying.common.enums.guide.Interest;
import com.guying.common.enums.guide.TravelPurpose;
import com.guying.pojo.entity.User;
import com.guying.pojo.entity.UserGuidePreference;
import com.guying.pojo.entity.UserTourHistory;
import com.guying.service.UserGuidePreferenceService;
import com.guying.service.UserService;
import com.guying.user.service.UserInternalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserInternalServiceImpl implements UserInternalService {
    @Autowired
    private UserService userService;
    @Autowired
    private UserGuidePreferenceService preferenceService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private UserAttractionsInternalService attractionsInternalService;


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
                // DB 存的是英文枚举名（savePreference 经 Interest.valueOf 校验后落库，如 HISTORY_CULTURE），
                // 这里同样用 Interest.valueOf 解析，保持读写一致；非法值直接丢弃。
                String interestLabels = Arrays.stream(pref.getInterests().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(name -> {
                            try {
                                return Interest.valueOf(name).getDesc();
                            } catch (IllegalArgumentException e) {
                                return null;
                            }
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

    @Override
    public void createTourHistory(Long userId, Long attractionId, String conversationId, Integer tourStatus) {
        AttractionDTO attraction = attractionsInternalService.getAttraction(attractionId);
        if (attraction == null) {
            log.error("createTourHistory: attraction not found for id={}", attractionId);
            return;
        }
        UserTourHistory entity = new UserTourHistory();
        entity.setUserId(userId);
        entity.setAttractionId(attractionId);
        entity.setConversationId(conversationId);
        entity.setAttractionName(attraction.getAttractionName());
        entity.setCoverUrl(attraction.getCoverUrl());
        entity.setType(attraction.getType());
        entity.setCity(attraction.getCity());
        // messageCount 不设值：继续对话复用记录时不得清零已累计的消息数（upsert 对 null 不更新，新插入默认 0）
        entity.setTourStatus(tourStatus);
        userService.upsertUserTourHistory(entity);
    }

    @Override
    public void deleteTourHistory(Long userId, String conversationId) {
        userService.deleteUserTourHistoryByConversation(userId, conversationId);
    }

    @Override
    public Integer getTourHistoryMessageCount(Long userId, String conversationId) {
        UserTourHistory history = userService.getUserTourHistoryByConversation(userId, conversationId);
        if (history == null) {
            return null;
        }
        return history.getMessageCount() == null ? 0 : history.getMessageCount();
    }

}
