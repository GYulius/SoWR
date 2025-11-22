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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Passenger entity representing guests on cruise ships
 * Priority entity for analytics and recommendations
 */
@Entity
@Table(name = "passengers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Passenger {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cruise_schedule_id", nullable = false)
    private CruiseSchedule cruiseSchedule;
    
    @Column(name = "cabin_number")
    private String cabinNumber;
    
    @Column(name = "dining_preference")
    private String diningPreference; // EARLY, LATE, ANYTIME
    
    @Column(name = "special_occasions", columnDefinition = "JSON")
    private String specialOccasions; // Birthdays, anniversaries, etc.
    
    @Column(name = "accessibility_needs", columnDefinition = "JSON")
    private String accessibilityNeeds;
    
    @Column(name = "dietary_restrictions", columnDefinition = "JSON")
    private String dietaryRestrictions;
    
    @Column(name = "voluntary_interests", columnDefinition = "JSON")
    private String voluntaryInterests; // Explicitly expressed interests
    
    @Column(name = "social_media_handles", columnDefinition = "JSON")
    private String socialMediaHandles; // Twitter, Instagram, Facebook handles
    
    @Column(name = "social_media_consent")
    @Builder.Default
    private Boolean socialMediaConsent = false;
    
    @Column(name = "preferred_activities", columnDefinition = "JSON")
    private String preferredActivities; // Adventure, culture, relaxation, etc.
    
    @Column(name = "budget_range")
    private String budgetRange; // BUDGET, MODERATE, LUXURY
    
    @Column(name = "group_size")
    private Integer groupSize;
    
    @Column(name = "travel_companions", columnDefinition = "JSON")
    private String travelCompanions; // Family, friends, solo, etc.
    
    @Column(name = "previous_cruise_experience")
    private Integer previousCruiseExperience; // Number of previous cruises
    
    @Column(name = "port_visit_preferences", columnDefinition = "JSON")
    private String portVisitPreferences; // Must-see highlights, specific attractions
    
    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Relationships - Ignored in JSON serialization to avoid lazy loading issues
    @JsonIgnore
    @OneToMany(mappedBy = "passenger", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SocialMediaProfile> socialMediaProfiles;
    
    @JsonIgnore
    @OneToMany(mappedBy = "passenger", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PassengerInterest> interests;
    
    @JsonIgnore
    @OneToMany(mappedBy = "passenger", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ShoreExcursionBooking> shoreExcursionBookings;
}
