package com.cruise.recommender.service;

import com.cruise.recommender.config.RabbitMQConfig;
import com.cruise.recommender.entity.Port;
import com.cruise.recommender.entity.User;
import com.cruise.recommender.repository.PortRepository;
import com.cruise.recommender.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for ports to produce messages to users via RabbitMQ
 * Simulates port updates being sent to users
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortMessageProducer {
    
    private final RabbitTemplate rabbitTemplate;
    private final PortRepository portRepository;
    private final UserRepository userRepository;
    private final StatisticsService statisticsService;
    private final ObjectMapper objectMapper;
    
    /**
     * Send port update message from a port to a user
     */
    public void sendPortUpdateToUser(Long portId, Long userId, Map<String, Object> updateData) {
        try {
            Port port = portRepository.findById(portId).orElse(null);
            User user = userRepository.findById(userId).orElse(null);
            
            if (port == null || user == null) {
                log.warn("Port {} or User {} not found", portId, userId);
                return;
            }
            
            // Create message with tracking headers
            MessageProperties props = new MessageProperties();
            props.setHeader("producerId", portId);
            props.setHeader("consumerId", userId);
            props.setHeader("portCode", port.getPortCode());
            props.setHeader("portName", port.getName());
            props.setHeader("userEmail", user.getEmail());
            props.setHeader("messageType", "port_update");
            props.setHeader("exchange", RabbitMQConfig.NOTIFICATION_EXCHANGE);
            props.setHeader("routingKey", RabbitMQConfig.USER_NOTIFICATION);
            props.setContentType("application/json");
            
            // Create proper JSON message body
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("portId", portId);
            messageData.put("portCode", port.getPortCode());
            messageData.put("portName", port.getName());
            messageData.put("update", updateData != null ? updateData : new HashMap<>());
            
            String messageBody = objectMapper.writeValueAsString(messageData);
            
            Message message = new Message(messageBody.getBytes(), props);
            
            // Send to notification exchange
            rabbitTemplate.send(
                RabbitMQConfig.NOTIFICATION_EXCHANGE,
                RabbitMQConfig.USER_NOTIFICATION,
                message
            );
            
            // Record in statistics
            statisticsService.recordMessage(
                portId,
                userId,
                RabbitMQConfig.NOTIFICATION_QUEUE,
                RabbitMQConfig.NOTIFICATION_EXCHANGE,
                RabbitMQConfig.USER_NOTIFICATION,
                messageBody,
                "port_update"
            );
            
            log.debug("Sent port update from port {} to user {}", portId, userId);
            
        } catch (Exception e) {
            log.error("Error sending port update message", e);
        }
    }
    
    /**
     * Simulate port updates being sent to all active users
     * Runs periodically to generate message traffic
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void simulatePortUpdates() {
        try {
            List<Port> ports = portRepository.findAll();
            List<User> users = userRepository.findByIsActive(true);
            
            if (ports.isEmpty() || users.isEmpty()) {
                return;
            }
            
            // Send a few random port updates
            int updatesToSend = Math.min(5, ports.size());
            for (int i = 0; i < updatesToSend; i++) {
                Port port = ports.get((int) (Math.random() * ports.size()));
                User user = users.get((int) (Math.random() * users.size()));
                
                Map<String, Object> updateData = Map.of(
                    "type", "capacity_update",
                    "timestamp", System.currentTimeMillis()
                );
                
                sendPortUpdateToUser(port.getId(), user.getId(), updateData);
            }
            
            log.debug("Simulated {} port updates", updatesToSend);
            
        } catch (Exception e) {
            log.error("Error simulating port updates", e);
        }
    }
}

