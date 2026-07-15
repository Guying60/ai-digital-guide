package com.guying.user.service;

import java.util.Map;

public interface UserInternalService {
    Map<String, String> getUserInfo(Long userId);

    /** 同步创建游览历史记录（绕过 MQ，避免异步竞态导致秒退脏数据残留） */
    void createTourHistory(Long userId, Long attractionId, String conversationId, Integer tourStatus);

    /** 同步删除游览历史记录（无效会话清理，绕过 MQ 保证即时生效） */
    void deleteTourHistory(Long userId, String conversationId);
}
