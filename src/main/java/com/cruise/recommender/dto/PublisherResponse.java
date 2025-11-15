package com.cruise.recommender.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for Publisher responses
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublisherResponse {
    
    private Long id;
    private String businessName;
    private String businessType;
    private String description;
    private String contactEmail;
    private String contactPhone;
    private String website;
    private String address;
    private Double latitude;
    private Double longitude;
    private String verificationStatus;
    private Integer subscriptionCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
