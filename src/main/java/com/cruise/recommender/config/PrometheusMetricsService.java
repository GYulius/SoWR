package com.cruise.recommender.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Service for recording Prometheus metrics for SPARQL queries and message tracking
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PrometheusMetricsService {
    
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Counter> queryCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> queryTimers = new ConcurrentHashMap<>();
    private Counter totalMessagesCounter;
    private Counter consumedMessagesCounter;
    private Counter failedQueriesCounter;
    
    @PostConstruct
    public void init() {
        // Initialize counters
        totalMessagesCounter = Counter.builder("cruise_recommender_messages_total")
                .description("Total number of messages sent")
                .register(meterRegistry);
        
        consumedMessagesCounter = Counter.builder("cruise_recommender_messages_consumed_total")
                .description("Total number of messages consumed")
                .register(meterRegistry);
        
        failedQueriesCounter = Counter.builder("cruise_recommender_sparql_queries_failed_total")
                .description("Total number of failed SPARQL queries")
                .register(meterRegistry);
        
        // Register gauge for pending messages
        Gauge.builder("cruise_recommender_messages_pending", this, 
                service -> service.getPendingMessagesCount())
                .description("Number of pending messages")
                .register(meterRegistry);
    }
    
    /**
     * Record a SPARQL query execution
     */
    public void recordSparqlQuery(String queryType, boolean success, long durationMs) {
        String counterName = "cruise_recommender_sparql_queries_total";
        Counter counter = queryCounters.computeIfAbsent(queryType, type ->
                Counter.builder(counterName)
                        .tag("query_type", type)
                        .tag("status", success ? "success" : "failure")
                        .description("Total number of SPARQL queries by type")
                        .register(meterRegistry)
        );
        counter.increment();
        
        if (!success) {
            failedQueriesCounter.increment();
        }
        
        String timerName = "cruise_recommender_sparql_query_duration_ms";
        Timer timer = queryTimers.computeIfAbsent(queryType, type ->
                Timer.builder(timerName)
                        .tag("query_type", type)
                        .description("SPARQL query duration in milliseconds")
                        .register(meterRegistry)
        );
        timer.record(durationMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Record a message being sent
     */
    public void recordMessageSent(Long producerId, Long consumerId, String queueName) {
        totalMessagesCounter.increment();
        
        Counter.builder("cruise_recommender_messages_sent_total")
                .tag("producer_id", String.valueOf(producerId))
                .tag("consumer_id", String.valueOf(consumerId))
                .tag("queue", queueName)
                .description("Messages sent by producer to consumer")
                .register(meterRegistry)
                .increment();
    }
    
    /**
     * Record a message being consumed
     */
    public void recordMessageConsumed(Long consumerId, String queueName) {
        consumedMessagesCounter.increment();
        
        Counter.builder("cruise_recommender_messages_consumed_by_consumer_total")
                .tag("consumer_id", String.valueOf(consumerId))
                .tag("queue", queueName)
                .description("Messages consumed by consumer")
                .register(meterRegistry)
                .increment();
    }
    
    private double getPendingMessagesCount() {
        // This would query the database or message queue to get pending count
        // For now, return 0 as placeholder
        return 0.0;
    }
}

