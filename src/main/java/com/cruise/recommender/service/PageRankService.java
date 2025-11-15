package com.cruise.recommender.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.Graph;
import org.jgrapht.alg.scoring.PageRank;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for social network graph analysis using PageRank algorithm
 * Analyzes user relationships, publisher networks, and recommendation influence
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PageRankService {
    
    /**
     * Calculate PageRank for user social network
     * Users are nodes, subscriptions/interactions are edges
     */
    public Map<Long, Double> calculateUserPageRank(List<UserRelationship> relationships) {
        log.info("Calculating PageRank for user social network");
        
        // Build graph using JGraphT
        Graph<Long, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
        
        // Add vertices (users)
        relationships.stream()
                .flatMap(r -> List.of(r.getFromUserId(), r.getToUserId()).stream())
                .distinct()
                .forEach(graph::addVertex);
        
        // Add edges (relationships)
        relationships.forEach(r -> {
            if (graph.containsVertex(r.getFromUserId()) && graph.containsVertex(r.getToUserId())) {
                graph.addEdge(r.getFromUserId(), r.getToUserId());
            }
        });
        
        // Calculate PageRank
        PageRank<Long, DefaultEdge> pageRank = new PageRank<>(graph);
        Map<Long, Double> scores = pageRank.getScores();
        
        log.info("Calculated PageRank for {} users", scores.size());
        
        return scores;
    }
    
    /**
     * Calculate PageRank for publisher network
     * Publishers are nodes, subscriptions are edges (weighted by subscriber count)
     */
    public Map<Long, Double> calculatePublisherPageRank(List<PublisherRelationship> relationships) {
        log.info("Calculating PageRank for publisher network");
        
        Graph<Long, WeightedEdge> graph = new DefaultDirectedGraph<>(WeightedEdge.class);
        
        // Add vertices (publishers)
        relationships.stream()
                .flatMap(r -> List.of(r.getFromPublisherId(), r.getToPublisherId()).stream())
                .distinct()
                .forEach(graph::addVertex);
        
        // Add weighted edges
        relationships.forEach(r -> {
            if (graph.containsVertex(r.getFromPublisherId()) && 
                graph.containsVertex(r.getToPublisherId())) {
                WeightedEdge edge = graph.addEdge(r.getFromPublisherId(), r.getToPublisherId());
                if (edge != null) {
                    edge.setWeight(r.getWeight());
                }
            }
        });
        
        // Calculate PageRank with weights
        PageRank<Long, WeightedEdge> pageRank = new PageRank<>(graph);
        Map<Long, Double> scores = pageRank.getScores();
        
        log.info("Calculated PageRank for {} publishers", scores.size());
        
        return scores;
    }
    
    /**
     * Calculate PageRank for recommendation influence network
     * Items are nodes, recommendations are edges
     */
    public Map<Long, Double> calculateRecommendationInfluencePageRank(
            List<RecommendationEdge> recommendations) {
        log.info("Calculating PageRank for recommendation influence network");
        
        Graph<Long, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
        
        // Add vertices (items)
        recommendations.stream()
                .flatMap(r -> List.of(r.getFromItemId(), r.getToItemId()).stream())
                .distinct()
                .forEach(graph::addVertex);
        
        // Add edges (if item A is recommended, item B is also recommended)
        recommendations.forEach(r -> {
            if (graph.containsVertex(r.getFromItemId()) && graph.containsVertex(r.getToItemId())) {
                graph.addEdge(r.getFromItemId(), r.getToItemId());
            }
        });
        
        // Calculate PageRank
        PageRank<Long, DefaultEdge> pageRank = new PageRank<>(graph);
        Map<Long, Double> scores = pageRank.getScores();
        
        log.info("Calculated PageRank for {} items", scores.size());
        
        return scores;
    }
    
    /**
     * Mine social network graph to find influential users
     */
    public List<InfluentialUser> findInfluentialUsers(Map<Long, Double> pageRankScores, int topN) {
        log.info("Finding top {} influential users", topN);
        
        return pageRankScores.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                .limit(topN)
                .map(entry -> InfluentialUser.builder()
                        .userId(entry.getKey())
                        .pageRankScore(entry.getValue())
                        .influenceLevel(calculateInfluenceLevel(entry.getValue()))
                        .build())
                .collect(Collectors.toList());
    }
    
    /**
     * Mine graph to find communities/clusters
     */
    public Map<Long, Integer> detectCommunities(List<UserRelationship> relationships) {
        log.info("Detecting communities in social network");
        
        // Simple community detection using connected components
        Map<Long, Integer> communities = new HashMap<>();
        int communityId = 0;
        
        // This is a simplified version - in production, use proper community detection algorithms
        // like Louvain, Label Propagation, or Infomap
        
        for (UserRelationship rel : relationships) {
            Long from = rel.getFromUserId();
            Long to = rel.getToUserId();
            
            if (!communities.containsKey(from) && !communities.containsKey(to)) {
                communities.put(from, communityId);
                communities.put(to, communityId);
                communityId++;
            } else if (communities.containsKey(from)) {
                communities.put(to, communities.get(from));
            } else if (communities.containsKey(to)) {
                communities.put(from, communities.get(to));
            }
        }
        
        log.info("Detected {} communities", communityId);
        
        return communities;
    }
    
    /**
     * Calculate influence level based on PageRank score
     */
    private String calculateInfluenceLevel(Double score) {
        if (score > 0.1) return "VERY_HIGH";
        if (score > 0.05) return "HIGH";
        if (score > 0.01) return "MEDIUM";
        return "LOW";
    }
    
    // DTOs
    @lombok.Data
    @lombok.Builder
    public static class UserRelationship {
        private Long fromUserId;
        private Long toUserId;
        private String relationshipType; // SUBSCRIPTION, INTERACTION, SIMILAR_INTERESTS
    }
    
    @lombok.Data
    @lombok.Builder
    public static class PublisherRelationship {
        private Long fromPublisherId;
        private Long toPublisherId;
        private Double weight; // Number of shared subscribers
    }
    
    @lombok.Data
    @lombok.Builder
    public static class RecommendationEdge {
        private Long fromItemId;
        private Long toItemId;
        private Double weight; // Co-occurrence frequency
    }
    
    @lombok.Data
    @lombok.Builder
    public static class InfluentialUser {
        private Long userId;
        private Double pageRankScore;
        private String influenceLevel;
    }
    
    // Custom weighted edge for JGraphT
    public static class WeightedEdge extends DefaultEdge {
        private double weight = 1.0;
        
        public double getWeight() {
            return weight;
        }
        
        public void setWeight(double weight) {
            this.weight = weight;
        }
    }
}
