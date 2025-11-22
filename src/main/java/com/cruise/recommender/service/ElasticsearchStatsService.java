package com.cruise.recommender.service;

import com.cruise.recommender.entity.MessageTracking;
import com.cruise.recommender.entity.SparqlQueryStat;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for indexing statistics data to Elasticsearch
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ElasticsearchStatsService {
    
    @Value("${elasticsearch.host:localhost}")
    private String elasticsearchHost;
    
    @Value("${elasticsearch.port:9200}")
    private int elasticsearchPort;
    
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    
    /**
     * Index SPARQL query statistic to Elasticsearch
     */
    public void indexSparqlQueryStat(SparqlQueryStat stat) {
        try {
            String indexName = "sparql-query-stats-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
            String url = String.format("http://%s:%d/%s/_doc", elasticsearchHost, elasticsearchPort, indexName);
            
            Map<String, Object> doc = new HashMap<>();
            doc.put("queryType", stat.getQueryType());
            doc.put("success", stat.getSuccess());
            doc.put("durationMs", stat.getDurationMs());
            doc.put("errorType", stat.getErrorType());
            doc.put("resultCount", stat.getResultCount());
            doc.put("timestamp", stat.getTimestamp().toString());
            doc.put("queryHash", stat.getQueryHash());
            
            String json = objectMapper.writeValueAsString(doc);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.debug("Indexed SPARQL query stat to Elasticsearch");
            } else {
                log.warn("Failed to index SPARQL query stat: {}", response.statusCode());
            }
            
        } catch (Exception e) {
            log.debug("Could not index SPARQL query stat to Elasticsearch: {}", e.getMessage());
        }
    }
    
    /**
     * Index message tracking data to Elasticsearch
     */
    public void indexMessageTracking(MessageTracking tracking) {
        try {
            String indexName = "message-tracking-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
            String url = String.format("http://%s:%d/%s/_doc", elasticsearchHost, elasticsearchPort, indexName);
            
            Map<String, Object> doc = new HashMap<>();
            doc.put("producerId", tracking.getProducerId());
            doc.put("consumerId", tracking.getConsumerId());
            doc.put("queueName", tracking.getQueueName());
            doc.put("exchangeName", tracking.getExchangeName());
            doc.put("routingKey", tracking.getRoutingKey());
            doc.put("consumed", tracking.getConsumed());
            doc.put("timestamp", tracking.getTimestamp().toString());
            doc.put("consumedAt", tracking.getConsumedAt() != null ? tracking.getConsumedAt().toString() : null);
            doc.put("messageType", tracking.getMessageType());
            
            String json = objectMapper.writeValueAsString(doc);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.debug("Indexed message tracking to Elasticsearch");
            } else {
                log.warn("Failed to index message tracking: {}", response.statusCode());
            }
            
        } catch (Exception e) {
            log.debug("Could not index message tracking to Elasticsearch: {}", e.getMessage());
        }
    }
}

