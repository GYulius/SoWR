package com.cruise.recommender.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cruise Ship entity with AIS tracking capabilities
 */
@Entity
@Table(name = "cruise_ships")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CruiseShip {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(name = "cruise_line", nullable = false)
    private String cruiseLine;
    
    @Column(nullable = false)
    private Integer capacity;
    
    @Column(name = "length_meters")
    private Double lengthMeters;
    
    @Column(name = "width_meters")
    private Double widthMeters;
    
    @Column(name = "year_built")
    private Integer yearBuilt;
    
    @Column(columnDefinition = "JSON")
    private String amenities;
    
    @Column(name = "mmsi", unique = true)
    private String mmsi; // AIS identifier
    
    @Column(name = "imo", unique = true)
    private String imo; // IMO number
    
    @Column(name = "call_sign")
    private String callSign;
    
    @Column(name = "ais_enabled")
    @Builder.Default
    private Boolean aisEnabled = true;
    
    @Column(name = "last_ais_update")
    private LocalDateTime lastAisUpdate;
    
    @Column(name = "current_latitude")
    private Double currentLatitude;
    
    @Column(name = "current_longitude")
    private Double currentLongitude;
    
    @Column(name = "current_speed")
    private Double currentSpeed;
    
    @Column(name = "current_course")
    private Double currentCourse;
    
    @Column(name = "tracking_status")
    @Enumerated(EnumType.STRING)
    private TrackingStatus trackingStatus;
    
    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Relationships - Ignored in JSON serialization to avoid lazy loading issues
    @JsonIgnore
    @OneToMany(mappedBy = "cruiseShip", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<AisData> aisDataHistory;
    
    @JsonIgnore
    @OneToMany(mappedBy = "ship", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CruiseSchedule> schedules;
    
    public enum TrackingStatus {
        TRACKED,      // Currently being tracked via AIS
        OUT_OF_RANGE, // Out of range of AIS stations
        NO_SIGNAL,    // No AIS signal received
        OFFLINE       // AIS transceiver offline
    }
}
