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
    
    @Column(nullable = false)
    private Integer capacity;
    
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
    private String tourism1;
    
    @Column(name = "foodie_main", columnDefinition = "JSON")
    private String foodieMain;
    
    @Column(name = "foodie_dessert", columnDefinition = "JSON")
    private String foodieDessert;
    
    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Relationships
    @OneToMany(mappedBy = "port", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Attraction> attractions;
    
    @OneToMany(mappedBy = "port", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Restaurant> restaurants;
    
    @OneToMany(mappedBy = "port", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Activity> activities;
    
    @OneToMany(mappedBy = "port", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CruiseSchedule> cruiseSchedules;
    
    @OneToMany(mappedBy = "port", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Recommendation> recommendations;
}
