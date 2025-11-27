package com.cruise.recommender.repository.elasticsearch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

/**
 * Elasticsearch document for system resource utilization metrics
 * Tracks CPU, memory, disk, and JVM metrics
 */
@Document(indexName = "resource-utilization")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceUtilizationDocument {
    
    @Id
    private String id; // Auto-generated
    
    @Field(type = FieldType.Date, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
    
    // CPU Metrics
    @Field(type = FieldType.Double)
    private Double cpuUsagePercent; // CPU usage percentage
    
    @Field(type = FieldType.Double)
    private Double systemLoadAverage; // System load average
    
    // Memory Metrics
    @Field(type = FieldType.Long)
    private Long heapUsedBytes; // JVM heap used
    
    @Field(type = FieldType.Long)
    private Long heapMaxBytes; // JVM heap max
    
    @Field(type = FieldType.Double)
    private Double heapUsagePercent; // Heap usage percentage
    
    @Field(type = FieldType.Long)
    private Long nonHeapUsedBytes; // Non-heap memory used
    
    @Field(type = FieldType.Long)
    private Long nonHeapMaxBytes; // Non-heap memory max
    
    @Field(type = FieldType.Long)
    private Long totalMemoryBytes; // Total system memory
    
    @Field(type = FieldType.Long)
    private Long freeMemoryBytes; // Free system memory
    
    @Field(type = FieldType.Double)
    private Double memoryUsagePercent; // System memory usage percentage
    
    // Thread Metrics
    @Field(type = FieldType.Integer)
    private Integer threadCount; // Current thread count
    
    @Field(type = FieldType.Integer)
    private Integer peakThreadCount; // Peak thread count
    
    @Field(type = FieldType.Integer)
    private Integer daemonThreadCount; // Daemon thread count
    
    // GC Metrics
    @Field(type = FieldType.Long)
    private Long gcCollectionCount; // Total GC collections
    
    @Field(type = FieldType.Long)
    private Long gcCollectionTimeMs; // Total GC time in milliseconds
    
    // Disk Metrics
    @Field(type = FieldType.Long)
    private Long diskTotalBytes; // Total disk space
    
    @Field(type = FieldType.Long)
    private Long diskFreeBytes; // Free disk space
    
    @Field(type = FieldType.Double)
    private Double diskUsagePercent; // Disk usage percentage
    
    // Database Connection Pool Metrics
    @Field(type = FieldType.Integer)
    private Integer activeConnections; // Active DB connections
    
    @Field(type = FieldType.Integer)
    private Integer idleConnections; // Idle DB connections
    
    @Field(type = FieldType.Integer)
    private Integer maxConnections; // Max DB connections
    
    @Field(type = FieldType.Double)
    private Double connectionPoolUsagePercent; // Connection pool usage
    
    // Redis Metrics
    @Field(type = FieldType.Long)
    private Long redisMemoryUsedBytes; // Redis memory used
    
    @Field(type = FieldType.Integer)
    private Integer redisConnectedClients; // Redis connected clients
    
    @Field(type = FieldType.Long)
    private Long redisKeyspaceHits; // Redis keyspace hits
    
    @Field(type = FieldType.Long)
    private Long redisKeyspaceMisses; // Redis keyspace misses
    
    @Field(type = FieldType.Double)
    private Double redisHitRate; // Redis hit rate percentage
}

