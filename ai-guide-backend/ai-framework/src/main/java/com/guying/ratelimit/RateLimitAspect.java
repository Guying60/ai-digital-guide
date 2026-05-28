package com.guying.ratelimit;

import com.guying.context.UserContext;
import com.guying.exception.ServiceException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RateLimitAspect {

    @Autowired
    private RateLimiterUtil rateLimiterUtil;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        // 取当前登录用户ID，按用户单独限流
        Long userId = UserContext.getUserId();
        String key = "rate:limit:" + pjp.getSignature().getName() + ":" + userId;

        if (!rateLimiterUtil.tryAcquire(key, rateLimit.rate(), rateLimit.interval())) {
            throw new ServiceException(429, rateLimit.message());
        }
        return pjp.proceed();
    }
}