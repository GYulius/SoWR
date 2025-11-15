package com.cruise.recommender.dto;

import com.cruise.recommender.service.MealVenueRecommendationService;
import com.cruise.recommender.service.ShoreExcursionRecommendationService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for passenger recommendation response
 * Contains personalized recommendations for shore excursions and meal venues
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassengerRecommendationResponse {
    
    private Long passengerId;
    private Long portId;
    private List<ShoreExcursionRecommendationService.ShoreExcursionRecommendation> shoreExcursions;
    private List<ShoreExcursionRecommendationService.ShoreExcursionRecommendation> mustSeeHighlights;
    private List<MealVenueRecommendationService.MealVenueRecommendation> breakfastVenues;
    private List<MealVenueRecommendationService.MealVenueRecommendation> lunchVenues;
}
