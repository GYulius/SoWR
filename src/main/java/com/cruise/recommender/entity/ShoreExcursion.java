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
import java.util.List;

/**
 * Shore Excursion entity for port activities
 */
@Entity
@Table(name = "shore_excursions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ShoreExcursion {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "port_id", nullable = false)
    private Port port;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;
    
    @Column(nullable = false)
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "excursion_type")
    @Enumerated(EnumType.STRING)
    private ExcursionType excursionType;
    
    @Column(name = "duration_hours")
    private Double durationHours;
    
    @Column(name = "difficulty_level")
    @Enumerated(EnumType.STRING)
    private DifficultyLevel difficultyLevel;
    
    @Column(name = "min_age")
    private Integer minAge;
    
    @Column(name = "max_group_size")
    private Integer maxGroupSize;
    
    @Column(name = "price_per_person", precision = 10, scale = 2)
    private BigDecimal pricePerPerson;
    
    @Column(nullable = false)
    private String currency;
    
    @Column(name = "departure_times", columnDefinition = "JSON")
    private String departureTimes; // Available departure times
    
    @Column(name = "must_see_highlight")
    @Builder.Default
    private Boolean mustSeeHighlight = false; // Is this a must-see highlight?
    
    @Column(name = "popularity_score")
    private Double popularityScore;
    
    @Column(name = "rating")
    private BigDecimal rating;
    
    @Column(name = "review_count")
    private Integer reviewCount;
    
    @Column(name = "includes_transportation")
    @Builder.Default
    private Boolean includesTransportation = false;
    
    @Column(name = "includes_meals")
    @Builder.Default
    private Boolean includesMeals = false;
    
    @Column(name = "meeting_point")
    private String meetingPoint;
    
    @Column(name = "languages_available", columnDefinition = "JSON")
    private String languagesAvailable;
    
    @Column(name = "accessibility_features", columnDefinition = "JSON")
    private String accessibilityFeatures;
    
    @Column(name = "what_to_bring", columnDefinition = "JSON")
    private String whatToBring;
    
    @Column(columnDefinition = "JSON")
    private String images;
    
    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Relationships
    @OneToMany(mappedBy = "shoreExcursion", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ShoreExcursionBooking> bookings;
    
    public enum ExcursionType {
        WALKING_TOUR, BUS_TOUR, BOAT_TOUR, ADVENTURE, CULTURAL, FOOD_TOUR, 
        SHOPPING_TOUR, NATURE, HISTORICAL, PHOTOGRAPHY, FAMILY_FRIENDLY
    }
    
    public enum DifficultyLevel {
        EASY, MODERATE, CHALLENGING, STRENUOUS
    }
}
