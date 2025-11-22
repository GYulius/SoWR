package com.cruise.recommender.controller;

import com.cruise.recommender.service.PortRdfService;
import com.cruise.recommender.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.QuerySolution;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Port RDF dataset operations
 */
@RestController
@RequestMapping("/rdf/ports")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class PortRdfController {
    
    private final PortRdfService portRdfService;
    private final StatisticsService statisticsService;
    
    /**
     * Create/update RDF dataset for all ports
     * POST /api/v1/rdf/ports/create-dataset
     */
    @PostMapping("/create-dataset")
    public ResponseEntity<?> createPortsDataset() {
        try {
            log.info("Creating ports RDF dataset");
            portRdfService.createPortsRdfDataset();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Ports RDF dataset created successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error creating ports RDF dataset", e);
            String errorMessage = e.getMessage();
            if (e.getCause() != null) {
                errorMessage += " - Cause: " + e.getCause().getMessage();
            }
            return ResponseEntity.status(500)
                    .body(Map.of("success", false, "error", errorMessage, "details", e.getClass().getSimpleName()));
        }
    }
    
    /**
     * Query ports by country
     * GET /api/rdf/ports/by-country?country=Spain
     */
    @GetMapping("/by-country")
    public ResponseEntity<?> getPortsByCountry(@RequestParam String country) {
        try {
            List<QuerySolution> results = portRdfService.findPortsByCountry(country);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("country", country);
            response.put("count", results.size());
            response.put("ports", results);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error querying ports by country", e);
            return ResponseEntity.status(500)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
    
    /**
     * Query ports by activity/interest
     * GET /api/rdf/ports/by-activity?activity=swimming
     */
    @GetMapping("/by-activity")
    public ResponseEntity<?> getPortsByActivity(@RequestParam String activity) {
        try {
            List<QuerySolution> results = portRdfService.findPortsByActivity(activity);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("activity", activity);
            response.put("count", results.size());
            response.put("ports", results);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error querying ports by activity", e);
            return ResponseEntity.status(500)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
    
    /**
     * Get port details
     * GET /api/rdf/ports/{portCode}
     */
    @GetMapping("/{portCode}")
    public ResponseEntity<?> getPortDetails(@PathVariable String portCode) {
        try {
            QuerySolution result = portRdfService.getPortDetails(portCode);
            
            if (result == null) {
                return ResponseEntity.notFound().build();
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("portCode", portCode);
            response.put("data", result);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting port details", e);
            return ResponseEntity.status(500)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
    
    /**
     * Execute custom SPARQL query
     * POST /api/rdf/ports/query
     * Body: { "query": "SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 10" }
     */
    @PostMapping("/query")
    public ResponseEntity<?> executeSparqlQuery(@RequestBody Map<String, String> request) {
        long startTime = System.currentTimeMillis();
        String queryType = "custom_query";
        boolean success = false;
        String errorType = null;
        int resultCount = 0;
        
        try {
            String query = request.get("query");
            if (query == null || query.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "error", "Query parameter is required"));
            }
            
            // Determine query type from query content
            queryType = determineQueryType(query);
            
            List<QuerySolution> results = portRdfService.queryPorts(query);
            resultCount = results.size();
            success = true;
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", resultCount);
            response.put("results", results);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error executing SPARQL query", e);
            errorType = e.getClass().getSimpleName();
            return ResponseEntity.status(500)
                    .body(Map.of("success", false, "error", e.getMessage()));
        } finally {
            // Record statistics
            long durationMs = System.currentTimeMillis() - startTime;
            String queryHash = hashQuery(request.get("query"));
            statisticsService.recordSparqlQuery(
                queryType,
                success,
                durationMs,
                errorType,
                resultCount,
                queryHash
            );
        }
    }
    
    private String determineQueryType(String query) {
        String lowerQuery = query.toLowerCase();
        if (lowerQuery.contains("by-country") || lowerQuery.contains("dcterms:coverage")) {
            return "ports_by_country";
        } else if (lowerQuery.contains("by-activity") || lowerQuery.contains("skos:related")) {
            return "ports_by_activity";
        } else if (lowerQuery.contains("port details") || lowerQuery.contains("skos:altLabel")) {
            return "port_details";
        } else if (lowerQuery.contains("geo:location") || lowerQuery.contains("latitude") || lowerQuery.contains("longitude")) {
            return "geographic_proximity";
        } else if (lowerQuery.contains("berthsCapacity") || lowerQuery.contains("capacity")) {
            return "ports_with_capacity";
        } else if (lowerQuery.contains("select ?s ?p ?o") || lowerQuery.contains("select *")) {
            return "all_ports";
        }
        return "custom_query";
    }
    
    private String hashQuery(String query) {
        if (query == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(query.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return query.substring(0, Math.min(50, query.length()));
        }
    }
}

