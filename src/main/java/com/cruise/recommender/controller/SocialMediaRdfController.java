package com.cruise.recommender.controller;

import com.cruise.recommender.service.SocialMediaRdfQueryService;
import com.cruise.recommender.service.SocialMediaRdfService;
import com.cruise.recommender.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Social Media RDF dataset operations
 * Provides endpoints for querying social media data via SPARQL
 */
@RestController
@RequestMapping("/rdf/social-media")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class SocialMediaRdfController {
    
    private final SocialMediaRdfQueryService queryService;
    private final StatisticsService statisticsService;
    
    /**
     * Find posts mentioning a specific port
     * GET /api/v1/rdf/social-media/posts/by-port?portCode=BARCELONA
     */
    @GetMapping("/posts/by-port")
    public ResponseEntity<?> getPostsByPort(@RequestParam String portCode) {
        try {
            List<Map<String, String>> results = queryService.findPostsByPort(portCode);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("portCode", portCode);
            response.put("count", results.size());
            response.put("posts", results);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error querying posts by port", e);
            return ResponseEntity.status(500)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
    
    /**
     * Find posts by keyword/interest
     * GET /api/v1/rdf/social-media/posts/by-keyword?keyword=snorkeling
     */
    @GetMapping("/posts/by-keyword")
    public ResponseEntity<?> getPostsByKeyword(@RequestParam String keyword) {
        try {
            List<Map<String, String>> results = queryService.findPostsByKeyword(keyword);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("keyword", keyword);
            response.put("count", results.size());
            response.put("posts", results);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error querying posts by keyword", e);
            return ResponseEntity.status(500)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
    
    /**
     * Find posts by hashtag
     * GET /api/v1/rdf/social-media/posts/by-hashtag?hashtag=cruiseport
     */
    @GetMapping("/posts/by-hashtag")
    public ResponseEntity<?> getPostsByHashtag(@RequestParam String hashtag) {
        try {
            List<Map<String, String>> results = queryService.findPostsByHashtag(hashtag);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("hashtag", hashtag);
            response.put("count", results.size());
            response.put("posts", results);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error querying posts by hashtag", e);
            return ResponseEntity.status(500)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
    
    /**
     * Find popular interests from social media
     * GET /api/v1/rdf/social-media/interests/popular?limit=20
     */
    @GetMapping("/interests/popular")
    public ResponseEntity<?> getPopularInterests(@RequestParam(defaultValue = "20") int limit) {
        try {
            List<Map<String, String>> results = queryService.findPopularInterests(limit);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("limit", limit);
            response.put("count", results.size());
            response.put("interests", results);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error querying popular interests", e);
            return ResponseEntity.status(500)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
    
    /**
     * Find popular ports mentioned in social media
     * GET /api/v1/rdf/social-media/ports/popular?limit=10
     */
    @GetMapping("/ports/popular")
    public ResponseEntity<?> getPopularPorts(@RequestParam(defaultValue = "10") int limit) {
        try {
            List<Map<String, String>> results = queryService.findPopularPorts(limit);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("limit", limit);
            response.put("count", results.size());
            response.put("ports", results);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error querying popular ports", e);
            return ResponseEntity.status(500)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
    
    /**
     * Find posts matching user interests
     * POST /api/v1/rdf/social-media/posts/matching-interests
     * Body: ["snorkeling", "art", "food"]
     */
    @PostMapping("/posts/matching-interests")
    public ResponseEntity<?> getPostsMatchingInterests(@RequestBody List<String> interests) {
        try {
            List<Map<String, String>> results = queryService.findPostsMatchingInterests(interests);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("interests", interests);
            response.put("count", results.size());
            response.put("posts", results);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error querying posts matching interests", e);
            return ResponseEntity.status(500)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
    
    /**
     * Find recommended ports based on social media activity
     * POST /api/v1/rdf/social-media/ports/recommended
     * Body: ["snorkeling", "art", "food"]
     */
    @PostMapping("/ports/recommended")
    public ResponseEntity<?> getRecommendedPorts(@RequestBody List<String> interests) {
        try {
            List<Map<String, String>> results = queryService.findRecommendedPortsBySocialMedia(interests);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("interests", interests);
            response.put("count", results.size());
            response.put("ports", results);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error querying recommended ports", e);
            return ResponseEntity.status(500)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
    
    /**
     * Find posts by location
     * GET /api/v1/rdf/social-media/posts/by-location?location=Barcelona
     */
    @GetMapping("/posts/by-location")
    public ResponseEntity<?> getPostsByLocation(@RequestParam String location) {
        try {
            List<Map<String, String>> results = queryService.findPostsByLocation(location);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("location", location);
            response.put("count", results.size());
            response.put("posts", results);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error querying posts by location", e);
            return ResponseEntity.status(500)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
    
    /**
     * Find posts by platform
     * GET /api/v1/rdf/social-media/posts/by-platform?platform=facebook
     */
    @GetMapping("/posts/by-platform")
    public ResponseEntity<?> getPostsByPlatform(@RequestParam String platform) {
        try {
            List<Map<String, String>> results = queryService.findPostsByPlatform(platform);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("platform", platform);
            response.put("count", results.size());
            response.put("posts", results);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error querying posts by platform", e);
            return ResponseEntity.status(500)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}

