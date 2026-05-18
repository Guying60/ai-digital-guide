package com.guying.converter;

import com.guying.pojo.dto.TourEvaluateDTO;
import com.guying.pojo.entity.UserTourHistory;
import com.guying.pojo.vo.UserTourHistoryPageVO;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserTourHistoryConverter {

    List<UserTourHistoryPageVO> toUserTourHistoryPageVOList(List<UserTourHistory> list);

    UserTourHistory toUserTourHistory(TourEvaluateDTO tourEvaluateDTO);
}
