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
 * Restaurant entity representing dining establishments
 */
@Entity
@Table(name = "restaurants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Restaurant {
    
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
    
    @Column(name = "cuisine_type")
    private String cuisineType;
    
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
    
    @Column(name = "menu_items", columnDefinition = "JSON")
    private String menuItems;
    
    @Column(name = "dietary_options", columnDefinition = "JSON")
    private String dietaryOptions;
    
    @Column(columnDefinition = "JSON")
    private String images;
    
    @Builder.Default
    @Column(name = "reservation_required")
    private Boolean reservationRequired = false;
    
    @Column(name = "max_capacity")
    private Integer maxCapacity;
    
    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Relationships
    @OneToMany(mappedBy = "restaurant", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Recommendation> recommendations;
    
    @OneToMany(mappedBy = "restaurant", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<UserInteraction> interactions;
    
    @OneToMany(mappedBy = "restaurant", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Booking> bookings;
    
    // Enums
    public enum PriceRange {
        BUDGET, MODERATE, EXPENSIVE, LUXURY
    }
}
