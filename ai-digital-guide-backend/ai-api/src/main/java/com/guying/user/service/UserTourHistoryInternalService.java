package com.guying.user.service;

import com.guying.user.dto.UserChatTrendDTO;

public interface UserTourHistoryInternalService {
    UserChatTrendDTO getUserChatTrend(Long attractionId, Integer days);
}
