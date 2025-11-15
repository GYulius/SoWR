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
 * Attraction entity representing touristic attractions
 */
@Entity
@Table(name = "attractions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Attraction {
    
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
    
    @Column(columnDefinition = "TEXT")
    private String address;
    
    private Double latitude;
    
    private Double longitude;
    
    @Builder.Default
    private BigDecimal rating = BigDecimal.ZERO;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "price_range")
    private PriceRange priceRange;
    
    @Column(name = "opening_hours", columnDefinition = "JSON")
    private String openingHours;
    
    @Column(name = "contact_info", columnDefinition = "JSON")
    private String contactInfo;
    
    @Column(columnDefinition = "JSON")
    private String images;
    
    @Column(name = "accessibility_features", columnDefinition = "JSON")
    private String accessibilityFeatures;
    
    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Relationships
    @OneToMany(mappedBy = "attraction", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Recommendation> recommendations;
    
    public enum PriceRange {
        FREE, LOW, MEDIUM, HIGH, LUXURY
    }
}
