package com.cruise.recommender.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ Configuration for message queuing
 * Handles AIS data processing, notifications, and real-time updates
 */
@Configuration
public class RabbitMQConfig {
    
    // Queue names
    public static final String AIS_DATA_QUEUE = "ais.data.queue";
    public static final String NOTIFICATION_QUEUE = "notification.queue";
    public static final String RECOMMENDATION_QUEUE = "recommendation.queue";
    public static final String ANALYTICS_QUEUE = "analytics.queue";
    
    // Exchange names
    public static final String AIS_EXCHANGE = "ais.exchange";
    public static final String NOTIFICATION_EXCHANGE = "notification.exchange";
    public static final String RECOMMENDATION_EXCHANGE = "recommendation.exchange";
    
    // Routing keys
    public static final String SHIP_POSITION_UPDATE = "ship.position.update";
    public static final String SHIP_APPROACHING_PORT = "ship.approaching.port";
    public static final String USER_NOTIFICATION = "user.notification";
    public static final String RECOMMENDATION_UPDATE = "recommendation.update";
    
    /**
     * AIS Data Exchange
     */
    @Bean
    public TopicExchange aisExchange() {
        return new TopicExchange(AIS_EXCHANGE);
    }
    
    /**
     * AIS Data Queue
     */
    @Bean
    public Queue aisDataQueue() {
        return QueueBuilder.durable(AIS_DATA_QUEUE)
                .withArgument("x-message-ttl", 3600000) // 1 hour TTL
                .build();
    }
    
    /**
     * Notification Exchange
     */
    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(NOTIFICATION_EXCHANGE);
    }
    
    /**
     * Notification Queue
     */
    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                .withArgument("x-message-ttl", 86400000) // 24 hours TTL
                .build();
    }
    
    /**
     * Recommendation Exchange
     */
    @Bean
    public TopicExchange recommendationExchange() {
        return new TopicExchange(RECOMMENDATION_EXCHANGE);
    }
    
    /**
     * Recommendation Queue
     */
    @Bean
    public Queue recommendationQueue() {
        return QueueBuilder.durable(RECOMMENDATION_QUEUE).build();
    }
    
    /**
     * Analytics Queue
     */
    @Bean
    public Queue analyticsQueue() {
        return QueueBuilder.durable(ANALYTICS_QUEUE).build();
    }
    
    /**
     * Bindings
     */
    @Bean
    public Binding aisDataBinding() {
        return BindingBuilder
                .bind(aisDataQueue())
                .to(aisExchange())
                .with("ais.data.*");
    }
    
    @Bean
    public Binding shipPositionBinding() {
        return BindingBuilder
                .bind(aisDataQueue())
                .to(aisExchange())
                .with(SHIP_POSITION_UPDATE);
    }
    
    @Bean
    public Binding shipApproachingPortBinding() {
        return BindingBuilder
                .bind(aisDataQueue())
                .to(aisExchange())
                .with(SHIP_APPROACHING_PORT);
    }
    
    @Bean
    public Binding notificationBinding() {
        return BindingBuilder
                .bind(notificationQueue())
                .to(notificationExchange())
                .with(USER_NOTIFICATION);
    }
    
    @Bean
    public Binding recommendationBinding() {
        return BindingBuilder
                .bind(recommendationQueue())
                .to(recommendationExchange())
                .with(RECOMMENDATION_UPDATE);
    }
    
    /**
     * Message converter for JSON
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
    
    /**
     * RabbitTemplate with JSON converter
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
