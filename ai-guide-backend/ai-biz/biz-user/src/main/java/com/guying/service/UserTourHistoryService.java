package com.guying.service;

import com.guying.common.result.ScrollResult;
import com.guying.pojo.dto.TourEvaluateDTO;
import com.guying.pojo.dto.UserTourHistoryPageQueryDTO;
import jakarta.validation.Valid;

public interface UserTourHistoryService {
    ScrollResult getTourHistory(UserTourHistoryPageQueryDTO userTourHistoryPageQueryDTO);

    void deleteTourHistory(Long id);

    void evaluateTourHistory(TourEvaluateDTO tourEvaluateDTO);
}
