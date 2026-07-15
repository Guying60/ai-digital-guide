package com.guying.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.guying.ai.service.ChatHistoryInternalService;
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

    @Autowired
    private ChatHistoryInternalService chatHistoryInternalService;

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
     * 若该会话为零交互（用户一次都没提问），则直接删除游览历史及关联数据，而非改状态，
     * 避免空记录残留并显示为「可评价」状态。
     *
     * 交互判定信号：
     *  1) 会话 WebSocket 仍存活：以内存中实时维护的 questionCount 为准（同步信号，无竞态）；
     *  2) 会话已断开：以 MQ 落库的 messageCount 为准，并以聊天记录表兜底——
     *     covers 断开后 MQ 尚未消费、messageCount 仍为 0 的短暂窗口
     *     （断开时聊天记录早已随流式完成落库，此时查它是安全的）。
     *
     * @param conversationId 会话 ID
     * @return true=记录已删除或已不存在（零交互/已被清理），false=记录已结束（有对话内容）
     */
    @Override
    public boolean endTourHistory(String conversationId) {
        Long userId = UserContext.getUserId();

        LambdaQueryWrapper<UserTourHistory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserTourHistory::getUserId, userId)
                .eq(UserTourHistory::getConversationId, conversationId);
        UserTourHistory history = userTourHistoryMapper.selectOne(queryWrapper);

        Integer liveQuestionCount = chatHistoryInternalService.getLiveQuestionCount(conversationId);
        // history == null 说明记录已不存在（如无效会话断开时被 cleanup 同步删除，前端列表展示的是旧数据），
        // 必须按「已删除」返回 true，否则前端会把一条已不存在的记录错误地标成「已结束」
        boolean zeroInteraction = liveQuestionCount != null
                ? liveQuestionCount == 0
                : (history == null
                    || ((history.getMessageCount() == null || history.getMessageCount() == 0)
                        && !chatHistoryInternalService.hasMessages(conversationId)));

        if (zeroInteraction) {
            log.info("结束对话时检测到零交互会话，删除游览历史 userId={}, conversationId={}, liveQuestionCount={}",
                    userId, conversationId, liveQuestionCount);
            if (history != null) {
                userTourHistoryMapper.deleteById(history.getId());
            }
            // 联动清理聊天记录与待评价记录（幂等，无数据则无操作）
            chatHistoryInternalService.deleteByConversationId(conversationId);
            reviewInternalService.deletePendingReviewByConversationId(conversationId, userId);
            // 关闭仍存活的 WS（如返回主页时被保活的会话），并标记令 cleanup 跳过持久化
            chatHistoryInternalService.endLiveSession(conversationId, true);
            return true;
        }

        // 有对话内容：状态「进行中」→「已结束」，已是「已评价」的不再降级
        LambdaUpdateWrapper<UserTourHistory> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserTourHistory::getUserId, userId)
                .eq(UserTourHistory::getConversationId, conversationId)
                .eq(UserTourHistory::getTourStatus, TourStatusEnum.IN_PROGRESS.getCode())
                .set(UserTourHistory::getTourStatus, TourStatusEnum.ENDED.getCode());
        userTourHistoryMapper.update(updateWrapper);
        // 关闭仍存活的 WS；有交互会话由 cleanup 正常完成 messageCount/待评价的落库
        chatHistoryInternalService.endLiveSession(conversationId, false);
        return false;
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
