package com.cruise.recommender.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Port entity representing cruise ports
 */
@Entity
@Table(name = "ports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Port {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "port_code", unique = true, nullable = false)
    private String portCode;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false)
    private String country;
    
    private String geo;
    
    private String region;
    
    @Column(nullable = false)
    private String city;
    
    @Column(nullable = false)
    private Double latitude;
    
    @Column(nullable = false)
    private Double longitude;
    
    @Column(name = "berths_capacity", nullable = false)
    private Integer berthsCapacity;
    
    @Column(columnDefinition = "JSON")
    private String facilities;
    
    @Column(columnDefinition = "JSON")
    private String amenities;
    
    @Column(name = "docking_fees", precision = 10, scale = 2)
    private BigDecimal dockingFees;
    
    @Builder.Default
    private String currency = "USD";
    
    private String timezone;
    
    private String language;
    
    @Column(name = "tourism1")
    private String tourism1; // Tourist Attractions (ATTRACTION category)
    
    @Column(name = "foodie_main", columnDefinition = "JSON")
    private String foodieMain; // Meal Venues - Main dishes (MEAL_VENUE category)
    
    @Column(name = "foodie_dessert", columnDefinition = "JSON")
    private String foodieDessert; // Meal Venues - Desserts (MEAL_VENUE category)
    
    @Column(name = "activities", columnDefinition = "JSON")
    private String activityKeywords; // Activities keywords (ACTIVITY category) - e.g., Swimming, Hiking, Snorkeling
    
    @Column(name = "restaurants", columnDefinition = "JSON")
    private String restaurantKeywords; // Restaurant types/cuisines (RESTAURANT category) - e.g., Italian, Asian, Mediterranean
    
    @Column(name = "excursions", columnDefinition = "JSON")
    private String excursions; // Shore Excursion keywords (EXCURSION category) - e.g., Adventure tours, Cultural tours
    
    @Column(name = "general_interests", columnDefinition = "JSON")
    private String generalInterests; // General Interests keywords (GENERAL category) - e.g., History, Art, Nature
    
    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Relationships - Ignored in JSON serialization to avoid lazy loading issues
    @JsonIgnore
    @OneToMany(mappedBy = "port", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Attraction> attractions;
    
    @JsonIgnore
    @OneToMany(mappedBy = "port", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Restaurant> restaurants;
    
    @JsonIgnore
    @OneToMany(mappedBy = "port", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Activity> activities;
    
    @JsonIgnore
    @OneToMany(mappedBy = "port", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CruiseSchedule> cruiseSchedules;
    
    @JsonIgnore
    @OneToMany(mappedBy = "port", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Recommendation> recommendations;
}

