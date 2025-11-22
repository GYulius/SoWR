package com.cruise.recommender.service;

import com.cruise.recommender.config.PrometheusMetricsService;
import com.cruise.recommender.entity.MessageTracking;
import com.cruise.recommender.entity.SparqlQueryStat;
import com.cruise.recommender.repository.MessageTrackingRepository;
import com.cruise.recommender.repository.SparqlQueryStatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing statistics
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StatisticsService {
    
    private final SparqlQueryStatRepository sparqlQueryStatRepository;
    private final MessageTrackingRepository messageTrackingRepository;
    private final PrometheusMetricsService prometheusMetricsService;
    private final ElasticsearchStatsService elasticsearchStatsService;
    
    /**
     * Record a SPARQL query statistic
     */
    @Transactional
    public void recordSparqlQuery(String queryType, boolean success, long durationMs, 
                                   String errorType, Integer resultCount, String queryHash) {
        SparqlQueryStat stat = SparqlQueryStat.builder()
                .queryType(queryType)
                .success(success)
                .durationMs(durationMs)
                .errorType(errorType)
                .resultCount(resultCount)
                .queryHash(queryHash)
                .timestamp(LocalDateTime.now())
                .build();
        
        SparqlQueryStat savedStat = sparqlQueryStatRepository.save(stat);
        
        // Record in Prometheus
        prometheusMetricsService.recordSparqlQuery(queryType, success, durationMs);
        
        // Index to Elasticsearch (async, non-blocking)
        try {
            elasticsearchStatsService.indexSparqlQueryStat(savedStat);
        } catch (Exception e) {
            log.debug("Could not index to Elasticsearch: {}", e.getMessage());
        }
        
        log.debug("Recorded SPARQL query stat: type={}, success={}, duration={}ms", 
                  queryType, success, durationMs);
    }
    
    /**
     * Get SPARQL query statistics
     */
    public Map<String, Object> getSparqlStats(LocalDateTime since) {
        Map<String, Object> stats = new HashMap<>();
        
        Long totalQueries = sparqlQueryStatRepository.countSince(since);
        Long successfulQueries = sparqlQueryStatRepository.countSuccessfulSince(since);
        Double avgDuration = sparqlQueryStatRepository.getAverageDurationSince(since);
        
        stats.put("totalQueries", totalQueries);
        stats.put("successfulQueries", successfulQueries);
        stats.put("failedQueries", totalQueries - successfulQueries);
        stats.put("successRate", totalQueries > 0 ? (successfulQueries * 100.0 / totalQueries) : 0);
        stats.put("avgDurationMs", avgDuration != null ? avgDuration : 0);
        
        // Stats by query type
        List<Object[]> typeStats = sparqlQueryStatRepository.getStatsByTypeSince(since);
        Map<String, Map<String, Object>> byType = new HashMap<>();
        for (Object[] row : typeStats) {
            String type = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            Double avgDur = ((Number) row[2]).doubleValue();
            Long successCount = ((Number) row[3]).longValue();
            
            Map<String, Object> typeData = new HashMap<>();
            typeData.put("count", count);
            typeData.put("avgDurationMs", avgDur);
            typeData.put("successCount", successCount);
            typeData.put("successRate", count > 0 ? (successCount * 100.0 / count) : 0);
            byType.put(type, typeData);
        }
        stats.put("byType", byType);
        
        // Error statistics
        List<Object[]> errorStats = sparqlQueryStatRepository.getErrorStatsSince(since);
        Map<String, Long> errors = new HashMap<>();
        for (Object[] row : errorStats) {
            String errorType = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            errors.put(errorType, count);
        }
        stats.put("errors", errors);
        
        return stats;
    }
    
    /**
     * Record a message sent from a port (producer) to a user (consumer)
     */
    @Transactional
    public void recordMessage(Long producerId, Long consumerId, String queueName, 
                              String exchangeName, String routingKey, String messageBody, 
                              String messageType) {
        MessageTracking tracking = MessageTracking.builder()
                .producerId(producerId)
                .consumerId(consumerId)
                .queueName(queueName)
                .exchangeName(exchangeName)
                .routingKey(routingKey)
                .messageBody(messageBody)
                .messageType(messageType)
                .consumed(false)
                .timestamp(LocalDateTime.now())
                .build();
        
        MessageTracking savedTracking = messageTrackingRepository.save(tracking);
        
        // Record in Prometheus
        prometheusMetricsService.recordMessageSent(producerId, consumerId, queueName);
        
        // Index to Elasticsearch (async, non-blocking)
        try {
            elasticsearchStatsService.indexMessageTracking(savedTracking);
        } catch (Exception e) {
            log.debug("Could not index to Elasticsearch: {}", e.getMessage());
        }
        
        log.debug("Recorded message: producer={}, consumer={}, queue={}", 
                  producerId, consumerId, queueName);
    }
    
    /**
     * Mark a message as consumed
     */
    @Transactional
    public void markMessageConsumed(Long messageId) {
        messageTrackingRepository.findById(messageId).ifPresent(msg -> {
            msg.setConsumed(true);
            msg.setConsumedAt(LocalDateTime.now());
            messageTrackingRepository.save(msg);
            
            // Record in Prometheus
            prometheusMetricsService.recordMessageConsumed(msg.getConsumerId(), msg.getQueueName());
        });
    }
    
    /**
     * Get message tracking statistics
     */
    public Map<String, Object> getMessageStats(LocalDateTime since) {
        Map<String, Object> stats = new HashMap<>();
        
        // Producer statistics
        List<Object[]> producerStats = messageTrackingRepository.getProducerStatsSince(since);
        List<Map<String, Object>> producers = producerStats.stream()
                .map(row -> {
                    Map<String, Object> prod = new HashMap<>();
                    prod.put("producerId", ((Number) row[0]).longValue());
                    prod.put("messageCount", ((Number) row[1]).longValue());
                    return prod;
                })
                .collect(Collectors.toList());
        stats.put("producers", producers);
        
        // Consumer statistics
        List<Object[]> consumerStats = messageTrackingRepository.getConsumerStatsSince(since);
        List<Map<String, Object>> consumers = consumerStats.stream()
                .map(row -> {
                    Map<String, Object> cons = new HashMap<>();
                    cons.put("consumerId", ((Number) row[0]).longValue());
                    cons.put("consumedCount", ((Number) row[1]).longValue());
                    return cons;
                })
                .collect(Collectors.toList());
        stats.put("consumers", consumers);
        
        // Total messages
        long totalMessages = messageTrackingRepository.findAll().stream()
                .filter(m -> m.getTimestamp().isAfter(since) || m.getTimestamp().isEqual(since))
                .count();
        stats.put("totalMessages", totalMessages);
        
        long consumedMessages = messageTrackingRepository.findAll().stream()
                .filter(m -> m.getConsumed() && 
                           (m.getTimestamp().isAfter(since) || m.getTimestamp().isEqual(since)))
                .count();
        stats.put("consumedMessages", consumedMessages);
        stats.put("pendingMessages", totalMessages - consumedMessages);
        
        return stats;
    }
}

