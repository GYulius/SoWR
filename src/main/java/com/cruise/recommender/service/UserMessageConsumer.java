package com.cruise.recommender.service;

import com.cruise.recommender.config.RabbitMQConfig;
import com.cruise.recommender.repository.MessageTrackingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for users to consume messages from RabbitMQ
 * Tracks message consumption for statistics
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserMessageConsumer {
    
    private final MessageTrackingRepository messageTrackingRepository;
    private final StatisticsService statisticsService;
    
    /**
     * Consume notification messages
     */
    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
    public void consumeNotification(Message message) {
        try {
            Long consumerId = extractUserIdFromMessage(message);
            if (consumerId != null) {
                // Find and mark the corresponding message as consumed
                List<com.cruise.recommender.entity.MessageTracking> messages = 
                    messageTrackingRepository.findByConsumerId(consumerId);
                
                // Mark the most recent unconsumed message as consumed
                messages.stream()
                    .filter(m -> !m.getConsumed())
                    .findFirst()
                    .ifPresent(m -> {
                        statisticsService.markMessageConsumed(m.getId());
                        log.debug("Marked message {} as consumed by user {}", m.getId(), consumerId);
                    });
            }
            
            log.debug("Consumed notification message");
            
        } catch (Exception e) {
            log.error("Error consuming notification message", e);
        }
    }
    
    /**
     * Consume recommendation messages
     */
    @RabbitListener(queues = RabbitMQConfig.RECOMMENDATION_QUEUE)
    public void consumeRecommendation(Message message) {
        try {
            Long consumerId = extractUserIdFromMessage(message);
            if (consumerId != null) {
                log.debug("User {} consumed recommendation message", consumerId);
            }
        } catch (Exception e) {
            log.error("Error consuming recommendation message", e);
        }
    }
    
    private Long extractUserIdFromMessage(Message message) {
        try {
            Object userId = message.getMessageProperties().getHeaders().get("consumerId");
            if (userId == null) {
                userId = message.getMessageProperties().getHeaders().get("userId");
            }
            
            if (userId instanceof Long) {
                return (Long) userId;
            } else if (userId instanceof Number) {
                return ((Number) userId).longValue();
            } else if (userId instanceof String) {
                return Long.parseLong((String) userId);
            }
        } catch (Exception e) {
            log.debug("Could not extract user ID from message: {}", e.getMessage());
        }
        return null;
    }
}

