package com.cruise.recommender.service;

import com.cruise.recommender.entity.Passenger;
import com.cruise.recommender.entity.PassengerInterest;
import com.cruise.recommender.repository.PassengerInterestRepository;
import com.cruise.recommender.repository.PassengerRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for building user-item interaction matrix
 * 
 * Extracts interaction data from:
 * - MySQL: User preferences, voluntary interests, explicit ratings
 * - SPARQL: Social media likes, Facebook interests, YouTube preferences
 * 
 * Matrix format:
 * - Rows: User IDs (passenger:123)
 * - Columns: Item IDs (attractions, dishes)
 * - Values: Interaction strength (1-5 scale)
 *   - 1: Facebook "Like"
 *   - 3: Voluntary preference on app
 *   - 5: Explicit rating/preference
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserItemMatrixService {
    
    private final PassengerInterestRepository passengerInterestRepository;
    private final PassengerRepository passengerRepository;
    
    /**
     * Build user-item interaction matrix for ALS
     * 
     * @param passengerId Target passenger ID
     * @param portId Port ID for context
     * @param socialInterests Social media interests from SPARQL
     * @param portFeatures Port features (attractions, dishes) from SPARQL
     * @return UserItemMatrix ready for ALS training
     */
    public UserItemMatrix buildMatrix(
            Long passengerId,
            Long portId,
            List<String> socialInterests,
            List<RecommendationOrchestratorService.PortFeature> portFeatures) {
        
        log.info("Building user-item matrix for passenger {} at port {}", passengerId, portId);
        
        UserItemMatrix matrix = new UserItemMatrix();
        matrix.setUserId(passengerId);
        matrix.setPortId(portId);
        
        List<Interaction> interactions = new ArrayList<>();
        
        // Step 1: Get explicit preferences from MySQL
        interactions.addAll(getExplicitInteractions(passengerId, portFeatures));
        
        // Step 2: Get social media interactions (weighted lower)
        interactions.addAll(getSocialMediaInteractions(passengerId, socialInterests, portFeatures));
        
        // Step 3: Get similar users' preferences (for collaborative filtering)
        interactions.addAll(getSimilarUserInteractions(passengerId, socialInterests, portFeatures));
        
        matrix.setInteractions(interactions);
        
        log.info("Built matrix with {} interactions for passenger {}", 
                interactions.size(), passengerId);
        
        return matrix;
    }
    
    /**
     * Get explicit interactions from MySQL (voluntary preferences)
     * These have the highest weight (5)
     */
    private List<Interaction> getExplicitInteractions(
            Long passengerId,
            List<RecommendationOrchestratorService.PortFeature> portFeatures) {
        
        List<Interaction> interactions = new ArrayList<>();
        
        try {
            // Get passenger
            Optional<Passenger> passengerOpt = passengerRepository.findById(passengerId);
            if (passengerOpt.isEmpty()) {
                return interactions;
            }
            
            Passenger passenger = passengerOpt.get();
            
            // Get passenger interests from database
            List<PassengerInterest> interests = passengerInterestRepository
                    .findByPassenger(passenger);
            
            // Match interests to port features
            for (PassengerInterest interest : interests) {
                
                String keyword = interest.getInterestKeyword();
                if (keyword == null) continue;
                
                for (RecommendationOrchestratorService.PortFeature feature : portFeatures) {
                    if (feature.getName() != null && 
                        feature.getName().toLowerCase().contains(keyword.toLowerCase())) {
                        
                        Interaction interaction = Interaction.builder()
                                .userId(passengerId)
                                .itemId(feature.getName())
                                .itemName(feature.getName())
                                .category(feature.getCategory())
                                .rating(5.0) // Explicit preference = highest rating
                                .source("EXPLICIT")
                                .build();
                        
                        interactions.add(interaction);
                    }
                }
            }
            
        } catch (Exception e) {
            log.warn("Error getting explicit interactions: {}", e.getMessage());
        }
        
        return interactions;
    }
    
    /**
     * Get social media interactions (Facebook likes, etc.)
     * These have medium weight (1-3)
     */
    private List<Interaction> getSocialMediaInteractions(
            Long passengerId,
            List<String> socialInterests,
            List<RecommendationOrchestratorService.PortFeature> portFeatures) {
        
        List<Interaction> interactions = new ArrayList<>();
        
        for (String interest : socialInterests) {
            for (RecommendationOrchestratorService.PortFeature feature : portFeatures) {
                if (feature.getName() != null && 
                    feature.getName().toLowerCase().contains(interest.toLowerCase())) {
                    
                    Interaction interaction = Interaction.builder()
                            .userId(passengerId)
                            .itemId(feature.getName())
                            .itemName(feature.getName())
                            .category(feature.getCategory())
                            .rating(1.0) // Facebook like = lower rating
                            .source("SOCIAL_MEDIA")
                            .build();
                    
                    interactions.add(interaction);
                }
            }
        }
        
        return interactions;
    }
    
    /**
     * Get interactions from similar users (collaborative filtering)
     * Uses SPARQL to find users with similar interests who visited the port
     */
    private List<Interaction> getSimilarUserInteractions(
            Long passengerId,
            List<String> socialInterests,
            List<RecommendationOrchestratorService.PortFeature> portFeatures) {
        
        // This would query SPARQL for:
        // "Find users who liked these interests and visited this port"
        // For now, return empty list - can be enhanced with SPARQL queries
        
        log.debug("Similar user interactions not yet implemented");
        return Collections.emptyList();
    }
    
    /**
     * User-Item Interaction Matrix
     */
    @Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UserItemMatrix {
        private Long userId;
        private Long portId;
        private List<Interaction> interactions;
        
        /**
         * Convert to Spark DataFrame format (list of rows)
         * Each row: [userId, itemId, rating]
         */
        public List<Object[]> toSparkRows() {
            return interactions.stream()
                    .map(i -> new Object[]{
                            i.getUserId(),
                            i.getItemId(),
                            i.getRating()
                    })
                    .collect(Collectors.toList());
        }
    }
    
    /**
     * User-Item Interaction
     */
    @Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Interaction {
        private Long userId;
        private String itemId;
        private String itemName;
        private String category; // Added for better matching
        private Double rating; // 1.0 to 5.0
        private String source; // EXPLICIT, SOCIAL_MEDIA, COLLABORATIVE
    }
}
