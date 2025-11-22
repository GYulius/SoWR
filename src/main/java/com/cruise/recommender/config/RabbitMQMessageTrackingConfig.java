package com.cruise.recommender.config;

import com.cruise.recommender.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Configuration for tracking RabbitMQ messages from ports (producers) to users (consumers)
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class RabbitMQMessageTrackingConfig {
    
    private final StatisticsService statisticsService;
    
    /**
     * Message post-processor to track messages sent from ports
     */
    @Bean
    public MessagePostProcessor messageTrackingPostProcessor() {
        return new MessagePostProcessor() {
            @Override
            public Message postProcessMessage(Message message) {
                try {
                    MessageProperties props = message.getMessageProperties();
                    Map<String, Object> headers = props.getHeaders();
                    
                    // Extract producer (port) and consumer (user) IDs from headers
                    Long producerId = extractLongHeader(headers, "producerId", "portId");
                    Long consumerId = extractLongHeader(headers, "consumerId", "userId");
                    
                    if (producerId != null && consumerId != null) {
                        String queueName = props.getReceivedRoutingKey() != null ? 
                            props.getReceivedRoutingKey() : 
                            (String) headers.getOrDefault("queue", "unknown");
                        String exchangeName = props.getReceivedExchange() != null ? 
                            props.getReceivedExchange() : 
                            (String) headers.getOrDefault("exchange", "unknown");
                        String routingKey = props.getReceivedRoutingKey() != null ? 
                            props.getReceivedRoutingKey() : 
                            (String) headers.getOrDefault("routingKey", "");
                        String messageType = (String) headers.getOrDefault("messageType", "unknown");
                        String messageBody = new String(message.getBody());
                        
                        // Record the message
                        statisticsService.recordMessage(
                            producerId,
                            consumerId,
                            queueName,
                            exchangeName,
                            routingKey,
                            messageBody,
                            messageType
                        );
                        
                        log.debug("Tracked message: producer={}, consumer={}, queue={}", 
                                  producerId, consumerId, queueName);
                    }
                } catch (Exception e) {
                    log.warn("Failed to track message: {}", e.getMessage());
                }
                
                return message;
            }
            
            private Long extractLongHeader(Map<String, Object> headers, String... keys) {
                for (String key : keys) {
                    Object value = headers.get(key);
                    if (value != null) {
                        if (value instanceof Long) {
                            return (Long) value;
                        } else if (value instanceof Number) {
                            return ((Number) value).longValue();
                        } else if (value instanceof String) {
                            try {
                                return Long.parseLong((String) value);
                            } catch (NumberFormatException e) {
                                // Ignore
                            }
                        }
                    }
                }
                return null;
            }
        };
    }
}

