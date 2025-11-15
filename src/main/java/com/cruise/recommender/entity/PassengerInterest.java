package com.cruise.recommender.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Passenger Interest entity for tracking voluntarily expressed interests
 */
@Entity
@Table(name = "passenger_interests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class PassengerInterest {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_id", nullable = false)
    private Passenger passenger;
    
    @Column(name = "interest_category", nullable = false)
    private String interestCategory; // HISTORY, ART, NATURE, ADVENTURE, FOOD, SHOPPING, etc.
    
    @Column(name = "interest_keyword", nullable = false)
    private String interestKeyword; // Specific interest
    
    @Column(name = "source")
    @Enumerated(EnumType.STRING)
    private InterestSource source; // Where the interest was expressed
    
    @Column(name = "confidence_score")
    private Double confidenceScore; // 0.0 to 1.0
    
    @Column(name = "is_explicit")
    @Builder.Default
    private Boolean isExplicit = false; // Explicitly stated vs inferred
    
    @Column(name = "expressed_at")
    private LocalDateTime expressedAt;
    
    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public enum InterestSource {
        PROFILE_FORM,        // User filled out profile
        SOCIAL_MEDIA,        // Extracted from social media
        PREVIOUS_BOOKINGS,   // Inferred from past bookings
        SEARCH_HISTORY,      // From search queries
        INTERACTION_HISTORY, // From clicks, views, likes
        MANUAL_ENTRY         // User manually added
    }
}
