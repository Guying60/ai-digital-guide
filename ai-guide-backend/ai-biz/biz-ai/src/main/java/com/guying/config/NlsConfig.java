package com.guying.config;

import com.alibaba.nls.client.protocol.NlsClient;
import com.guying.utils.AliyunNlsTokenManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NlsConfig {


    @Autowired
    private AliyunNlsTokenManager tokenManager;

    // NlsClient 全局单例，和应用生命周期一致
    @Bean(destroyMethod = "shutdown")
    public NlsClient nlsClient() {
        return new NlsClient(
            "wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1",
            tokenManager.getToken()
        );
    }

}