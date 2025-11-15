package com.cruise.recommender.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for ship tracking response
 * Contains real-time AIS data for cruise ship positions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipTrackingResponse {
    
    private Long shipId;
    private String shipName;
    private String cruiseLine;
    private Double latitude;
    private Double longitude;
    private Double speed;
    private Double course;
    private String trackingStatus;
    private LocalDateTime lastUpdate;
}
