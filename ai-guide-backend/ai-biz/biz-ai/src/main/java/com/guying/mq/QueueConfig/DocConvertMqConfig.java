package com.guying.mq.QueueConfig;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.guying.common.constants.MqConstants.*;

@Configuration
public class DocConvertMqConfig {

    // 声明死信交换机
    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    // 声明死信队列 (2026年最佳实践：使用 quorum 仲裁队列)
    @Bean
    public Queue dlqQueue() {
        return QueueBuilder.durable(DLQ_QUEUE)
                .quorum() // 启用仲裁队列替代传统持久化，提供极高的高可用性
                .build();
    }

    // 将死信队列绑定到死信交换机
    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(dlqQueue()).to(dlxExchange()).with(DLQ_ROUTING_KEY);
    }


    @Bean
    public Queue requestQueue() {
        return QueueBuilder.durable(REQUEST_QUEUE)
                .quorum() 
                // 【核心参数】给正常队列指定它的“墓地”在哪里
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(DLQ_ROUTING_KEY)
                // 可选：设置消息的存活时间，例如 10 分钟没有被 Python 消费完，自动进入死信
                // .ttl(600000) 
                .build();
    }
}