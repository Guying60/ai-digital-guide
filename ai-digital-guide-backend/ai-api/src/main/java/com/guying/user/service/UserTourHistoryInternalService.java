package com.guying.user.service;

import com.guying.user.dto.UserChatTrendDTO;
import com.guying.user.dto.UserSatisfactionTrendDTO;

import java.util.List;

public interface UserTourHistoryInternalService {
    UserChatTrendDTO getUserChatTrend(Long attractionId, Integer days);

    UserSatisfactionTrendDTO getUserSatisfactionTrend(Long attractionId, Integer days);

    List<String> getFeedbackText(Long attractionId, Integer days);
}
