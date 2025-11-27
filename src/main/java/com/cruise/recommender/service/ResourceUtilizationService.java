package com.cruise.recommender.service;

import com.cruise.recommender.repository.elasticsearch.ResourceUtilizationDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for collecting and indexing system resource utilization metrics
 * Runs on a schedule to periodically collect system metrics
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceUtilizationService {
    
    @Value("${elasticsearch.host:localhost}")
    private String elasticsearchHost;
    
    @Value("${elasticsearch.port:9200}")
    private int elasticsearchPort;
    
    @Value("${elasticsearch.enabled:false}")
    private boolean elasticsearchEnabled;
    
    @Value("${monitoring.resource-collection-interval:60000}") // Default: 60 seconds
    private long collectionIntervalMs;
    
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    
    /**
     * Collect and index resource utilization metrics
     * Runs every minute by default (configurable)
     */
    @Scheduled(fixedDelayString = "${monitoring.resource-collection-interval:60000}")
    public void collectAndIndexMetrics() {
        if (!elasticsearchEnabled) {
            return; // Skip if Elasticsearch is not enabled
        }
        
        try {
            ResourceUtilizationDocument metrics = collectMetrics();
            indexMetrics(metrics);
        } catch (Exception e) {
            log.debug("Error collecting resource utilization metrics: {}", e.getMessage());
        }
    }
    
    /**
     * Collect current system metrics
     */
    private ResourceUtilizationDocument collectMetrics() {
        LocalDateTime timestamp = LocalDateTime.now();
        
        // Memory metrics
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();
        double heapUsagePercent = heapMax > 0 ? (heapUsed * 100.0 / heapMax) : 0;
        
        long nonHeapUsed = memoryBean.getNonHeapMemoryUsage().getUsed();
        long nonHeapMax = memoryBean.getNonHeapMemoryUsage().getMax();
        
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        double memoryUsagePercent = totalMemory > 0 ? (usedMemory * 100.0 / totalMemory) : 0;
        
        // Thread metrics
        int threadCount = threadBean.getThreadCount();
        int peakThreadCount = threadBean.getPeakThreadCount();
        int daemonThreadCount = threadBean.getDaemonThreadCount();
        
        // GC metrics
        long gcCollectionCount = 0;
        long gcCollectionTimeMs = 0;
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCollectionCount += gcBean.getCollectionCount();
            gcCollectionTimeMs += gcBean.getCollectionTime();
        }
        
        // CPU metrics (approximate)
        // Note: getProcessLoadAverage() is not available in standard OperatingSystemMXBean
        // Use available CPU time instead for approximate CPU usage
        com.sun.management.OperatingSystemMXBean osBean = 
            (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        
        double systemLoadAverage = -1.0;
        double cpuUsagePercent = 0.0;
        
        try {
            // Try to get process load average (available on Unix-like systems)
            systemLoadAverage = osBean.getProcessCpuLoad();
            if (systemLoadAverage >= 0) {
                cpuUsagePercent = systemLoadAverage * 100.0;
            } else {
                // Fallback: use system load average if available
                systemLoadAverage = osBean.getSystemLoadAverage();
                if (systemLoadAverage >= 0) {
                    cpuUsagePercent = Math.min(systemLoadAverage * 100.0, 100.0);
                }
            }
        } catch (Exception e) {
            // If com.sun.management is not available, use system load average
            try {
                systemLoadAverage = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
                if (systemLoadAverage >= 0) {
                    cpuUsagePercent = Math.min(systemLoadAverage * 100.0, 100.0);
                }
            } catch (Exception ex) {
                // If not available, leave as 0
                systemLoadAverage = -1.0;
                cpuUsagePercent = 0.0;
            }
        }
        
        // Disk metrics (using available system properties)
        java.io.File root = new java.io.File("/");
        long diskTotalBytes = root.getTotalSpace();
        long diskFreeBytes = root.getFreeSpace();
        double diskUsagePercent = diskTotalBytes > 0 
                ? ((diskTotalBytes - diskFreeBytes) * 100.0 / diskTotalBytes) 
                : 0;
        
        return ResourceUtilizationDocument.builder()
                .id(UUID.randomUUID().toString())
                .timestamp(timestamp)
                .cpuUsagePercent(cpuUsagePercent)
                .systemLoadAverage(systemLoadAverage)
                .heapUsedBytes(heapUsed)
                .heapMaxBytes(heapMax)
                .heapUsagePercent(heapUsagePercent)
                .nonHeapUsedBytes(nonHeapUsed)
                .nonHeapMaxBytes(nonHeapMax)
                .totalMemoryBytes(totalMemory)
                .freeMemoryBytes(freeMemory)
                .memoryUsagePercent(memoryUsagePercent)
                .threadCount(threadCount)
                .peakThreadCount(peakThreadCount)
                .daemonThreadCount(daemonThreadCount)
                .gcCollectionCount(gcCollectionCount)
                .gcCollectionTimeMs(gcCollectionTimeMs)
                .diskTotalBytes(diskTotalBytes)
                .diskFreeBytes(diskFreeBytes)
                .diskUsagePercent(diskUsagePercent)
                // Database and Redis metrics would require additional connections
                // These can be added later if needed
                .build();
    }
    
    /**
     * Index metrics to Elasticsearch
     */
    private void indexMetrics(ResourceUtilizationDocument metrics) {
        try {
            String indexName = "resource-utilization-" + 
                    metrics.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
            String url = String.format("http://%s:%d/%s/_doc", elasticsearchHost, elasticsearchPort, indexName);
            
            Map<String, Object> doc = new HashMap<>();
            doc.put("id", metrics.getId());
            doc.put("timestamp", metrics.getTimestamp().toString());
            doc.put("cpuUsagePercent", metrics.getCpuUsagePercent());
            doc.put("systemLoadAverage", metrics.getSystemLoadAverage());
            doc.put("heapUsedBytes", metrics.getHeapUsedBytes());
            doc.put("heapMaxBytes", metrics.getHeapMaxBytes());
            doc.put("heapUsagePercent", metrics.getHeapUsagePercent());
            doc.put("nonHeapUsedBytes", metrics.getNonHeapUsedBytes());
            doc.put("nonHeapMaxBytes", metrics.getNonHeapMaxBytes());
            doc.put("totalMemoryBytes", metrics.getTotalMemoryBytes());
            doc.put("freeMemoryBytes", metrics.getFreeMemoryBytes());
            doc.put("memoryUsagePercent", metrics.getMemoryUsagePercent());
            doc.put("threadCount", metrics.getThreadCount());
            doc.put("peakThreadCount", metrics.getPeakThreadCount());
            doc.put("daemonThreadCount", metrics.getDaemonThreadCount());
            doc.put("gcCollectionCount", metrics.getGcCollectionCount());
            doc.put("gcCollectionTimeMs", metrics.getGcCollectionTimeMs());
            doc.put("diskTotalBytes", metrics.getDiskTotalBytes());
            doc.put("diskFreeBytes", metrics.getDiskFreeBytes());
            doc.put("diskUsagePercent", metrics.getDiskUsagePercent());
            
            String json = objectMapper.writeValueAsString(doc);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            log.debug("Indexed resource utilization metrics to Elasticsearch");
                        } else {
                            log.warn("Failed to index resource utilization metrics: {}", response.statusCode());
                        }
                    })
                    .exceptionally(e -> {
                        log.debug("Could not index resource utilization metrics to Elasticsearch: {}", e.getMessage());
                        return null;
                    });
            
        } catch (Exception e) {
            log.debug("Error indexing resource utilization metrics: {}", e.getMessage());
        }
    }
}

