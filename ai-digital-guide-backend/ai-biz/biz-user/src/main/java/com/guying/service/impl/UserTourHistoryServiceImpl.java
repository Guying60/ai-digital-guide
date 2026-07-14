package com.guying.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.guying.common.enums.TourStatusEnum;
import com.guying.common.result.ScrollResult;
import com.guying.context.UserContext;
import com.guying.converter.UserTourHistoryConverter;
import com.guying.attractions.service.ReviewInternalService;
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

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class UserTourHistoryServiceImpl implements UserTourHistoryService {

    @Autowired
    private UserTourHistoryMapper userTourHistoryMapper;

    @Autowired
    private UserTourHistoryConverter userTourHistoryConverter;

    @Autowired
    private ReviewInternalService reviewInternalService;

    /**
     * 获取旅游历史
     * @param userTourHistoryPageQueryDTO
     * @return
     */
    @Override
    public ScrollResult<UserTourHistoryPageVO> getTourHistory(UserTourHistoryPageQueryDTO userTourHistoryPageQueryDTO) {
        Long userId = UserContext.getUserId();
        boolean asc = "asc".equalsIgnoreCase(userTourHistoryPageQueryDTO.getSortOrder());

        LambdaQueryWrapper<UserTourHistory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserTourHistory::getUserId, userId)
                .like(StringUtils.hasText(userTourHistoryPageQueryDTO.getKeyWord()), UserTourHistory::getAttractionName, userTourHistoryPageQueryDTO.getKeyWord())
                .eq(userTourHistoryPageQueryDTO.getType() != null, UserTourHistory::getType, userTourHistoryPageQueryDTO.getType())
                .eq(StringUtils.hasText(userTourHistoryPageQueryDTO.getCity()), UserTourHistory::getCity, userTourHistoryPageQueryDTO.getCity())
                .eq(userTourHistoryPageQueryDTO.getTourStatus() != null, UserTourHistory::getTourStatus, userTourHistoryPageQueryDTO.getTourStatus());

        // 按 createTime（上次对话时间）游标分页
        if (StringUtils.hasText(userTourHistoryPageQueryDTO.getLastId())) {
            UserTourHistory cursor = userTourHistoryMapper.selectById(Long.valueOf(userTourHistoryPageQueryDTO.getLastId()));
            if (cursor != null && cursor.getCreateTime() != null) {
                LocalDateTime lastTime = cursor.getCreateTime();
                Long lastIdVal = cursor.getId();
                if (asc) {
                    queryWrapper.and(w -> w.gt(UserTourHistory::getCreateTime, lastTime)
                            .or(i -> i.eq(UserTourHistory::getCreateTime, lastTime).gt(UserTourHistory::getId, lastIdVal)));
                } else {
                    queryWrapper.and(w -> w.lt(UserTourHistory::getCreateTime, lastTime)
                            .or(i -> i.eq(UserTourHistory::getCreateTime, lastTime).lt(UserTourHistory::getId, lastIdVal)));
                }
            } else if (asc) {
                queryWrapper.gt(UserTourHistory::getId, userTourHistoryPageQueryDTO.getLastId());
            } else {
                queryWrapper.lt(UserTourHistory::getId, userTourHistoryPageQueryDTO.getLastId());
            }
        }

        if (asc) {
            queryWrapper.orderByAsc(UserTourHistory::getCreateTime).orderByAsc(UserTourHistory::getId);
        } else {
            queryWrapper.orderByDesc(UserTourHistory::getCreateTime).orderByDesc(UserTourHistory::getId);
        }
        queryWrapper.last("limit " + (userTourHistoryPageQueryDTO.getPageSize() + 1));

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
     * 结束对话：将会话状态从「进行中」改为「已结束」。
     * 已是「已评价」的记录不再降级为「已结束」。
     * @param conversationId 会话 ID
     */
    @Override
    public void endTourHistory(String conversationId) {
        Long userId = UserContext.getUserId();
        LambdaUpdateWrapper<UserTourHistory> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserTourHistory::getUserId, userId)
                .eq(UserTourHistory::getConversationId, conversationId)
                .eq(UserTourHistory::getTourStatus, TourStatusEnum.IN_PROGRESS.getCode())
                .set(UserTourHistory::getTourStatus, TourStatusEnum.ENDED.getCode());
        userTourHistoryMapper.update(updateWrapper);
    }

    /**
     * 评价旅游历史（已迁移至 tb_user_review，委托给 ReviewInternalService）。
     * 评价成功后联动更新 tour_history 状态为「已评价」。
     * @param tourEvaluateDTO
     */
    @Override
    public void evaluateTourHistory(TourEvaluateDTO tourEvaluateDTO) {
        Long userId = UserContext.getUserId();
        reviewInternalService.submitByConversationId(
                tourEvaluateDTO.getConversationId(),
                userId,
                tourEvaluateDTO.getScore(),
                tourEvaluateDTO.getFeedbackText());
        // 联动更新 tour history 状态为已评价
        LambdaUpdateWrapper<UserTourHistory> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserTourHistory::getUserId, userId)
                .eq(UserTourHistory::getConversationId, tourEvaluateDTO.getConversationId())
                .set(UserTourHistory::getTourStatus, TourStatusEnum.RATED.getCode());
        userTourHistoryMapper.update(updateWrapper);
    }
}
