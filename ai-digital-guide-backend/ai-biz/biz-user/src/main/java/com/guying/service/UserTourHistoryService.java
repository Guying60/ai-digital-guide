package com.guying.service;

import com.guying.common.result.ScrollResult;
import com.guying.pojo.dto.TourEvaluateDTO;
import com.guying.pojo.dto.UserTourHistoryPageQueryDTO;
import jakarta.validation.Valid;

public interface UserTourHistoryService {
    ScrollResult getTourHistory(UserTourHistoryPageQueryDTO userTourHistoryPageQueryDTO);

    void deleteTourHistory(Long id);

    /**
     * 结束对话：将会话状态从「进行中」改为「已结束」。
     * 若该会话为零交互（无任何聊天记录），则直接删除游览历史及相关数据，而非改状态。
     * @param conversationId 会话 ID
     * @return true=记录已删除（零交互），false=记录已结束（有对话内容）
     */
    boolean endTourHistory(String conversationId);

    void evaluateTourHistory(TourEvaluateDTO tourEvaluateDTO);
}
