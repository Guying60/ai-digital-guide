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
public class AdminSaveFaqQueueConfig {

    @Bean
    public DirectExchange adminSaveFqaDirectExchange() {
        return new DirectExchange(MqConstants.ADMIN_SAVE_FAQ_DIRECT);
    }

    @Bean
    public Queue adminSaveFqaQueue() {
        return new Queue(MqConstants.ADMIN_SAVE_FAQ_QUEUE);
    }

    @Bean
    public Binding adminSaveFqaBinding() {
        return new Binding(
                MqConstants.ADMIN_SAVE_FAQ_QUEUE,
                Binding.DestinationType.QUEUE,
                MqConstants.ADMIN_SAVE_FAQ_DIRECT,
                MqConstants.ADMIN_SAVE_FAQ_ROUTING_KEY,
                null
        );
    }
}