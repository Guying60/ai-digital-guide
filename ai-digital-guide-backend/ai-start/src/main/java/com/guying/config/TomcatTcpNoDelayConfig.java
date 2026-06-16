package com.guying.config;

import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.boot.tomcat.TomcatWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ★ 显式设置 Tomcat NIO 连接器的 TCP_NODELAY 和最小写缓冲区。
 * 视频帧是大量连续小包（2-10KB），必须禁用 Nagle 避免批量延迟。
 * Tomcat 默认 tcpNoDelay=true，显式确保并最小化写缓冲。
 */
@Configuration
public class TomcatTcpNoDelayConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatWebServerFactory> tomcatNoDelayCustomizer() {
        return factory -> factory.addConnectorCustomizers(connector -> {
            if (connector.getProtocolHandler() instanceof Http11NioProtocol) {
                // 禁用 Nagle 算法，每个 write 立即发送到 TCP 层
                connector.setProperty("tcpNoDelay", "true");
                // 最小化 socket 写缓冲区（默认 ~8KB → 4KB），小帧不等待合并
                connector.setProperty("socket.appWriteBufSize", "4096");
                connector.setProperty("socket.appReadBufSize", "4096");
                // 控制 TCP 发送缓冲区
                connector.setProperty("socket.txBufSize", "4096");
            }
        });
    }
}
