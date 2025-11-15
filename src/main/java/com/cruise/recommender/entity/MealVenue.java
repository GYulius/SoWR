package com.cruise.recommender.entity;

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
import java.time.LocalTime;

/**
 * Meal Venue entity for breakfast and lunch recommendations
 */
@Entity
@Table(name = "meal_venues")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class MealVenue {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "port_id", nullable = false)
    private Port port;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant; // Link to restaurant if exists
    
    @Column(nullable = false)
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "venue_type")
    @Enumerated(EnumType.STRING)
    private VenueType venueType;
    
    @Column(name = "meal_types_served", columnDefinition = "JSON")
    private String mealTypesServed; // BREAKFAST, LUNCH, BRUNCH, etc.
    
    @Column(name = "breakfast_hours", columnDefinition = "JSON")
    private String breakfastHours; // Opening hours for breakfast
    
    @Column(name = "lunch_hours", columnDefinition = "JSON")
    private String lunchHours; // Opening hours for lunch
    
    @Column(name = "is_active_during_port_calls")
    @Builder.Default
    private Boolean isActiveDuringPortCalls = true;
    
    @Column(name = "typical_breakfast_duration_minutes")
    private Integer typicalBreakfastDurationMinutes;
    
    @Column(name = "typical_lunch_duration_minutes")
    private Integer typicalLunchDurationMinutes;
    
    @Column(name = "breakfast_price_range")
    @Enumerated(EnumType.STRING)
    private PriceRange breakfastPriceRange;
    
    @Column(name = "lunch_price_range")
    @Enumerated(EnumType.STRING)
    private PriceRange lunchPriceRange;
    
    @Column(name = "average_breakfast_price", precision = 10, scale = 2)
    private BigDecimal averageBreakfastPrice;
    
    @Column(name = "average_lunch_price", precision = 10, scale = 2)
    private BigDecimal averageLunchPrice;
    
    @Column(nullable = false)
    private String currency;
    
    @Column(name = "cuisine_type")
    private String cuisineType;
    
    @Column(name = "dietary_options", columnDefinition = "JSON")
    private String dietaryOptions; // VEGETARIAN, VEGAN, GLUTEN_FREE, etc.
    
    @Column(name = "reservation_required")
    @Builder.Default
    private Boolean reservationRequired = false;
    
    @Column(name = "walking_distance_from_port_minutes")
    private Integer walkingDistanceFromPortMinutes;
    
    @Column(name = "rating")
    private BigDecimal rating;
    
    @Column(name = "review_count")
    private Integer reviewCount;
    
    @Column(name = "popularity_score")
    private Double popularityScore;
    
    @Column(name = "local_recommendation_score")
    private Double localRecommendationScore; // How much locals recommend it
    
    @Column(name = "tourist_friendly")
    @Builder.Default
    private Boolean touristFriendly = true;
    
    @Column(name = "english_menu_available")
    @Builder.Default
    private Boolean englishMenuAvailable = true;
    
    @Column(columnDefinition = "JSON")
    private String images;
    
    @Column(name = "address")
    private String address;
    
    @Column(name = "latitude")
    private Double latitude;
    
    @Column(name = "longitude")
    private Double longitude;
    
    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public enum VenueType {
        RESTAURANT, CAFE, BISTRO, BAKERY, MARKET, FOOD_STALL, FOOD_TOUR_STOP
    }
    
    public enum PriceRange {
        BUDGET, MODERATE, EXPENSIVE, LUXURY
    }
}
