package com.guying.interceptor;

import com.guying.context.AdminContext; // 建议新建一个管理员上下文
import com.guying.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.util.concurrent.TimeUnit;

// 假设你有管理员相关的常量
import static com.guying.common.constants.RedisConstants.ADMIN_LOGIN_KEY; 
import static com.guying.common.constants.RedisConstants.LOGIN_TOKEN_EXPIRE_TIME;

@Slf4j
@Component
public class AdminJwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }
        try {
            Claims claims = jwtUtil.parseToken(token);
            String uuid = claims.get("uuid").toString();
            
            Long expireTime = redisTemplate.getExpire(ADMIN_LOGIN_KEY + uuid, TimeUnit.MILLISECONDS);
            if (expireTime == null || expireTime <= 0) {
                return false;
            }
            if (expireTime <= 1000 * 60 * 60 * 24) {
                redisTemplate.expire(ADMIN_LOGIN_KEY + uuid, LOGIN_TOKEN_EXPIRE_TIME, TimeUnit.MILLISECONDS);
            }
            
            AdminContext.setAdminId(Long.parseLong(jwtUtil.getSubject(token)));
            return true;
        } catch (Exception e) {
            log.warn("Admin token出错了");
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AdminContext.clear();
    }
}