package com.cruise.recommender.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for Recommendation responses
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationResponse {
    
    private Long id;
    private String itemType;
    private Long itemId;
    private BigDecimal score;
    private String reasoning;
    private String algorithmVersion;
    private LocalDateTime createdAt;
}
