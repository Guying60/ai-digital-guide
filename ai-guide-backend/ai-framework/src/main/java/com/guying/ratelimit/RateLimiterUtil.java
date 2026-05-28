package com.guying.ratelimit;

import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RateLimiterUtil {

    @Autowired
    private RedissonClient redissonClient;

    /**
     * @param key      限流key（比如 "ai:limit:userId"）
     * @param rate     时间窗口内允许的请求数
     * @param interval 时间窗口（秒）
     */
    public boolean tryAcquire(String key, int rate, int interval) {
        RRateLimiter limiter = redissonClient.getRateLimiter(key);
        // 新 API，用 Duration 替代 RateIntervalUnit
        limiter.trySetRate(RateType.OVERALL, rate, Duration.ofSeconds(interval));
        return limiter.tryAcquire(1);
    }
}