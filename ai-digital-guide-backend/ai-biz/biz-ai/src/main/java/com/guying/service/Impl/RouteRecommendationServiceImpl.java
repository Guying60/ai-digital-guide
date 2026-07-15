package com.guying.service.Impl;

import com.guying.amap.AmapClient;
import com.guying.amap.AmapPoi;
import com.guying.attractions.dto.AttractionDTO;
import com.guying.ai.service.RouteRecommendationInternalService;
import com.guying.attractions.service.UserAttractionsInternalService;
import com.guying.common.constants.RedisConstants;
import com.guying.common.enums.GenderEnum;
import com.guying.common.enums.RouteStopStatus;
import com.guying.pojo.dto.RoutePlanDraft;
import com.guying.pojo.dto.RouteStopDraft;
import com.guying.pojo.vo.RoutePlanVO;
import com.guying.pojo.vo.RouteStopVO;
import com.guying.prompt.RoutePromptConstants;
import com.guying.rag.VectorSearchService;
import com.guying.service.RouteRecommendationService;
import com.guying.websocket.protocol.WsMessageSender;
import com.guying.websocket.session.ChatSessionContext;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Service
@Slf4j
public class RouteRecommendationServiceImpl implements RouteRecommendationService, RouteRecommendationInternalService {

    @Autowired
    @Qualifier("expertChatClient")
    private ChatClient expertChatClient;

    @Autowired
    private VectorSearchService vectorSearchService;

    @Autowired
    private AmapClient amapClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private WsMessageSender sender;

    @Autowired
    private UserAttractionsInternalService userAttractionsInternalService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 专用线程池：路线生成是短时无状态 LLM+HTTP 调用，绝不借用 ctx 的 TTS executor（会被打断时 shutdownNow）。 */
    private final ExecutorService routeExecutor = Executors.newFixedThreadPool(4);
    private final Object[] routeLocks = new Object[64];

    private static final DefaultRedisScript<Long> SAVE_PLAN_IF_ACTIVE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "redis.call('set', KEYS[2], ARGV[2], 'EX', ARGV[3]); return 1 "
                    + "else return 0 end",
            Long.class);

    public RouteRecommendationServiceImpl() {
        for (int i = 0; i < routeLocks.length; i++) {
            routeLocks[i] = new Object();
        }
    }

    @PreDestroy
    public void shutdown() {
        routeExecutor.shutdown();
    }

    @Override
    public void generateAndPush(ChatSessionContext ctx) {
        if (ctx.isEnding()) {
            return;
        }
        String generationToken = UUID.randomUUID().toString();
        stringRedisTemplate.opsForValue().set(
                generationKey(ctx.getAttractionId(), ctx.getUserId(), ctx.conversationId()),
                generationToken,
                RedisConstants.ROUTE_PLAN_EXPIRE_TIME, TimeUnit.HOURS);
        routeExecutor.submit(() -> doGenerate(ctx, generationToken));
    }

    private void doGenerate(ChatSessionContext ctx, String generationToken) {
        Long userId = ctx.getUserId();
        Long attractionId = ctx.getAttractionId();
        String conversationId = ctx.conversationId();
        try {
            // 1. 个性化字段
            String infoKey = RedisConstants.USER_INFO_KEY + userId;
            String genderDesc = resolveGender((String) stringRedisTemplate.opsForHash().get(infoKey, "gender"));
            String age = orDefault((String) stringRedisTemplate.opsForHash().get(infoKey, "age"), "未知");
            String interests = orDefault((String) stringRedisTemplate.opsForHash().get(infoKey, "interests"), "未知");
            String travelPurpose = orDefault((String) stringRedisTemplate.opsForHash().get(infoKey, "travelPurpose"), "未知");
            String guideDepth = orDefault((String) stringRedisTemplate.opsForHash().get(infoKey, "guideDepth"), "未知");

            // 2. 检索景点路线/景点资料
            // 注：interests 取自用户偏好缓存，历史 bug 曾导致该字段回落"未知"使 query 劣化、
            // 相似度跌破 0.7 阈值而误报"暂无资料"。这里在默认阈值召回为空时低阈值兜底重召回，
            // 避免个性化字段质量波动直接中断路线生成。
            String query = "推荐游览路线 参观顺序 必看景点 " + interests + " " + travelPurpose;
            List<Document> docs = vectorSearchService.searchDocByAttraction(query, attractionId, 8);
            if (docs == null || docs.isEmpty()) {
                log.warn("路线检索默认阈值(0.7)召回为空，低阈值兜底重召回 userId={} attractionId={} interests={}",
                        userId, attractionId, interests);
                docs = vectorSearchService.searchDocByAttraction(query, attractionId, 8, 0.3);
            }
            if (docs == null || docs.isEmpty()) {
                sender.sendJson(ctx, "routeError", "暂无可生成路线的景点资料");
                return;
            }
            String context = docs.stream().map(Document::getText).collect(Collectors.joining("\n\n"));

            // 3. 渲染提示词（先渲染占位符，再追加 JSON 格式说明，避免 JSON 花括号被模板误解析）
            BeanOutputConverter<RoutePlanDraft> converter = new BeanOutputConverter<>(RoutePlanDraft.class);
            Map<String, Object> vars = new HashMap<>();
            vars.put("gender", genderDesc);
            vars.put("age", age);
            vars.put("interests", interests);
            vars.put("travelPurpose", travelPurpose);
            vars.put("guideDepth", guideDepth);
            vars.put("context", context);
            String rendered = new PromptTemplate(RoutePromptConstants.ROUTE_PLAN_PROMPT).render(vars);
            String prompt = rendered + "\n" + converter.getFormat();

            // 4. LLM 结构化输出
            String json = expertChatClient.prompt().user(prompt).call().content();
            RoutePlanDraft draft = converter.convert(json);
            if (draft == null || draft.getStops() == null || draft.getStops().isEmpty()) {
                sender.sendJson(ctx, "routeError", "路线生成失败，请稍后再试");
                return;
            }

            // 5. 组装 VO + 高德锚点 + 逐站坐标预解析（任一失败均降级，不阻断）
            RoutePlanVO plan = buildPlan(draft, attractionId, conversationId);

            // 6. 缓存 + 推送
            synchronized (routeLock(attractionId, userId, conversationId)) {
                if (ctx.isEnding()) {
                    return;
                }
                Long saved = stringRedisTemplate.execute(
                        SAVE_PLAN_IF_ACTIVE,
                        List.of(generationKey(attractionId, userId, conversationId),
                                planKey(attractionId, userId, conversationId)),
                        generationToken,
                        objectMapper.writeValueAsString(plan),
                        String.valueOf(TimeUnit.HOURS.toSeconds(RedisConstants.ROUTE_PLAN_EXPIRE_TIME)));
                if (Long.valueOf(1L).equals(saved)) {
                    sender.sendJson(ctx, "routeTimeline", plan);
                } else {
                    log.info("路线生成结果已失效，跳过写入 userId={} attractionId={} conversationId={}",
                            userId, attractionId, conversationId);
                }
            }
        } catch (Exception e) {
            log.error("路线生成失败 userId={} attractionId={}", userId, attractionId, e);
            sender.sendJson(ctx, "routeError", "路线生成失败，请稍后再试");
        }
    }

    private RoutePlanVO buildPlan(RoutePlanDraft draft, Long attractionId, String conversationId) {
        RoutePlanVO plan = new RoutePlanVO();
        plan.setRouteId(UUID.randomUUID().toString());
        plan.setConversationId(conversationId);
        plan.setTitle(draft.getTitle());
        plan.setSummary(draft.getSummary());
        plan.setAttractionId(attractionId);
        plan.setGeneratedAt(System.currentTimeMillis());

        // 高德锚点（失败则降级为空锚点）
        AmapPoi anchor = resolveAnchorQuietly(attractionId);
        if (anchor != null) {
            plan.setCityName(anchor.cityName());
            plan.setAdcode(anchor.adcode());
            plan.setCenterLng(anchor.longitude());
            plan.setCenterLat(anchor.latitude());
        }

        List<RouteStopDraft> draftStops = draft.getStops();
        int n = Math.min(draftStops.size(), 10);
        List<RouteStopVO> stops = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            RouteStopDraft d = draftStops.get(i);
            RouteStopVO stop = new RouteStopVO();
            stop.setStopIndex(i);
            stop.setName(d.getName());
            stop.setSearchKeyword(d.getSearchKeyword());
            stop.setRecommendReason(d.getRecommendReason());
            stop.setEstimatedMinutes(d.getEstimatedMinutes());
            stop.setStatus(i == 0 ? RouteStopStatus.CURRENT : RouteStopStatus.UPCOMING);

            String keyword = (d.getSearchKeyword() != null && !d.getSearchKeyword().isBlank())
                    ? d.getSearchKeyword() : d.getName();
            AmapPoi poi = resolveLandmarkQuietly(keyword, anchor);
            if (poi != null && poi.longitude() != null && poi.latitude() != null) {
                stop.setLongitude(poi.longitude());
                stop.setLatitude(poi.latitude());
                stop.setAddress(poi.address());
                stop.setPoiId(poi.poiId());
                stop.setResolved(true);
            } else {
                stop.setResolved(false);
            }
            stops.add(stop);
        }
        plan.setStops(stops);
        return plan;
    }

    private AmapPoi resolveAnchorQuietly(Long attractionId) {
        try {
            AttractionDTO attraction = userAttractionsInternalService.getAttraction(attractionId);
            if (attraction == null) return null;
            return amapClient.resolveAttractionAnchor(attractionId, attraction.getAttractionName());
        } catch (Exception e) {
            log.warn("高德锚点解析失败 attractionId={}: {}", attractionId, e.getMessage());
            return null;
        }
    }

    private AmapPoi resolveLandmarkQuietly(String keyword, AmapPoi anchor) {
        try {
            return amapClient.resolveLandmark(keyword, anchor);
        } catch (Exception e) {
            log.warn("高德地标解析失败 keyword={}: {}", keyword, e.getMessage());
            return null;
        }
    }

    @Override
    public RoutePlanVO getCurrentPlan(Long userId, Long attractionId, String conversationId) {
        String json = stringRedisTemplate.opsForValue().get(planKey(attractionId, userId, conversationId));
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, RoutePlanVO.class);
        } catch (Exception e) {
            log.warn("路线缓存反序列化失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public RoutePlanVO markArrived(ChatSessionContext ctx, int stopIndex) {
        synchronized (routeLock(ctx.getAttractionId(), ctx.getUserId(), ctx.conversationId())) {
            if (ctx.isEnding()) {
                return null;
            }
            String key = planKey(ctx.getAttractionId(), ctx.getUserId(), ctx.conversationId());
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null) return null;

            RoutePlanVO plan;
            try {
                plan = objectMapper.readValue(json, RoutePlanVO.class);
            } catch (Exception e) {
                log.warn("路线缓存反序列化失败: {}", e.getMessage());
                return null;
            }
            List<RouteStopVO> stops = plan.getStops();
            if (stops == null || stopIndex < 0 || stopIndex >= stops.size()) {
                return plan;
            }

            stops.get(stopIndex).setStatus(RouteStopStatus.ARRIVED);
            plan.setRevision(plan.getRevision() + 1);
            boolean currentAssigned = false;
            for (RouteStopVO s : stops) {
                if (s.getStatus() == RouteStopStatus.ARRIVED) continue;
                if (!currentAssigned) {
                    s.setStatus(RouteStopStatus.CURRENT);
                    currentAssigned = true;
                } else {
                    s.setStatus(RouteStopStatus.UPCOMING);
                }
            }

            stringRedisTemplate.opsForValue().set(
                    key, objectMapper.writeValueAsString(plan),
                    RedisConstants.ROUTE_PLAN_EXPIRE_TIME, TimeUnit.HOURS);
            return plan;
        }
    }

    @Override
    public void clearPlan(Long userId, Long attractionId, String conversationId) {
        if (conversationId == null) {
            return;
        }
        synchronized (routeLock(attractionId, userId, conversationId)) {
            stringRedisTemplate.delete(List.of(
                    planKey(attractionId, userId, conversationId),
                    generationKey(attractionId, userId, conversationId)));
        }
    }

    // ---- helpers ----

    private Object routeLock(Long attractionId, Long userId, String conversationId) {
        int hash = (attractionId + ":" + userId + ":" + conversationId).hashCode();
        return routeLocks[(hash & Integer.MAX_VALUE) % routeLocks.length];
    }

    private String planKey(Long attractionId, Long userId, String conversationId) {
        return RedisConstants.ROUTE_PLAN_KEY + attractionId + ":" + userId + ":" + conversationId;
    }

    private String generationKey(Long attractionId, Long userId, String conversationId) {
        return planKey(attractionId, userId, conversationId) + ":generation";
    }

    private String resolveGender(String genderCode) {
        if (genderCode == null || genderCode.isBlank()) return "未知";
        try {
            return GenderEnum.fromCode(Integer.parseInt(genderCode)).getDesc();
        } catch (NumberFormatException e) {
            return "未知";
        }
    }

    private String orDefault(String v, String def) {
        return (v == null || v.isBlank()) ? def : v;
    }
}
