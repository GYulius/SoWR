package com.cruise.recommender.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for Publisher requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublisherRequest {
    
    @NotBlank(message = "Business name is required")
    @Size(max = 255, message = "Business name must not exceed 255 characters")
    private String businessName;
    
    @NotBlank(message = "Business type is required")
    private String businessType;
    
    private String description;
    
    @Email(message = "Invalid email format")
    private String contactEmail;
    
    private String contactPhone;
    
    private String website;
    
    private String address;
    
    private Double latitude;
    
    private Double longitude;
}
