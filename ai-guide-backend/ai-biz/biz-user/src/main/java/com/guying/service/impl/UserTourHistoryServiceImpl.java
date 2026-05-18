package com.guying.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guying.common.result.ScrollResult;
import com.guying.context.UserContext;
import com.guying.converter.UserTourHistoryConverter;
import com.guying.exception.ServiceException;
import com.guying.mapper.UserTourHistoryMapper;
import com.guying.pojo.dto.TourEvaluateDTO;
import com.guying.pojo.dto.UserTourHistoryPageQueryDTO;
import com.guying.pojo.entity.UserTourHistory;
import com.guying.pojo.vo.UserTourHistoryPageVO;
import com.guying.service.UserTourHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@Slf4j
public class UserTourHistoryServiceImpl implements UserTourHistoryService {

    @Autowired
    private UserTourHistoryMapper userTourHistoryMapper;

    @Autowired
    private UserTourHistoryConverter userTourHistoryConverter;

    @Override
    public ScrollResult<UserTourHistoryPageVO> getTourHistory(UserTourHistoryPageQueryDTO userTourHistoryPageQueryDTO) {

        Long userId = UserContext.getUserId();
        LambdaQueryWrapper<UserTourHistory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserTourHistory::getUserId, userId)
                .like(StringUtils.hasText(userTourHistoryPageQueryDTO.getKeyWord()), UserTourHistory::getAttractionName, userTourHistoryPageQueryDTO.getKeyWord())
                .eq(userTourHistoryPageQueryDTO.getType() != null, UserTourHistory::getType, userTourHistoryPageQueryDTO.getType())
                .lt(StringUtils.hasText(userTourHistoryPageQueryDTO.getLastId()), UserTourHistory::getId, userTourHistoryPageQueryDTO.getLastId())
                .orderByDesc(UserTourHistory::getCreateTime)
                .last("limit " + (userTourHistoryPageQueryDTO.getPageSize()+1));

        List<UserTourHistory> userTourHistoryList = userTourHistoryMapper.selectList(queryWrapper);
        boolean hasMore = userTourHistoryList.size() > userTourHistoryPageQueryDTO.getPageSize();
        if (hasMore) {
            userTourHistoryList.removeLast();
        }
        Long nextLastId = userTourHistoryList.isEmpty() ? null : userTourHistoryList.getLast().getId();

        List<UserTourHistoryPageVO> userTourHistoryPageVOList =
                userTourHistoryConverter.toUserTourHistoryPageVOList(userTourHistoryList);

        return new ScrollResult<>(userTourHistoryPageVOList, nextLastId, hasMore);
    }

    /**
     * 删除旅游历史
     * @param id
     */
    @Override
    public void deleteTourHistory(Long id) {
        try {
            userTourHistoryMapper.deleteById(id);
        } catch (Exception e) {
            log.error("删除旅游历史失败,id:{}", id, e);
            throw new ServiceException("删除旅游历史失败");
        }
    }

    /**
     * 评价旅游历史
     * @param tourEvaluateDTO
     */
    @Override
    public void evaluateTourHistory(TourEvaluateDTO tourEvaluateDTO) {
        Long userId = UserContext.getUserId();
        UserTourHistory userTourHistory = userTourHistoryConverter.toUserTourHistory(tourEvaluateDTO);
        LambdaQueryWrapper<UserTourHistory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserTourHistory::getUserId, userId)
                        .eq(UserTourHistory::getConversationId, tourEvaluateDTO.getConversationId());
        userTourHistoryMapper.update(userTourHistory, queryWrapper);

    }
}
