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
 * AIS (Automatic Identification System) data entity for ship tracking
 */
@Entity
@Table(name = "ais_data", indexes = {
    @Index(name = "idx_mmsi", columnList = "mmsi"),
    @Index(name = "idx_timestamp", columnList = "timestamp"),
    @Index(name = "idx_latitude_longitude", columnList = "latitude,longitude")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class AisData {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String mmsi; // Maritime Mobile Service Identity
    
    @Column(nullable = false)
    private String shipName;
    
    @Column(nullable = false)
    private Double latitude;
    
    @Column(nullable = false)
    private Double longitude;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    private Double speed; // Speed over ground in knots
    
    private Double course; // Course over ground in degrees
    
    private Integer heading; // Heading in degrees
    
    private String shipType;
    
    private Integer length; // Length in meters
    
    private Integer width; // Width in meters
    
    private String destination;
    
    private String eta; // Estimated Time of Arrival
    
    private String imo; // International Maritime Organization number
    
    private String callSign;
    
    @Column(name = "station_range")
    private Double stationRange; // Distance to nearest AIS station in nautical miles
    
    @Column(name = "signal_quality")
    private String signalQuality; // GOOD, FAIR, POOR, NONE
    
    @Column(name = "data_source")
    private String dataSource; // SATELLITE, TERRESTRIAL, BOTH
    
    @Column(columnDefinition = "JSON")
    private String metadata; // Additional AIS data
    
    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Relationship to cruise ships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cruise_ship_id")
    private CruiseShip cruiseShip;
}
