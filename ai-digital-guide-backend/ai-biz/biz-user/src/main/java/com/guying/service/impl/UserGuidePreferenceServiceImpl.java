package com.guying.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.guying.common.constants.RedisConstants;
import com.guying.common.enums.guide.GuideDepth;
import com.guying.common.enums.guide.GuideStyle;
import com.guying.common.enums.guide.Interest;
import com.guying.common.enums.guide.TravelPurpose;
import com.guying.context.UserContext;
import com.guying.mapper.UserGuidePreferenceMapper;
import com.guying.pojo.dto.UserGuidePreferenceDTO;
import com.guying.pojo.entity.UserGuidePreference;
import com.guying.pojo.vo.UserGuidePreferenceVO;
import com.guying.service.UserGuidePreferenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserGuidePreferenceServiceImpl
        extends ServiceImpl<UserGuidePreferenceMapper, UserGuidePreference>
        implements UserGuidePreferenceService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void savePreference(UserGuidePreferenceDTO dto) {
        Long userId = UserContext.getUserId();

        UserGuidePreference preference = lambdaQuery()
                .eq(UserGuidePreference::getUserId, userId)
                .one();

        if (preference == null) {
            preference = new UserGuidePreference();
            preference.setUserId(userId);
        }

        preference.setGuideStyle(dto.getGuideStyle());
        preference.setGuideDepth(dto.getGuideDepth());
        preference.setTravelPurpose(dto.getTravelPurpose());
        preference.setSpecialRequirements(dto.getSpecialRequirements());

        // interests: 校验每个枚举name是否合法，过滤非法值
        if (dto.getInterests() != null && !dto.getInterests().isBlank()) {
            String validated = Arrays.stream(dto.getInterests().split(","))
                    .map(String::trim)
                    .filter(name -> {
                        try {
                            Interest.valueOf(name);
                            return true;
                        } catch (IllegalArgumentException e) {
                            log.warn("忽略非法兴趣偏好值: {}", name);
                            return false;
                        }
                    })
                    .collect(Collectors.joining(","));
            preference.setInterests(validated.isEmpty() ? null : validated);
        } else {
            preference.setInterests(null);
        }

        saveOrUpdate(preference);

        // 删除用户信息缓存，下次 getUserInfo() 会重建包含最新偏好数据的缓存
        String cacheKey = RedisConstants.USER_INFO_KEY + userId;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(cacheKey))) {
            stringRedisTemplate.delete(cacheKey);
            log.debug("已清除用户信息缓存: userId={}", userId);
        }
    }

    @Override
    public UserGuidePreferenceVO getPreference() {
        Long userId = UserContext.getUserId();
        UserGuidePreference preference = lambdaQuery()
                .eq(UserGuidePreference::getUserId, userId)
                .one();

        if (preference == null) {
            return null;
        }

        UserGuidePreferenceVO vo = new UserGuidePreferenceVO();
        vo.setId(preference.getId());
        vo.setSpecialRequirements(preference.getSpecialRequirements());
        vo.setInterests(preference.getInterests());

        if (preference.getGuideStyle() != null) {
            GuideStyle style = GuideStyle.fromCode(preference.getGuideStyle());
            vo.setGuideStyle(preference.getGuideStyle());
            vo.setGuideStyleName(style != null ? style.getDesc() : null);
        }
        if (preference.getGuideDepth() != null) {
            GuideDepth depth = GuideDepth.fromCode(preference.getGuideDepth());
            vo.setGuideDepth(preference.getGuideDepth());
            vo.setGuideDepthName(depth != null ? depth.getDesc() : null);
        }
        if (preference.getTravelPurpose() != null) {
            TravelPurpose purpose = TravelPurpose.fromCode(preference.getTravelPurpose());
            vo.setTravelPurpose(preference.getTravelPurpose());
            vo.setTravelPurposeName(purpose != null ? purpose.getDesc() : null);
        }

        return vo;
    }
}
