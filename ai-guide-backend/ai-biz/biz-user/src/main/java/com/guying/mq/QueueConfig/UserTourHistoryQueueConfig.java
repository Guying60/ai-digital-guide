package com.guying.mq.QueueConfig;

import com.guying.common.constants.MqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.guying.common.constants.MqConstants.*;

@Configuration
@ConditionalOnProperty(prefix = "spring.rabbitmq.listener.simple.retry", name = "enabled", havingValue = "true")
public class UserTourHistoryQueueConfig {

    @Bean
    public DirectExchange directUserTourHistoryExchange() {
        return new DirectExchange(USER_TOUR_HISTORY_DIRECT);
    }


    @Bean
    public Queue userTourHistoryQueue() {
        return new Queue(USER_TOUR_HISTORY_QUEUE);
    }



    @Bean
    public Binding bindingUserTourHistory() {
        return new Binding(USER_TOUR_HISTORY_QUEUE, Binding.DestinationType.QUEUE, USER_TOUR_HISTORY_DIRECT, USER_TOUR_HISTORY_ROUTING_KEY, null);
    }
}
