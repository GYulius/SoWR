package com.cruise.recommender.service;

import com.cruise.recommender.entity.*;
import com.cruise.recommender.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for recommending shore excursions based on passenger interests
 * Focuses on must-see highlights and personalized touristic experiences
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ShoreExcursionRecommendationService {
    
    private final ShoreExcursionRepository shoreExcursionRepository;
    private final PassengerRepository passengerRepository;
    private final PassengerInterestRepository passengerInterestRepository;
    
    /**
     * Generate personalized shore excursion recommendations for a passenger
     */
    public List<ShoreExcursionRecommendation> recommendShoreExcursions(
            Long passengerId, Long portId) {
        
        log.info("Generating shore excursion recommendations for passenger {} at port {}", 
                passengerId, portId);
        
        Passenger passenger = passengerRepository.findById(passengerId)
                .orElseThrow(() -> new RuntimeException("Passenger not found: " + passengerId));
        
        // Get passenger interests (voluntary + social media)
        List<PassengerInterest> interests = passengerInterestRepository.findByPassenger(passenger);
        
        // Get available shore excursions for the port
        List<ShoreExcursion> availableExcursions = shoreExcursionRepository.findByPortId(portId);
        
        // Score each excursion based on passenger interests
        List<ShoreExcursionRecommendation> recommendations = availableExcursions.stream()
                .map(excursion -> scoreExcursion(excursion, passenger, interests))
                .filter(rec -> rec.getScore() >= 0.3) // Minimum threshold
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(10)
                .collect(Collectors.toList());
        
        log.info("Generated {} shore excursion recommendations", recommendations.size());
        
        return recommendations;
    }
    
    /**
     * Get must-see highlights for a port
     */
    @Transactional(readOnly = true)
    public List<ShoreExcursion> getMustSeeHighlights(Long portId) {
        log.info("Getting must-see highlights for port: {}", portId);
        
        return shoreExcursionRepository.findByPortIdAndMustSeeHighlightTrue(portId);
    }
    
    /**
     * Get personalized must-see highlights based on passenger interests
     */
    public List<ShoreExcursionRecommendation> getPersonalizedMustSeeHighlights(
            Long passengerId, Long portId) {
        
        log.info("Getting personalized must-see highlights for passenger {} at port {}", 
                passengerId, portId);
        
        List<ShoreExcursion> mustSeeHighlights = getMustSeeHighlights(portId);
        Passenger passenger = passengerRepository.findById(passengerId)
                .orElseThrow(() -> new RuntimeException("Passenger not found: " + passengerId));
        
        List<PassengerInterest> interests = passengerInterestRepository.findByPassenger(passenger);
        
        return mustSeeHighlights.stream()
                .map(excursion -> scoreExcursion(excursion, passenger, interests))
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .collect(Collectors.toList());
    }
    
    /**
     * Score an excursion based on passenger interests and preferences
     */
    private ShoreExcursionRecommendation scoreExcursion(
            ShoreExcursion excursion, 
            Passenger passenger, 
            List<PassengerInterest> interests) {
        
        double score = 0.0;
        List<String> reasons = new ArrayList<>();
        
        // Base score from popularity
        if (excursion.getPopularityScore() != null) {
            score += excursion.getPopularityScore() * 0.2;
            reasons.add("Popularity: " + String.format("%.1f%%", excursion.getPopularityScore() * 100));
        }
        
        // Interest matching
        double interestScore = calculateInterestMatch(excursion, interests);
        score += interestScore * 0.4;
        if (interestScore > 0) {
            reasons.add("Interest match: " + String.format("%.1f%%", interestScore * 100));
        }
        
        // Must-see highlight boost
        if (excursion.getMustSeeHighlight()) {
            score += 0.2;
            reasons.add("Must-see highlight");
        }
        
        // Rating boost
        if (excursion.getRating() != null && excursion.getRating().doubleValue() >= 4.0) {
            score += 0.1;
            reasons.add("High rating: " + excursion.getRating());
        }
        
        // Budget compatibility
        if (isBudgetCompatible(excursion, passenger)) {
            score += 0.05;
            reasons.add("Budget compatible");
        }
        
        // Group size compatibility
        if (isGroupSizeCompatible(excursion, passenger)) {
            score += 0.05;
            reasons.add("Group size compatible");
        }
        
        return ShoreExcursionRecommendation.builder()
                .excursion(excursion)
                .score(Math.min(score, 1.0))
                .reasons(reasons)
                .build();
    }
    
    /**
     * Calculate interest match score
     */
    private double calculateInterestMatch(ShoreExcursion excursion, List<PassengerInterest> interests) {
        if (interests.isEmpty()) return 0.0;
        
        // Map excursion category to interest categories
        String excursionCategory = excursion.getCategory().getName();
        
        double totalScore = 0.0;
        int matches = 0;
        
        for (PassengerInterest interest : interests) {
            if (matchesInterestCategory(excursionCategory, interest.getInterestCategory())) {
                totalScore += interest.getConfidenceScore();
                matches++;
            }
        }
        
        return matches > 0 ? totalScore / interests.size() : 0.0;
    }
    
    /**
     * Check if excursion category matches interest category
     */
    private boolean matchesInterestCategory(String excursionCategory, String interestCategory) {
        // Simple matching logic - can be enhanced with semantic matching
        return excursionCategory.equalsIgnoreCase(interestCategory) ||
               excursionCategory.toLowerCase().contains(interestCategory.toLowerCase()) ||
               interestCategory.toLowerCase().contains(excursionCategory.toLowerCase());
    }
    
    /**
     * Check budget compatibility
     */
    private boolean isBudgetCompatible(ShoreExcursion excursion, Passenger passenger) {
        if (passenger.getBudgetRange() == null || excursion.getPricePerPerson() == null) {
            return true; // Assume compatible if not specified
        }
        
        // Simple budget matching logic
        return true; // Enhanced logic would check actual price ranges
    }
    
    /**
     * Check group size compatibility
     */
    private boolean isGroupSizeCompatible(ShoreExcursion excursion, Passenger passenger) {
        if (passenger.getGroupSize() == null || excursion.getMaxGroupSize() == null) {
            return true;
        }
        
        return passenger.getGroupSize() <= excursion.getMaxGroupSize();
    }
    
    // DTO
    @lombok.Data
    @lombok.Builder
    public static class ShoreExcursionRecommendation {
        private ShoreExcursion excursion;
        private Double score;
        private List<String> reasons;
    }
}
