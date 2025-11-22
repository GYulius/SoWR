package com.cruise.recommender.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity to track RabbitMQ messages between ports (producers) and users (consumers)
 */
@Entity
@Table(name = "message_tracking", indexes = {
    @Index(name = "idx_producer_id", columnList = "producerId"),
    @Index(name = "idx_consumer_id", columnList = "consumerId"),
    @Index(name = "idx_timestamp", columnList = "timestamp"),
    @Index(name = "idx_queue_name", columnList = "queueName")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageTracking {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long producerId; // Port ID
    
    @Column(nullable = false)
    private Long consumerId; // User ID
    
    @Column(nullable = false, length = 100)
    private String queueName;
    
    @Column(nullable = false, length = 100)
    private String exchangeName;
    
    @Column(length = 100)
    private String routingKey;
    
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean consumed = false;
    
    @Column
    private LocalDateTime consumedAt;
    
    @Column(columnDefinition = "TEXT")
    private String messageBody; // JSON message content
    
    @Column(length = 50)
    private String messageType; // e.g., "port_update", "notification", etc.
}

