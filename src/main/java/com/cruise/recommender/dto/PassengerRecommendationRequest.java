package com.cruise.recommender.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for passenger recommendation request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassengerRecommendationRequest {
    
    private Long passengerId;
    private Long portId;
    private Boolean includeExcursions;
    private Boolean includeMeals;
}
