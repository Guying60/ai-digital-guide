package com.guying.mq.QueueConfig;


import com.guying.common.constants.MqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "spring.rabbitmq.listener.simple.retry", name = "enabled", havingValue = "true")
public class DocumentDeleteQueueConfig {


    @Bean
    public DirectExchange directExchange() {
        return new DirectExchange(MqConstants.DELETE_VECTOR_DIRECT);
    }

    @Bean
    public Queue queue() {
        return new Queue(MqConstants.DELETE_VECTOR_QUEUE);
    }

    @Bean
    public Binding binding() {
        return new Binding(MqConstants.DELETE_VECTOR_QUEUE, Binding.DestinationType.QUEUE, MqConstants.DELETE_VECTOR_DIRECT, MqConstants.DELETE_VECTOR_ROUTING_KEY, null);
    }

}
