package com.guying.interceptor;

import com.guying.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.guying.common.constants.RedisConstants.USER_LOGIN_KEY;
import static com.guying.common.constants.RedisConstants.LOGIN_TOKEN_EXPIRE_TIME;

@Slf4j
@Component
public class WSJwtHandshakeInterceptor implements HandshakeInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private StringRedisTemplate redisTemplate;


    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest servletRequest){
            // 1. 优先尝试从 Header 中获取 Authorization
            String authHeader = request.getHeaders().getFirst("Authorization");
            String token = authHeader != null && authHeader.startsWith("Bearer ")
                    ? authHeader.substring(7) : null;

            // ==========================================
            // 【新增逻辑】2. 如果 Header 里没有，尝试从 URL Query 参数中获取
            // 方便前端纯 HTML WebSocket 测试使用
            // ==========================================
            if (!StringUtils.hasText(token)) {
                token = servletRequest.getServletRequest().getParameter("token");
            }

            if (!StringUtils.hasText(token)){
                log.warn("websocket token is empty");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            try {
                Claims claims = jwtUtil.parseToken(token);
                String uuid = claims.get("uuid").toString();

                Long expireTime = redisTemplate.getExpire(USER_LOGIN_KEY + uuid, TimeUnit.MILLISECONDS);
                if (expireTime == null || expireTime <= 0) {
                    log.warn("WebSocket 握手拒绝：Token 已在 Redis 过期");
                    response.setStatusCode(HttpStatus.UNAUTHORIZED);
                    return false;
                }
                if (expireTime <= 1000 * 60 * 60 * 24) {
                    redisTemplate.expire(USER_LOGIN_KEY + uuid, LOGIN_TOKEN_EXPIRE_TIME, TimeUnit.MILLISECONDS);
                }
                Long userId = Long.parseLong(claims.getSubject());
                //存放用户ID
                attributes.put("userId", userId);
                //存放景点ID
                String attractionId = servletRequest.getServletRequest().getParameter("attractionId");
                if (!StringUtils.hasText(attractionId) || "null".equals(attractionId)) {
                    log.warn("WebSocket 握手拒绝：未选择景点");
                    response.setStatusCode(HttpStatus.BAD_REQUEST);
                    return false;
                }
                attributes.put("attractionId", Long.parseLong(attractionId));
                // 可选：继续对话时携带原会话 ID（归属校验在 AiChatHandler 中进行）
                String conversationId = servletRequest.getServletRequest().getParameter("conversationId");
                if (StringUtils.hasText(conversationId) && !"null".equals(conversationId)) {
                    attributes.put("conversationId", conversationId);
                }
                return true;
            } catch (Exception e) { // 把这里的 NumberFormatException 改成了宽泛的 Exception，防止 jwtUtil 解析失败抛出其他异常导致崩溃
                log.warn("WebSocket 握手拒绝：Token 解析失败", e);
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
        }
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, @Nullable Exception exception) {

    }
}