package com.guying.utils;

import com.alibaba.nls.client.AccessToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AliyunNlsTokenManager {

    private static final Logger log = LoggerFactory.getLogger(AliyunNlsTokenManager.class);

    @Value("${spring.aliyun.nls.access-key}")
    private String accessKeyId;

    @Value("${spring.aliyun.nls.secret-key}")
    private String accessKeySecret;

    // 缓存当前的 token 对象
    private AccessToken accessToken;

    /**
     * 获取有效的 Token
     * 使用 synchronized 保证高并发下只有一个线程去向阿里云发起请求
     */
    public synchronized String getToken() {
        try {
            // 核心逻辑：如果 token 为空，或者有效期已经不足 1 小时 (3600秒)，则主动重新获取
            // 提前刷新可以避免在绝对过期那一瞬间，用户的语音请求因为 Token 失效而报错
            if (accessToken == null || isExpiringSoon(accessToken.getExpireTime())) {
                log.info("阿里云语音 Token 不存在或即将过期，正在向阿里云重新申请...");
                
                AccessToken newAccessToken = new AccessToken(accessKeyId, accessKeySecret);
                newAccessToken.apply(); // 发起网络请求获取新的 Token
                
                this.accessToken = newAccessToken;
                log.info("阿里云语音 Token 获取/刷新成功！有效期至时间戳: {}", accessToken.getExpireTime());
            }
            return accessToken.getToken();
            
        } catch (IOException e) {
            log.error("动态获取阿里云语音 Token 失败，请检查 AK/SK 配置或网络状态", e);
            throw new RuntimeException("智能语音服务暂时不可用", e);
        }
    }

    /**
     * 判断 Token 是否即将过期
     * @param expireTime 有效期时间戳 (秒)
     * @return boolean
     */
    private boolean isExpiringSoon(long expireTime) {
        long currentTimeSeconds = System.currentTimeMillis() / 1000;
        // 留出 3600 秒 (1小时) 的缓冲期
        return (expireTime - currentTimeSeconds) < 3600;
    }
}