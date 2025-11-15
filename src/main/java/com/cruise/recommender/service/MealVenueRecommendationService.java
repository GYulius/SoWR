package com.cruise.recommender.service;

import com.cruise.recommender.entity.*;
import com.cruise.recommender.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for recommending breakfast and lunch venues
 * Focuses on locally active venues during port calls
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MealVenueRecommendationService {
    
    private final MealVenueRepository mealVenueRepository;
    private final PassengerRepository passengerRepository;
    private final CruiseScheduleRepository cruiseScheduleRepository;
    
    /**
     * Recommend breakfast venues for a passenger at a port
     */
    public List<MealVenueRecommendation> recommendBreakfastVenues(
            Long passengerId, Long portId, LocalTime preferredTime) {
        
        log.info("Recommending breakfast venues for passenger {} at port {} at {}", 
                passengerId, portId, preferredTime);
        
        Passenger passenger = passengerRepository.findById(passengerId)
                .orElseThrow(() -> new RuntimeException("Passenger not found: " + passengerId));
        
        // Get cruise schedule to know arrival time
        CruiseSchedule schedule = passenger.getCruiseSchedule();
        
        // Get locally active breakfast venues
        List<MealVenue> breakfastVenues = mealVenueRepository.findByPortIdAndMealTypesServedContaining(
                portId, "BREAKFAST");
        
        // Filter venues active during port call time
        List<MealVenue> activeVenues = breakfastVenues.stream()
                .filter(venue -> isActiveDuringPortCall(venue, schedule, preferredTime))
                .collect(Collectors.toList());
        
        // Score and rank venues
        List<MealVenueRecommendation> recommendations = activeVenues.stream()
                .map(venue -> scoreMealVenue(venue, passenger, "BREAKFAST", preferredTime))
                .filter(rec -> rec.getScore() >= 0.3)
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(10)
                .collect(Collectors.toList());
        
        log.info("Generated {} breakfast venue recommendations", recommendations.size());
        
        return recommendations;
    }
    
    /**
     * Recommend lunch venues for a passenger at a port
     */
    public List<MealVenueRecommendation> recommendLunchVenues(
            Long passengerId, Long portId, LocalTime preferredTime) {
        
        log.info("Recommending lunch venues for passenger {} at port {} at {}", 
                passengerId, portId, preferredTime);
        
        Passenger passenger = passengerRepository.findById(passengerId)
                .orElseThrow(() -> new RuntimeException("Passenger not found: " + passengerId));
        
        CruiseSchedule schedule = passenger.getCruiseSchedule();
        
        // Get locally active lunch venues
        List<MealVenue> lunchVenues = mealVenueRepository.findByPortIdAndMealTypesServedContaining(
                portId, "LUNCH");
        
        // Filter venues active during port call time
        List<MealVenue> activeVenues = lunchVenues.stream()
                .filter(venue -> isActiveDuringPortCall(venue, schedule, preferredTime))
                .collect(Collectors.toList());
        
        // Score and rank venues
        List<MealVenueRecommendation> recommendations = activeVenues.stream()
                .map(venue -> scoreMealVenue(venue, passenger, "LUNCH", preferredTime))
                .filter(rec -> rec.getScore() >= 0.3)
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(10)
                .collect(Collectors.toList());
        
        log.info("Generated {} lunch venue recommendations", recommendations.size());
        
        return recommendations;
    }
    
    /**
     * Get locally recommended venues (high local recommendation score)
     */
    @Transactional(readOnly = true)
    public List<MealVenue> getLocallyRecommendedVenues(Long portId, String mealType) {
        log.info("Getting locally recommended {} venues for port: {}", mealType, portId);
        
        return mealVenueRepository.findByPortIdAndLocalRecommendationScoreGreaterThan(
                portId, 0.7);
    }
    
    /**
     * Check if venue is active during port call
     */
    private boolean isActiveDuringPortCall(
            MealVenue venue, CruiseSchedule schedule, LocalTime preferredTime) {
        
        if (!venue.getIsActiveDuringPortCalls()) {
            return false;
        }
        
        // Check if preferred time is within venue's operating hours
        if ("BREAKFAST".equals(venue.getMealTypesServed())) {
            // Parse breakfast hours and check
            return isTimeWithinHours(preferredTime, venue.getBreakfastHours());
        } else if ("LUNCH".equals(venue.getMealTypesServed())) {
            return isTimeWithinHours(preferredTime, venue.getLunchHours());
        }
        
        return true;
    }
    
    /**
     * Check if time is within venue hours
     */
    private boolean isTimeWithinHours(LocalTime time, String hoursJson) {
        // Parse hours JSON and check if time is within range
        // Simplified for now
        return true;
    }
    
    /**
     * Score a meal venue based on passenger preferences
     */
    private MealVenueRecommendation scoreMealVenue(
            MealVenue venue, 
            Passenger passenger, 
            String mealType,
            LocalTime preferredTime) {
        
        double score = 0.0;
        List<String> reasons = new ArrayList<>();
        
        // Local recommendation score (high priority)
        if (venue.getLocalRecommendationScore() != null) {
            score += venue.getLocalRecommendationScore() * 0.3;
            reasons.add("Local recommendation: " + 
                    String.format("%.1f%%", venue.getLocalRecommendationScore() * 100));
        }
        
        // Rating
        if (venue.getRating() != null && venue.getRating().doubleValue() >= 4.0) {
            score += 0.2;
            reasons.add("High rating: " + venue.getRating());
        }
        
        // Popularity
        if (venue.getPopularityScore() != null) {
            score += venue.getPopularityScore() * 0.15;
            reasons.add("Popular venue");
        }
        
        // Tourist friendly
        if (venue.getTouristFriendly() != null && venue.getTouristFriendly()) {
            score += 0.1;
            reasons.add("Tourist friendly");
        }
        
        // English menu available
        if (venue.getEnglishMenuAvailable() != null && venue.getEnglishMenuAvailable()) {
            score += 0.05;
            reasons.add("English menu available");
        }
        
        // Walking distance (closer is better)
        if (venue.getWalkingDistanceFromPortMinutes() != null) {
            if (venue.getWalkingDistanceFromPortMinutes() <= 10) {
                score += 0.1;
                reasons.add("Close to port (< 10 min walk)");
            } else if (venue.getWalkingDistanceFromPortMinutes() <= 20) {
                score += 0.05;
                reasons.add("Moderate distance (< 20 min walk)");
            }
        }
        
        // Dietary restrictions compatibility
        if (isDietaryCompatible(venue, passenger)) {
            score += 0.1;
            reasons.add("Dietary needs compatible");
        }
        
        // Budget compatibility
        if (isBudgetCompatible(venue, passenger, mealType)) {
            score += 0.05;
            reasons.add("Budget compatible");
        }
        
        return MealVenueRecommendation.builder()
                .venue(venue)
                .mealType(mealType)
                .score(Math.min(score, 1.0))
                .reasons(reasons)
                .estimatedDuration(mealType.equals("BREAKFAST") ? 
                        venue.getTypicalBreakfastDurationMinutes() : 
                        venue.getTypicalLunchDurationMinutes())
                .build();
    }
    
    /**
     * Check dietary compatibility
     */
    private boolean isDietaryCompatible(MealVenue venue, Passenger passenger) {
        if (passenger.getDietaryRestrictions() == null || venue.getDietaryOptions() == null) {
            return true;
        }
        
        // Parse and check dietary options
        // Simplified for now
        return true;
    }
    
    /**
     * Check budget compatibility
     */
    private boolean isBudgetCompatible(MealVenue venue, Passenger passenger, String mealType) {
        if (passenger.getBudgetRange() == null) {
            return true;
        }
        
        // Check if venue's price range matches passenger's budget
        // Simplified for now
        return true;
    }
    
    // DTO
    @lombok.Data
    @lombok.Builder
    public static class MealVenueRecommendation {
        private MealVenue venue;
        private String mealType;
        private Double score;
        private List<String> reasons;
        private Integer estimatedDuration; // in minutes
    }
}
