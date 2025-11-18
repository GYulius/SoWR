package com.cruise.recommender.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
 * Cruise Schedule entity (referenced by Passenger)
 */
@Entity
@Table(name = "cruise_schedules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CruiseSchedule {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ship_id", nullable = false)
    private CruiseShip ship;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "port_id", nullable = false)
    private Port port;
    
    @Column(name = "arrival_datetime", nullable = false)
    private LocalDateTime arrivalDatetime;
    
    @Column(name = "departure_datetime", nullable = false)
    private LocalDateTime departureDatetime;
    
    @Column(name = "estimated_passengers", nullable = false)
    private Integer estimatedPassengers;
    
    @Column(name = "actual_passengers")
    private Integer actualPassengers;
    
    @Column(name = "dock_number")
    private String dockNumber;
    
    @Column(name = "tender_required")
    @Builder.Default
    private Boolean tenderRequired = false;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ScheduleStatus status = ScheduleStatus.SCHEDULED;
    
    @Column(name = "weather_conditions", columnDefinition = "JSON")
    private String weatherConditions;
    
    @Column(name = "special_requirements", columnDefinition = "JSON")
    private String specialRequirements;
    
    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public enum ScheduleStatus {
        SCHEDULED,
        ARRIVED,
        DEPARTED,
        CANCELLED
    }
}
