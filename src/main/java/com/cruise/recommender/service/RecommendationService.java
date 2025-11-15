package com.cruise.recommender.service;

import com.cruise.recommender.dto.RecommendationRequest;
import com.cruise.recommender.dto.RecommendationResponse;
import com.cruise.recommender.entity.Recommendation;
import com.cruise.recommender.entity.User;
import com.cruise.recommender.entity.Port;
import com.cruise.recommender.repository.RecommendationRepository;
import com.cruise.recommender.repository.UserRepository;
import com.cruise.recommender.repository.PortRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for Recommendation operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RecommendationService {
    
    private final RecommendationRepository recommendationRepository;
    private final UserRepository userRepository;
    private final PortRepository portRepository;
    private final KnowledgeGraphService knowledgeGraphService;
    
    /**
     * Generate personalized recommendations for a user
     */
    public List<RecommendationResponse> generateRecommendations(String userEmail, RecommendationRequest request) {
        log.info("Generating recommendations for user {} at port {}", userEmail, request.getPortId());
        
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));
        
        Port port = portRepository.findById(request.getPortId())
                .orElseThrow(() -> new RuntimeException("Port not found: " + request.getPortId()));
        
        // Clear existing recommendations for this user and port
        recommendationRepository.deleteByUserAndPort(user, port);
        
        // Generate new recommendations using ML algorithms
        List<Recommendation> recommendations = generateMLRecommendations(user, port, request);
        
        // Save recommendations
        List<Recommendation> savedRecommendations = recommendationRepository.saveAll(recommendations);
        
        // Convert to response DTOs
        return savedRecommendations.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Submit feedback on a recommendation
     */
    public void submitFeedback(String userEmail, Long recommendationId, 
                              Integer rating, String feedbackText, String interactionType) {
        log.info("Submitting feedback for recommendation {} by user {}", recommendationId, userEmail);
        
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));
        
        Recommendation recommendation = recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new RuntimeException("Recommendation not found: " + recommendationId));
        
        if (!recommendation.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }
        
        // Store user interaction for ML training
        storeUserInteraction(user, recommendation, rating, feedbackText, interactionType);
    }
    
    /**
     * Get recommendation history for a user
     */
    @Transactional(readOnly = true)
    public Page<RecommendationResponse> getRecommendationHistory(String userEmail, Long portId, Pageable pageable) {
        log.info("Getting recommendation history for user {}", userEmail);
        
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));
        
        Page<Recommendation> recommendations;
        if (portId != null) {
            Port port = portRepository.findById(portId)
                    .orElseThrow(() -> new RuntimeException("Port not found: " + portId));
            recommendations = recommendationRepository.findByUserAndPortOrderByCreatedAtDesc(user, port, pageable);
        } else {
            recommendations = recommendationRepository.findByUserOrderByCreatedAtDesc(user, pageable);
        }
        
        return recommendations.map(this::convertToResponse);
    }
    
    /**
     * Refresh recommendations for a user
     */
    public List<RecommendationResponse> refreshRecommendations(String userEmail, Long portId) {
        log.info("Refreshing recommendations for user {} at port {}", userEmail, portId);
        
        RecommendationRequest request = RecommendationRequest.builder()
                .portId(portId)
                .limit(20)
                .build();
        
        return generateRecommendations(userEmail, request);
    }
    
    /**
     * Get explanation for a specific recommendation
     */
    @Transactional(readOnly = true)
    public RecommendationResponse getRecommendationExplanation(String userEmail, Long recommendationId) {
        log.info("Getting explanation for recommendation {} by user {}", recommendationId, userEmail);
        
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));
        
        Recommendation recommendation = recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new RuntimeException("Recommendation not found: " + recommendationId));
        
        if (!recommendation.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }
        
        return convertToResponse(recommendation);
    }
    
    /**
     * Generate ML-based recommendations
     */
    private List<Recommendation> generateMLRecommendations(User user, Port port, RecommendationRequest request) {
        // This is a simplified implementation
        // In a real system, this would use sophisticated ML algorithms
        
        log.info("Generating ML recommendations for user {} at port {}", user.getId(), port.getId());
        
        // For now, return empty list - would be implemented with actual ML logic
        return List.of();
    }
    
    /**
     * Store user interaction for ML training
     */
    private void storeUserInteraction(User user, Recommendation recommendation, 
                                    Integer rating, String feedbackText, String interactionType) {
        // Store interaction in database for future ML training
        log.info("Storing user interaction: user={}, recommendation={}, rating={}, type={}", 
                user.getId(), recommendation.getId(), rating, interactionType);
    }
    
    /**
     * Convert Recommendation entity to Response DTO
     */
    private RecommendationResponse convertToResponse(Recommendation recommendation) {
        return RecommendationResponse.builder()
                .id(recommendation.getId())
                .itemType(recommendation.getItemType().name())
                .itemId(recommendation.getItemId())
                .score(recommendation.getScore())
                .reasoning(recommendation.getReasoning())
                .algorithmVersion(recommendation.getAlgorithmVersion())
                .createdAt(recommendation.getCreatedAt())
                .build();
    }
}
