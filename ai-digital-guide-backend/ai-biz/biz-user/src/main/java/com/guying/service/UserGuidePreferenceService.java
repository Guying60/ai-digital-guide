package com.guying.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.guying.pojo.dto.UserGuidePreferenceDTO;
import com.guying.pojo.entity.UserGuidePreference;
import com.guying.pojo.vo.UserGuidePreferenceVO;

public interface UserGuidePreferenceService extends IService<UserGuidePreference> {

    /**
     * 保存或更新当前用户的导览偏好（存在则更新，不存在则新增）
     */
    void savePreference(UserGuidePreferenceDTO dto);

    /**
     * 查询当前用户的导览偏好
     * @return 偏好VO，无记录时返回null
     */
    UserGuidePreferenceVO getPreference();
}
