package com.cruise.recommender.service;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.*;

/**
 * Service for querying system performance metrics from Elasticsearch
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemPerformanceService {
    
    @Value("${elasticsearch.host:localhost}")
    private String elasticsearchHost;
    
    @Value("${elasticsearch.port:9200}")
    private int elasticsearchPort;
    
    @Value("${elasticsearch.enabled:false}")
    private boolean elasticsearchEnabled;
    
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    
    /**
     * Get API performance summary statistics
     */
    public Map<String, Object> getApiPerformanceStats(int hours) {
        if (!elasticsearchEnabled) {
            return Map.of(
                "enabled", false,
                "message", "Elasticsearch is not enabled"
            );
        }
        
        try {
            // First check if Elasticsearch is accessible
            if (!isElasticsearchAccessible()) {
                return Map.of("enabled", true, "error", "Cannot connect to Elasticsearch. Please check if Elasticsearch is running.");
            }
            
            String indexPattern = "api-performance-*";
            String url = String.format("http://%s:%d/%s/_search", elasticsearchHost, elasticsearchPort, indexPattern);
            
            LocalDateTime since = LocalDateTime.now().minusHours(hours);
            // Format timestamp to match what's stored: "yyyy-MM-dd'T'HH:mm:ss"
            String sinceStr = since.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
            
            // Build aggregation query - filter by timestamp and aggregate
            // Use doc_count from filtered aggregation instead of value_count on _id
            String query = """
                {
                  "size": 0,
                  "query": {
                    "match_all": {}
                  },
                  "aggs": {
                    "filtered_by_time": {
                      "filter": {
                        "range": {
                          "timestamp": {
                            "gte": "%s"
                          }
                        }
                      },
                      "aggs": {
                        "successful_requests": {
                          "filter": {
                            "term": {
                              "success": true
                            }
                          }
                        },
                        "failed_requests": {
                          "filter": {
                            "term": {
                              "success": false
                            }
                          }
                        },
                        "avg_response_time": {
                          "avg": {
                            "field": "responseTimeMs"
                          }
                        },
                        "max_response_time": {
                          "max": {
                            "field": "responseTimeMs"
                          }
                        },
                        "p95_response_time": {
                          "percentiles": {
                            "field": "responseTimeMs",
                            "percents": [95]
                          }
                        },
                        "by_status": {
                          "terms": {
                            "field": "statusCode",
                            "size": 10
                          }
                        },
                        "by_endpoint": {
                          "terms": {
                            "field": "endpoint.keyword",
                            "size": 10,
                            "order": {
                              "_count": "desc"
                            }
                          },
                          "aggs": {
                            "avg_time": {
                              "avg": {
                                "field": "responseTimeMs"
                              }
                            }
                          }
                        },
                        "error_types": {
                          "terms": {
                            "field": "errorType.keyword",
                            "size": 10,
                            "missing": "N/A"
                          }
                        }
                      }
                    }
                  }
                }
                """.replace("%s", sinceStr);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(query))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode json = objectMapper.readTree(response.body());
                
                // Check for Elasticsearch errors in response
                if (json.has("error")) {
                    String errorMsg = json.get("error").toString();
                    log.warn("Elasticsearch returned error: {}", errorMsg);
                    return Map.of("enabled", true, "error", "Elasticsearch error: " + errorMsg);
                }
                
                // Check if aggregations exist (might be null if no data)
                JsonNode aggs = json.get("aggregations");
                if (aggs == null || aggs.isEmpty()) {
                    // No data found - return empty stats
                    return createEmptyApiPerformanceStats(hours, sinceStr);
                }
                
                // Get filtered aggregations (nested under filtered_by_time)
                JsonNode filteredAggs = aggs.has("filtered_by_time") ? aggs.get("filtered_by_time") : aggs;
                
                // Get total requests from the filtered aggregation's doc_count
                long totalRequests = filteredAggs.has("doc_count") 
                    ? filteredAggs.get("doc_count").asLong() : 0;
                long successfulRequests = filteredAggs.has("successful_requests") && filteredAggs.get("successful_requests").has("doc_count")
                    ? filteredAggs.get("successful_requests").get("doc_count").asLong() : 0;
                long failedRequests = filteredAggs.has("failed_requests") && filteredAggs.get("failed_requests").has("doc_count")
                    ? filteredAggs.get("failed_requests").get("doc_count").asLong() : 0;
                
                double avgResponseTime = filteredAggs.has("avg_response_time") && filteredAggs.get("avg_response_time").has("value") && !filteredAggs.get("avg_response_time").get("value").isNull()
                    ? filteredAggs.get("avg_response_time").get("value").asDouble() : 0.0;
                double maxResponseTime = filteredAggs.has("max_response_time") && filteredAggs.get("max_response_time").has("value") && !filteredAggs.get("max_response_time").get("value").isNull()
                    ? filteredAggs.get("max_response_time").get("value").asDouble() : 0.0;
                double p95ResponseTime = filteredAggs.has("p95_response_time") && filteredAggs.get("p95_response_time").has("values") && filteredAggs.get("p95_response_time").get("values").has("95.0")
                    ? filteredAggs.get("p95_response_time").get("values").get("95.0").asDouble() : 0.0;
                
                double successRate = totalRequests > 0 ? (successfulRequests * 100.0 / totalRequests) : 0;
                double errorRate = totalRequests > 0 ? (failedRequests * 100.0 / totalRequests) : 0;
                
                // Extract status code distribution
                List<Map<String, Object>> statusDistribution = new ArrayList<>();
                if (filteredAggs.has("by_status") && filteredAggs.get("by_status").has("buckets")) {
                    JsonNode byStatus = filteredAggs.get("by_status").get("buckets");
                    for (JsonNode bucket : byStatus) {
                        Map<String, Object> status = new HashMap<>();
                        // statusCode is stored as integer, so get it as int then convert to string
                        int statusCode = bucket.get("key").asInt();
                        status.put("status", String.valueOf(statusCode));
                        status.put("count", bucket.get("doc_count").asLong());
                        statusDistribution.add(status);
                    }
                }
                
                // Extract endpoint performance
                List<Map<String, Object>> endpointPerformance = new ArrayList<>();
                if (filteredAggs.has("by_endpoint") && filteredAggs.get("by_endpoint").has("buckets")) {
                    JsonNode byEndpoint = filteredAggs.get("by_endpoint").get("buckets");
                    for (JsonNode bucket : byEndpoint) {
                        Map<String, Object> endpoint = new HashMap<>();
                        endpoint.put("endpoint", bucket.get("key").asText());
                        endpoint.put("count", bucket.get("doc_count").asLong());
                        if (bucket.has("avg_time") && bucket.get("avg_time").has("value") && !bucket.get("avg_time").get("value").isNull()) {
                            endpoint.put("avgTime", bucket.get("avg_time").get("value").asDouble());
                        } else {
                            endpoint.put("avgTime", 0.0);
                        }
                        endpointPerformance.add(endpoint);
                    }
                }
                
                // Extract error types
                List<Map<String, Object>> errorTypes = new ArrayList<>();
                if (filteredAggs.has("error_types") && filteredAggs.get("error_types").has("buckets")) {
                    JsonNode errorTypesAgg = filteredAggs.get("error_types").get("buckets");
                    for (JsonNode bucket : errorTypesAgg) {
                        Map<String, Object> error = new HashMap<>();
                        error.put("type", bucket.get("key").asText());
                        error.put("count", bucket.get("doc_count").asLong());
                        errorTypes.add(error);
                    }
                }
                
                Map<String, Object> result = new HashMap<>();
                result.put("enabled", true);
                result.put("totalRequests", totalRequests);
                result.put("successfulRequests", successfulRequests);
                result.put("failedRequests", failedRequests);
                result.put("successRate", successRate);
                result.put("errorRate", errorRate);
                result.put("avgResponseTimeMs", avgResponseTime);
                result.put("maxResponseTimeMs", maxResponseTime);
                result.put("p95ResponseTimeMs", p95ResponseTime);
                result.put("statusDistribution", statusDistribution);
                result.put("endpointPerformance", endpointPerformance);
                result.put("errorTypes", errorTypes);
                result.put("timeRange", Map.of("hours", hours, "since", sinceStr));
                
                return result;
            } else {
                String errorBody = response.body();
                log.warn("Failed to query Elasticsearch for API performance: {} - {}", response.statusCode(), errorBody);
                
                // Try to parse error details from response
                String errorMessage = "Failed to query Elasticsearch: HTTP " + response.statusCode();
                try {
                    JsonNode errorJson = objectMapper.readTree(errorBody);
                    if (errorJson.has("error")) {
                        JsonNode error = errorJson.get("error");
                        if (error.has("root_cause") && error.get("root_cause").isArray() && error.get("root_cause").size() > 0) {
                            JsonNode rootCause = error.get("root_cause").get(0);
                            if (rootCause.has("reason")) {
                                errorMessage = "Elasticsearch error: " + rootCause.get("reason").asText();
                            }
                        } else if (error.has("reason")) {
                            errorMessage = "Elasticsearch error: " + error.get("reason").asText();
                        }
                    }
                } catch (Exception e) {
                    // Use default error message
                }
                
                // Check if it's a 404 (indices don't exist)
                if (response.statusCode() == 404) {
                    return Map.of("enabled", true, "error", "No API performance data found. Data will appear after API calls are made.");
                }
                
                return Map.of("enabled", true, "error", errorMessage);
            }
        } catch (java.net.ConnectException e) {
            log.warn("Cannot connect to Elasticsearch at {}:{}", elasticsearchHost, elasticsearchPort);
            return Map.of("enabled", true, "error", "Cannot connect to Elasticsearch. Please check if Elasticsearch is running at " + elasticsearchHost + ":" + elasticsearchPort);
        } catch (Exception e) {
            log.error("Error querying API performance from Elasticsearch", e);
            return Map.of("enabled", true, "error", "Error querying Elasticsearch: " + e.getMessage());
        }
    }
    
    /**
     * Create empty stats when no data is available
     */
    private Map<String, Object> createEmptyApiPerformanceStats(int hours, String sinceStr) {
        Map<String, Object> result = new HashMap<>();
        result.put("enabled", true);
        result.put("totalRequests", 0);
        result.put("successfulRequests", 0);
        result.put("failedRequests", 0);
        result.put("successRate", 0.0);
        result.put("errorRate", 0.0);
        result.put("avgResponseTimeMs", 0.0);
        result.put("maxResponseTimeMs", 0.0);
        result.put("p95ResponseTimeMs", 0.0);
        result.put("statusDistribution", new ArrayList<>());
        result.put("endpointPerformance", new ArrayList<>());
        result.put("errorTypes", new ArrayList<>());
        result.put("timeRange", Map.of("hours", hours, "since", sinceStr));
        return result;
    }
    
    /**
     * Check if Elasticsearch is accessible
     */
    private boolean isElasticsearchAccessible() {
        try {
            String url = String.format("http://%s:%d/_cluster/health", elasticsearchHost, elasticsearchPort);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            log.debug("Elasticsearch health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Get resource utilization summary statistics
     */
    public Map<String, Object> getResourceUtilizationStats(int hours) {
        if (!elasticsearchEnabled) {
            return Map.of(
                "enabled", false,
                "message", "Elasticsearch is not enabled"
            );
        }
        
        try {
            // First check if Elasticsearch is accessible
            if (!isElasticsearchAccessible()) {
                return Map.of("enabled", true, "error", "Cannot connect to Elasticsearch. Please check if Elasticsearch is running.");
            }
            
            String indexPattern = "resource-utilization-*";
            String url = String.format("http://%s:%d/%s/_search", elasticsearchHost, elasticsearchPort, indexPattern);
            
            LocalDateTime since = LocalDateTime.now().minusHours(hours);
            // Format timestamp to match what's stored: "yyyy-MM-dd'T'HH:mm:ss"
            String sinceStr = since.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
            
            // Get latest resource metrics
            String query = String.format("""
                {
                  "size": 1,
                  "sort": [
                    {
                      "timestamp": {
                        "order": "desc"
                      }
                    }
                  ],
                  "query": {
                    "bool": {
                      "must": [
                        {
                          "range": {
                            "timestamp": {
                              "gte": "%s",
                              "format": "yyyy-MM-dd'T'HH:mm:ss"
                            }
                          }
                        }
                      ]
                    }
                  },
                  "aggs": {
                    "avg_cpu": {
                      "avg": {
                        "field": "cpuUsagePercent"
                      }
                    },
                    "avg_memory": {
                      "avg": {
                        "field": "memoryUsagePercent"
                      }
                    },
                    "avg_heap": {
                      "avg": {
                        "field": "heapUsagePercent"
                      }
                    },
                    "avg_disk": {
                      "avg": {
                        "field": "diskUsagePercent"
                      }
                    },
                    "max_cpu": {
                      "max": {
                        "field": "cpuUsagePercent"
                      }
                    },
                    "max_memory": {
                      "max": {
                        "field": "memoryUsagePercent"
                      }
                    },
                    "max_heap": {
                      "max": {
                        "field": "heapUsagePercent"
                      }
                    },
                    "max_disk": {
                      "max": {
                        "field": "diskUsagePercent"
                      }
                    },
                    "avg_threads": {
                      "avg": {
                        "field": "activeThreads"
                      }
                    },
                    "max_threads": {
                      "max": {
                        "field": "activeThreads"
                      }
                    }
                  }
                }
                """, sinceStr);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(query))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode json = objectMapper.readTree(response.body());
                JsonNode hits = json.get("hits").get("hits");
                JsonNode aggs = json.get("aggregations");
                
                // Check if aggregations exist
                if (aggs == null || aggs.isEmpty()) {
                    return Map.of("enabled", true, "error", "No resource utilization data found. Data will appear after collection starts.");
                }
                
                Map<String, Object> latest = new HashMap<>();
                if (hits.size() > 0) {
                    JsonNode latestDoc = hits.get(0).get("_source");
                    latest.put("timestamp", latestDoc.has("timestamp") ? latestDoc.get("timestamp").asText() : "");
                    latest.put("cpuUsagePercent", latestDoc.has("cpuUsagePercent") && !latestDoc.get("cpuUsagePercent").isNull() ? latestDoc.get("cpuUsagePercent").asDouble() : 0);
                    latest.put("memoryUsagePercent", latestDoc.has("memoryUsagePercent") && !latestDoc.get("memoryUsagePercent").isNull() ? latestDoc.get("memoryUsagePercent").asDouble() : 0);
                    latest.put("heapUsagePercent", latestDoc.has("heapUsagePercent") && !latestDoc.get("heapUsagePercent").isNull() ? latestDoc.get("heapUsagePercent").asDouble() : 0);
                    latest.put("diskUsagePercent", latestDoc.has("diskUsagePercent") && !latestDoc.get("diskUsagePercent").isNull() ? latestDoc.get("diskUsagePercent").asDouble() : 0);
                    latest.put("threadCount", latestDoc.has("activeThreads") && !latestDoc.get("activeThreads").isNull() ? latestDoc.get("activeThreads").asInt() : 0);
                    latest.put("heapUsedBytes", latestDoc.has("heapUsedBytes") && !latestDoc.get("heapUsedBytes").isNull() ? latestDoc.get("heapUsedBytes").asLong() : 0);
                    latest.put("heapMaxBytes", latestDoc.has("heapMaxBytes") && !latestDoc.get("heapMaxBytes").isNull() ? latestDoc.get("heapMaxBytes").asLong() : 0);
                }
                
                // Handle null values from aggregations
                double avgCpu = aggs.has("avg_cpu") && aggs.get("avg_cpu").has("value") && !aggs.get("avg_cpu").get("value").isNull() 
                    ? aggs.get("avg_cpu").get("value").asDouble() : 0.0;
                double avgMemory = aggs.has("avg_memory") && aggs.get("avg_memory").has("value") && !aggs.get("avg_memory").get("value").isNull()
                    ? aggs.get("avg_memory").get("value").asDouble() : 0.0;
                double avgHeap = aggs.has("avg_heap") && aggs.get("avg_heap").has("value") && !aggs.get("avg_heap").get("value").isNull()
                    ? aggs.get("avg_heap").get("value").asDouble() : 0.0;
                double avgDisk = aggs.has("avg_disk") && aggs.get("avg_disk").has("value") && !aggs.get("avg_disk").get("value").isNull()
                    ? aggs.get("avg_disk").get("value").asDouble() : 0.0;
                double avgThreads = aggs.has("avg_threads") && aggs.get("avg_threads").has("value") && !aggs.get("avg_threads").get("value").isNull()
                    ? aggs.get("avg_threads").get("value").asDouble() : 0.0;
                
                double maxCpu = aggs.has("max_cpu") && aggs.get("max_cpu").has("value") && !aggs.get("max_cpu").get("value").isNull()
                    ? aggs.get("max_cpu").get("value").asDouble() : 0.0;
                double maxMemory = aggs.has("max_memory") && aggs.get("max_memory").has("value") && !aggs.get("max_memory").get("value").isNull()
                    ? aggs.get("max_memory").get("value").asDouble() : 0.0;
                double maxHeap = aggs.has("max_heap") && aggs.get("max_heap").has("value") && !aggs.get("max_heap").get("value").isNull()
                    ? aggs.get("max_heap").get("value").asDouble() : 0.0;
                double maxDisk = aggs.has("max_disk") && aggs.get("max_disk").has("value") && !aggs.get("max_disk").get("value").isNull()
                    ? aggs.get("max_disk").get("value").asDouble() : 0.0;
                double maxThreads = aggs.has("max_threads") && aggs.get("max_threads").has("value") && !aggs.get("max_threads").get("value").isNull()
                    ? aggs.get("max_threads").get("value").asDouble() : 0.0;
                
                Map<String, Object> result = new HashMap<>();
                result.put("enabled", true);
                result.put("latest", latest);
                result.put("averages", Map.of(
                    "cpuUsagePercent", avgCpu,
                    "memoryUsagePercent", avgMemory,
                    "heapUsagePercent", avgHeap,
                    "diskUsagePercent", avgDisk,
                    "threadCount", avgThreads
                ));
                result.put("maximums", Map.of(
                    "cpuUsagePercent", maxCpu,
                    "memoryUsagePercent", maxMemory,
                    "heapUsagePercent", maxHeap,
                    "diskUsagePercent", maxDisk,
                    "threadCount", maxThreads
                ));
                result.put("timeRange", Map.of("hours", hours, "since", sinceStr));
                
                return result;
            } else {
                String errorBody = response.body();
                log.warn("Failed to query Elasticsearch for resource utilization: {} - {}", response.statusCode(), errorBody);
                
                // Check if it's a 404 (indices don't exist)
                if (response.statusCode() == 404) {
                    return Map.of("enabled", true, "error", "No resource utilization data found. Data will appear after collection starts.");
                }
                
                return Map.of("enabled", true, "error", "Failed to query Elasticsearch: HTTP " + response.statusCode());
            }
        } catch (java.net.ConnectException e) {
            log.warn("Cannot connect to Elasticsearch at {}:{}", elasticsearchHost, elasticsearchPort);
            return Map.of("enabled", true, "error", "Cannot connect to Elasticsearch. Please check if Elasticsearch is running at " + elasticsearchHost + ":" + elasticsearchPort);
        } catch (Exception e) {
            log.error("Error querying resource utilization from Elasticsearch", e);
            return Map.of("enabled", true, "error", "Error querying Elasticsearch: " + e.getMessage());
        }
    }
}

