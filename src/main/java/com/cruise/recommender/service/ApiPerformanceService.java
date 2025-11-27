package com.cruise.recommender.service;

import com.cruise.recommender.repository.elasticsearch.ApiPerformanceDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service for recording and indexing API performance metrics to Elasticsearch
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiPerformanceService {
    
    @Value("${elasticsearch.host:localhost}")
    private String elasticsearchHost;
    
    @Value("${elasticsearch.port:9200}")
    private int elasticsearchPort;
    
    @Value("${elasticsearch.enabled:false}")
    private boolean elasticsearchEnabled;
    
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    
    /**
     * Record an API call performance metric
     * This method is async and non-blocking
     */
    @Async
    public void recordApiCall(
            String endpoint,
            String method,
            int httpStatus,
            long responseTimeMs,
            boolean success,
            String errorType,
            String errorMessage,
            String userEmail,
            String userRole,
            String clientIp,
            String userAgent,
            int requestSizeBytes,
            int responseSizeBytes,
            LocalDateTime timestamp) {
        
        if (!elasticsearchEnabled) {
            return; // Skip if Elasticsearch is not enabled
        }
        
        try {
            String indexName = "api-performance-" + timestamp.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
            String url = String.format("http://%s:%d/%s/_doc", elasticsearchHost, elasticsearchPort, indexName);
            
            // Extract controller and action from endpoint
            String controller = extractController(endpoint);
            String action = extractAction(endpoint);
            
            Map<String, Object> doc = new HashMap<>();
            doc.put("id", UUID.randomUUID().toString());
            doc.put("endpoint", endpoint);
            doc.put("method", method);
            doc.put("httpStatus", String.valueOf(httpStatus));
            doc.put("statusCode", httpStatus);
            doc.put("responseTimeMs", responseTimeMs);
            doc.put("success", success);
            doc.put("errorType", errorType);
            doc.put("errorMessage", errorMessage);
            doc.put("userEmail", userEmail);
            doc.put("userRole", userRole);
            doc.put("clientIp", clientIp);
            doc.put("userAgent", userAgent);
            doc.put("requestSizeBytes", requestSizeBytes);
            doc.put("responseSizeBytes", responseSizeBytes);
            doc.put("timestamp", timestamp.toString());
            doc.put("controller", controller);
            doc.put("action", action);
            
            String json = objectMapper.writeValueAsString(doc);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            log.debug("Indexed API performance metric to Elasticsearch");
                        } else {
                            log.warn("Failed to index API performance metric: {}", response.statusCode());
                        }
                    })
                    .exceptionally(e -> {
                        log.debug("Could not index API performance metric to Elasticsearch: {}", e.getMessage());
                        return null;
                    });
            
        } catch (Exception e) {
            log.debug("Error preparing API performance metric for Elasticsearch: {}", e.getMessage());
        }
    }
    
    private String extractController(String endpoint) {
        // Extract controller name from endpoint path
        // e.g., "/api/v1/recommendations" -> "RecommendationController"
        if (endpoint == null || endpoint.isEmpty()) {
            return "Unknown";
        }
        
        String[] parts = endpoint.split("/");
        if (parts.length >= 3) {
            String resource = parts[parts.length - 1];
            if (!resource.isEmpty()) {
                // Capitalize first letter and add "Controller"
                return resource.substring(0, 1).toUpperCase() + 
                       resource.substring(1) + "Controller";
            }
        }
        return "Unknown";
    }
    
    private String extractAction(String endpoint) {
        // For now, return endpoint as action
        // Could be enhanced to map to actual method names
        return endpoint;
    }
}

