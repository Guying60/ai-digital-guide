package com.guying.service;

import com.guying.pojo.vo.RoutePlanVO;
import com.guying.websocket.session.ChatSessionContext;

/**
 * 个性化路线推荐（灵动岛数轴）。路线跨 WS 重连保留，直到明确结束游览或缓存过期。
 */
public interface RouteRecommendationService {

    /** 异步生成路线、缓存并通过 WS 推送 routeTimeline（失败推 routeError）。不阻塞 WS 读线程。 */
    void generateAndPush(ChatSessionContext ctx);

    /** 恢复当前激活路线（REST 重连/刷新用），无则返回 null。 */
    RoutePlanVO getCurrentPlan(Long userId, Long attractionId, String conversationId);

    /** 标记某节点已到达：置 ARRIVED、前移 CURRENT 指针、持久化，返回更新后的路线；无激活路线返回 null。 */
    RoutePlanVO markArrived(ChatSessionContext ctx, int stopIndex);

    /** 清除当前激活路线（明确关闭或结束游览）。 */
    void clearPlan(Long userId, Long attractionId, String conversationId);
}
