package com.cruise.recommender.service;

import com.cruise.recommender.config.RabbitMQConfig;
import com.cruise.recommender.entity.Passenger;
import com.cruise.recommender.entity.Port;
import com.cruise.recommender.service.RecommendationOrchestratorService.RecommendationItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for publishing recommendations via RabbitMQ
 * 
 * Publishes recommendations to passenger devices using Pub/Sub pattern
 * Integrates with monitoring (Prometheus, ElasticSearch, Kibana)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationPublisherService {
    
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    
    /**
     * Publish recommendations to passenger's device
     * 
     * @param passenger Target passenger
     * @param port Port context
     * @param recommendations List of recommended items
     */
    public void publishRecommendations(
            Passenger passenger,
            Port port,
            List<RecommendationItem> recommendations) {
        
        log.info("Publishing {} recommendations for passenger {} at port {}", 
                recommendations.size(), passenger.getId(), port.getName());
        
        try {
            // Build recommendation message
            RecommendationMessage message = RecommendationMessage.builder()
                    .passengerId(passenger.getId())
                    .userId(passenger.getUser().getId())
                    .portId(port.getId())
                    .portCode(port.getPortCode())
                    .portName(port.getName())
                    .recommendations(recommendations)
                    .timestamp(LocalDateTime.now())
                    .source("ALS_ORCHESTRATOR")
                    .build();
            
            // Create message properties with tracking headers
            MessageProperties props = new MessageProperties();
            props.setHeader("passengerId", passenger.getId());
            props.setHeader("userId", passenger.getUser().getId());
            props.setHeader("portCode", port.getPortCode());
            props.setHeader("portName", port.getName());
            props.setHeader("recommendationCount", recommendations.size());
            props.setHeader("messageType", "recommendation");
            props.setHeader("exchange", RabbitMQConfig.RECOMMENDATION_EXCHANGE);
            props.setHeader("routingKey", RabbitMQConfig.RECOMMENDATION_UPDATE);
            props.setContentType("application/json");
            props.setTimestamp(java.util.Date.from(java.time.Instant.now()));
            
            // Convert to JSON
            String messageBody = objectMapper.writeValueAsString(message);
            
            // Create RabbitMQ message
            Message rabbitMessage = new Message(messageBody.getBytes(), props);
            
            // Publish to recommendation exchange
            rabbitTemplate.send(
                    RabbitMQConfig.RECOMMENDATION_EXCHANGE,
                    RabbitMQConfig.RECOMMENDATION_UPDATE,
                    rabbitMessage
            );
            
            log.info("Successfully published recommendations for passenger {} to RabbitMQ", 
                    passenger.getId());
            
            // Example notification message
            String notificationText = buildNotificationText(port, recommendations);
            publishNotification(passenger, port, notificationText);
            
        } catch (Exception e) {
            log.error("Error publishing recommendations for passenger {}", 
                    passenger.getId(), e);
            throw new RuntimeException("Failed to publish recommendations", e);
        }
    }
    
    /**
     * Publish notification message to passenger
     */
    private void publishNotification(Passenger passenger, Port port, String notificationText) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("passengerId", passenger.getId());
            notification.put("userId", passenger.getUser().getId());
            notification.put("portCode", port.getPortCode());
            notification.put("portName", port.getName());
            notification.put("message", notificationText);
            notification.put("timestamp", LocalDateTime.now());
            notification.put("type", "RECOMMENDATION");
            
            String notificationBody = objectMapper.writeValueAsString(notification);
            
            MessageProperties props = new MessageProperties();
            props.setHeader("passengerId", passenger.getId());
            props.setHeader("messageType", "notification");
            props.setContentType("application/json");
            
            Message message = new Message(notificationBody.getBytes(), props);
            
            rabbitTemplate.send(
                    RabbitMQConfig.NOTIFICATION_EXCHANGE,
                    RabbitMQConfig.USER_NOTIFICATION,
                    message
            );
            
            log.debug("Published notification for passenger {}", passenger.getId());
            
        } catch (Exception e) {
            log.warn("Error publishing notification: {}", e.getMessage());
        }
    }
    
    /**
     * Build human-readable notification text
     */
    private String buildNotificationText(Port port, List<RecommendationItem> recommendations) {
        if (recommendations.isEmpty()) {
            return String.format("Welcome to %s! Check out our recommendations.", port.getName());
        }
        
        RecommendationItem topRecommendation = recommendations.get(0);
        String category = topRecommendation.getCategory() != null ? 
                topRecommendation.getCategory() : "attraction";
        
        return String.format(
                "Based on your interest in %s, don't miss %s at %s!",
                category,
                topRecommendation.getItemName(),
                port.getName()
        );
    }
    
    /**
     * Recommendation Message DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RecommendationMessage {
        private Long passengerId;
        private Long userId;
        private Long portId;
        private String portCode;
        private String portName;
        private List<RecommendationItem> recommendations;
        private LocalDateTime timestamp;
        private String source;
    }
}
