package com.seniorinsight.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    TopicExchange eventosExchange(@Value("${notification.rabbitmq.exchange}") String exchange) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    DirectExchange deadLetterExchange(@Value("${notification.rabbitmq.dead-letter-exchange}") String exchange) {
        return new DirectExchange(exchange, true, false);
    }

    @Bean
    Queue notificationQueue(
            @Value("${notification.rabbitmq.queue}") String queue,
            @Value("${notification.rabbitmq.dead-letter-exchange}") String deadLetterExchange,
            @Value("${notification.rabbitmq.dead-letter-routing-key}") String deadLetterRoutingKey) {
        return QueueBuilder.durable(queue)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey(deadLetterRoutingKey)
                .build();
    }

    @Bean
    Queue notificationDeadLetterQueue(@Value("${notification.rabbitmq.dead-letter-queue}") String queue) {
        return QueueBuilder.durable(queue).build();
    }

    @Bean
    Binding notificationBinding(
            @Qualifier("notificationQueue") Queue notificationQueue,
            TopicExchange eventosExchange,
            @Value("${notification.rabbitmq.routing-key}") String routingKey) {
        return BindingBuilder.bind(notificationQueue).to(eventosExchange).with(routingKey);
    }

    @Bean
    Binding notificationDeadLetterBinding(
            @Qualifier("notificationDeadLetterQueue") Queue notificationDeadLetterQueue,
            DirectExchange deadLetterExchange,
            @Value("${notification.rabbitmq.dead-letter-routing-key}") String routingKey) {
        return BindingBuilder.bind(notificationDeadLetterQueue).to(deadLetterExchange).with(routingKey);
    }
}
