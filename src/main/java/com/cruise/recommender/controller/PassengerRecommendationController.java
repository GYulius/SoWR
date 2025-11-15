package com.cruise.recommender.controller;

import com.cruise.recommender.dto.PassengerRecommendationRequest;
import com.cruise.recommender.dto.PassengerRecommendationResponse;
import com.cruise.recommender.service.MealVenueRecommendationService;
import com.cruise.recommender.service.ShoreExcursionRecommendationService;
import com.cruise.recommender.service.SocialMediaAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.List;

/**
 * REST Controller for Passenger-Focused Recommendations
 * Provides personalized recommendations based on passenger interests and social media analysis
 */
@RestController
@RequestMapping("/passengers")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Passenger Recommendations", 
     description = "Passenger-focused recommendations for shore excursions and meal venues")
public class PassengerRecommendationController {
    
    private final ShoreExcursionRecommendationService shoreExcursionService;
    private final MealVenueRecommendationService mealVenueService;
    private final SocialMediaAnalysisService socialMediaService;
    
    @GetMapping("/{passengerId}/recommendations")
    @Operation(summary = "Get comprehensive recommendations for passenger", 
               description = "Get personalized recommendations including shore excursions and meal venues")
    public ResponseEntity<PassengerRecommendationResponse> getPassengerRecommendations(
            @Parameter(description = "Passenger ID") @PathVariable Long passengerId,
            @Parameter(description = "Port ID") @RequestParam Long portId,
            @Parameter(description = "Include shore excursions") @RequestParam(defaultValue = "true") Boolean includeExcursions,
            @Parameter(description = "Include meal venues") @RequestParam(defaultValue = "true") Boolean includeMeals) {
        
        log.info("Getting comprehensive recommendations for passenger {} at port {}", 
                passengerId, portId);
        
        PassengerRecommendationResponse response = PassengerRecommendationResponse.builder()
                .passengerId(passengerId)
                .portId(portId)
                .build();
        
        if (includeExcursions) {
            // Get shore excursion recommendations
            var excursions = shoreExcursionService.recommendShoreExcursions(passengerId, portId);
            response.setShoreExcursions(excursions);
            
            // Get must-see highlights
            var highlights = shoreExcursionService.getPersonalizedMustSeeHighlights(passengerId, portId);
            response.setMustSeeHighlights(highlights);
        }
        
        if (includeMeals) {
            // Get breakfast recommendations (default 8 AM)
            var breakfast = mealVenueService.recommendBreakfastVenues(
                    passengerId, portId, LocalTime.of(8, 0));
            response.setBreakfastVenues(breakfast);
            
            // Get lunch recommendations (default 1 PM)
            var lunch = mealVenueService.recommendLunchVenues(
                    passengerId, portId, LocalTime.of(13, 0));
            response.setLunchVenues(lunch);
        }
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/{passengerId}/shore-excursions")
    @Operation(summary = "Get shore excursion recommendations", 
               description = "Get personalized shore excursion recommendations based on passenger interests")
    public ResponseEntity<List<ShoreExcursionRecommendationService.ShoreExcursionRecommendation>> 
            getShoreExcursionRecommendations(
            @Parameter(description = "Passenger ID") @PathVariable Long passengerId,
            @Parameter(description = "Port ID") @RequestParam Long portId) {
        
        log.info("Getting shore excursion recommendations for passenger {} at port {}", 
                passengerId, portId);
        
        var recommendations = shoreExcursionService.recommendShoreExcursions(passengerId, portId);
        
        return ResponseEntity.ok(recommendations);
    }
    
    @GetMapping("/{passengerId}/must-see-highlights")
    @Operation(summary = "Get must-see highlights", 
               description = "Get personalized must-see highlights based on passenger interests")
    public ResponseEntity<List<ShoreExcursionRecommendationService.ShoreExcursionRecommendation>> 
            getMustSeeHighlights(
            @Parameter(description = "Passenger ID") @PathVariable Long passengerId,
            @Parameter(description = "Port ID") @RequestParam Long portId) {
        
        log.info("Getting must-see highlights for passenger {} at port {}", passengerId, portId);
        
        var highlights = shoreExcursionService.getPersonalizedMustSeeHighlights(passengerId, portId);
        
        return ResponseEntity.ok(highlights);
    }
    
    @GetMapping("/{passengerId}/breakfast-venues")
    @Operation(summary = "Get breakfast venue recommendations", 
               description = "Get locally active breakfast venues for port call")
    public ResponseEntity<List<MealVenueRecommendationService.MealVenueRecommendation>> 
            getBreakfastVenues(
            @Parameter(description = "Passenger ID") @PathVariable Long passengerId,
            @Parameter(description = "Port ID") @RequestParam Long portId,
            @Parameter(description = "Preferred time (HH:mm)") @RequestParam(required = false) String preferredTime) {
        
        log.info("Getting breakfast venue recommendations for passenger {} at port {}", 
                passengerId, portId);
        
        LocalTime time = preferredTime != null ? 
                LocalTime.parse(preferredTime) : LocalTime.of(8, 0);
        
        var recommendations = mealVenueService.recommendBreakfastVenues(passengerId, portId, time);
        
        return ResponseEntity.ok(recommendations);
    }
    
    @GetMapping("/{passengerId}/lunch-venues")
    @Operation(summary = "Get lunch venue recommendations", 
               description = "Get locally active lunch venues for port call")
    public ResponseEntity<List<MealVenueRecommendationService.MealVenueRecommendation>> 
            getLunchVenues(
            @Parameter(description = "Passenger ID") @PathVariable Long passengerId,
            @Parameter(description = "Port ID") @RequestParam Long portId,
            @Parameter(description = "Preferred time (HH:mm)") @RequestParam(required = false) String preferredTime) {
        
        log.info("Getting lunch venue recommendations for passenger {} at port {}", 
                passengerId, portId);
        
        LocalTime time = preferredTime != null ? 
                LocalTime.parse(preferredTime) : LocalTime.of(13, 0);
        
        var recommendations = mealVenueService.recommendLunchVenues(passengerId, portId, time);
        
        return ResponseEntity.ok(recommendations);
    }
    
    @PostMapping("/{passengerId}/analyze-social-media")
    @Operation(summary = "Analyze passenger social media", 
               description = "Trigger social media analysis for passenger to extract interests")
    public ResponseEntity<Void> analyzeSocialMedia(
            @Parameter(description = "Passenger ID") @PathVariable Long passengerId) {
        
        log.info("Triggering social media analysis for passenger: {}", passengerId);
        
        socialMediaService.analyzePassengerSocialMedia(passengerId);
        
        return ResponseEntity.ok().build();
    }
}
