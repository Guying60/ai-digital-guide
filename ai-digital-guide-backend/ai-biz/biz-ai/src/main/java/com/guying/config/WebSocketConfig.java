package com.guying.config;

import com.guying.interceptor.WSJwtHandshakeInterceptor;
import com.guying.websocket.AiChatHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket // 开启 Spring WebSocket 支持
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private AiChatHandler aiChatHandler;

    @Autowired
    private WSJwtHandshakeInterceptor WSJwtHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册处理器并匹配路径，允许跨域
        registry.addHandler(aiChatHandler, "/chat")
                .addInterceptors(WSJwtHandshakeInterceptor)
                .setAllowedOrigins("*");
    }

    /**
     * 修改 WebSocket 引擎底层的缓冲区大小
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();

        container.setMaxTextMessageBufferSize(15 * 5000 * 1024);

        container.setMaxBinaryMessageBufferSize(15 * 5000 * 1024);

        // 可选：设置空闲超时时间（毫秒），例如 5 分钟无数据传输则断开连接
        // container.setMaxSessionIdleTimeout(5 * 60 * 1000L);

        return container;
    }

}