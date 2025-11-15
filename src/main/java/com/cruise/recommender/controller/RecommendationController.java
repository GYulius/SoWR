package com.cruise.recommender.controller;

import com.cruise.recommender.dto.RecommendationRequest;
import com.cruise.recommender.dto.RecommendationResponse;
import com.cruise.recommender.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Recommendation operations
 */
@RestController
@RequestMapping("/recommendations")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Recommendations", description = "Recommendation management APIs")
public class RecommendationController {
    
    private final RecommendationService recommendationService;
    
    @GetMapping
    @Operation(summary = "Get personalized recommendations", 
               description = "Retrieve personalized recommendations for a user at a specific port")
    public ResponseEntity<List<RecommendationResponse>> getRecommendations(
            @Parameter(description = "Port ID") @RequestParam Long portId,
            @Parameter(description = "Maximum number of recommendations") @RequestParam(defaultValue = "10") Integer limit,
            @Parameter(description = "Comma-separated list of categories") @RequestParam(required = false) String categories,
            @Parameter(description = "User preferences JSON") @RequestParam(required = false) String preferences,
            Authentication authentication) {
        
        log.info("Getting recommendations for user {} at port {}", authentication.getName(), portId);
        
        RecommendationRequest request = RecommendationRequest.builder()
                .portId(portId)
                .limit(limit)
                .categories(categories)
                .preferences(preferences)
                .build();
        
        List<RecommendationResponse> recommendations = recommendationService.generateRecommendations(
                authentication.getName(), request);
        
        return ResponseEntity.ok(recommendations);
    }
    
    @PostMapping("/feedback")
    @Operation(summary = "Submit recommendation feedback", 
               description = "Submit feedback on a specific recommendation")
    public ResponseEntity<Void> submitFeedback(
            @Parameter(description = "Recommendation ID") @RequestParam Long recommendationId,
            @Parameter(description = "Rating (1-5)") @RequestParam Integer rating,
            @Parameter(description = "Feedback text") @RequestParam(required = false) String feedbackText,
            @Parameter(description = "Interaction type") @RequestParam String interactionType,
            Authentication authentication) {
        
        log.info("Submitting feedback for recommendation {} by user {}", recommendationId, authentication.getName());
        
        recommendationService.submitFeedback(authentication.getName(), recommendationId, 
                rating, feedbackText, interactionType);
        
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/history")
    @Operation(summary = "Get recommendation history", 
               description = "Retrieve user's recommendation history")
    public ResponseEntity<Page<RecommendationResponse>> getRecommendationHistory(
            @Parameter(description = "Port ID filter") @RequestParam(required = false) Long portId,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") Integer page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") Integer size,
            Authentication authentication) {
        
        log.info("Getting recommendation history for user {}", authentication.getName());
        
        Pageable pageable = Pageable.ofSize(size).withPage(page);
        Page<RecommendationResponse> history = recommendationService.getRecommendationHistory(
                authentication.getName(), portId, pageable);
        
        return ResponseEntity.ok(history);
    }
    
    @PostMapping("/refresh")
    @Operation(summary = "Refresh recommendations", 
               description = "Clear and regenerate recommendations for a user at a specific port")
    public ResponseEntity<List<RecommendationResponse>> refreshRecommendations(
            @Parameter(description = "Port ID") @RequestParam Long portId,
            Authentication authentication) {
        
        log.info("Refreshing recommendations for user {} at port {}", authentication.getName(), portId);
        
        List<RecommendationResponse> recommendations = recommendationService.refreshRecommendations(
                authentication.getName(), portId);
        
        return ResponseEntity.ok(recommendations);
    }
    
    @GetMapping("/explain/{id}")
    @Operation(summary = "Get recommendation explanation", 
               description = "Get detailed explanation for a specific recommendation")
    public ResponseEntity<RecommendationResponse> getRecommendationExplanation(
            @Parameter(description = "Recommendation ID") @PathVariable Long id,
            Authentication authentication) {
        
        log.info("Getting explanation for recommendation {} by user {}", id, authentication.getName());
        
        RecommendationResponse explanation = recommendationService.getRecommendationExplanation(
                authentication.getName(), id);
        
        return ResponseEntity.ok(explanation);
    }
}
