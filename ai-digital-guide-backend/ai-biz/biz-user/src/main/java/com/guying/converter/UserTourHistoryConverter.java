package com.guying.converter;

import com.guying.pojo.entity.UserTourHistory;
import com.guying.pojo.vo.UserTourHistoryPageVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserTourHistoryConverter {

    @Mapping(source = "createTime", target = "lastChatTime")
    UserTourHistoryPageVO toUserTourHistoryPageVO(UserTourHistory entity);

    List<UserTourHistoryPageVO> toUserTourHistoryPageVOList(List<UserTourHistory> list);
}
