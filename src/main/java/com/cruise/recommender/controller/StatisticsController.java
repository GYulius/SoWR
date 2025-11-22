package com.cruise.recommender.controller;

import com.cruise.recommender.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Controller for statistics endpoints
 */
@RestController
@RequestMapping("/admin/stats")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@PreAuthorize("hasRole('ADMIN')")
public class StatisticsController {
    
    private final StatisticsService statisticsService;
    
    /**
     * Get SPARQL query statistics
     * GET /admin/stats/sparql?hours=24
     */
    @GetMapping("/sparql")
    public ResponseEntity<Map<String, Object>> getSparqlStats(
            @RequestParam(defaultValue = "24") int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        Map<String, Object> stats = statisticsService.getSparqlStats(since);
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Record SPARQL query statistics (called by simulation script)
     * POST /admin/stats/sparql
     * Note: Statistics are automatically recorded when queries are executed via the API.
     * This endpoint is kept for compatibility but doesn't require authentication for bulk imports.
     */
    @PostMapping("/sparql")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Map<String, String>> recordSparqlStats(@RequestBody Map<String, Object> stats) {
        log.info("Received SPARQL statistics bulk import: {} queries", stats.get("totalQueries"));
        // Statistics are already recorded by the service when queries execute, 
        // but we accept bulk imports for external tools
        return ResponseEntity.ok(Map.of("status", "received", "message", "Statistics recorded automatically during query execution"));
    }
    
    /**
     * Get message tracking statistics
     * GET /admin/stats/messages?hours=24
     */
    @GetMapping("/messages")
    public ResponseEntity<Map<String, Object>> getMessageStats(
            @RequestParam(defaultValue = "24") int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        Map<String, Object> stats = statisticsService.getMessageStats(since);
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Get combined statistics for dashboard
     * GET /admin/stats/dashboard?hours=24
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardStats(
            @RequestParam(defaultValue = "24") int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        Map<String, Object> dashboard = new java.util.HashMap<>();
        dashboard.put("sparql", statisticsService.getSparqlStats(since));
        dashboard.put("messages", statisticsService.getMessageStats(since));
        dashboard.put("timeRange", Map.of(
            "since", since.toString(),
            "hours", hours
        ));
        
        return ResponseEntity.ok(dashboard);
    }
}

