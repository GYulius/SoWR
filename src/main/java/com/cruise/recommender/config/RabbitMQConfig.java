package com.cruise.recommender.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
    public static final String SOCIAL_MEDIA_QUEUE = "social.media.queue";
    public static final String KNOWLEDGE_GRAPH_QUEUE = "knowledge.graph.queue";
    
    // Exchange names
    public static final String AIS_EXCHANGE = "ais.exchange";
    public static final String NOTIFICATION_EXCHANGE = "notification.exchange";
    public static final String RECOMMENDATION_EXCHANGE = "recommendation.exchange";
    public static final String SOCIAL_MEDIA_EXCHANGE = "social.media.exchange";
    public static final String KNOWLEDGE_GRAPH_EXCHANGE = "knowledge.graph.exchange";
    
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
     * Social Media Exchange
     */
    @Bean
    public TopicExchange socialMediaExchange() {
        return new TopicExchange(SOCIAL_MEDIA_EXCHANGE);
    }
    
    /**
     * Social Media Queue
     */
    @Bean
    public Queue socialMediaQueue() {
        return QueueBuilder.durable(SOCIAL_MEDIA_QUEUE)
                .withArgument("x-message-ttl", 86400000) // 24 hours TTL
                .build();
    }
    
    /**
     * Knowledge Graph Exchange
     */
    @Bean
    public TopicExchange knowledgeGraphExchange() {
        return new TopicExchange(KNOWLEDGE_GRAPH_EXCHANGE);
    }
    
    /**
     * Knowledge Graph Queue
     */
    @Bean
    public Queue knowledgeGraphQueue() {
        return QueueBuilder.durable(KNOWLEDGE_GRAPH_QUEUE).build();
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
    
    @Bean
    public Binding socialMediaBinding() {
        return BindingBuilder
                .bind(socialMediaQueue())
                .to(socialMediaExchange())
                .with("social.media.*");
    }
    
    @Bean
    public Binding knowledgeGraphBinding() {
        return BindingBuilder
                .bind(knowledgeGraphQueue())
                .to(knowledgeGraphExchange())
                .with("knowledge.graph.*");
    }
    
    /**
     * Message converter for JSON
     * Configured to handle Java 8 time types (LocalDateTime, etc.)
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        return converter;
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
