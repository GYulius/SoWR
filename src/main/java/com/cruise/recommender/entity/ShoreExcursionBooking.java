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
 * Shore Excursion Booking entity
 */
@Entity
@Table(name = "shore_excursion_bookings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ShoreExcursionBooking {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_id", nullable = false)
    private Passenger passenger;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shore_excursion_id", nullable = false)
    private ShoreExcursion shoreExcursion;
    
    @Column(name = "booking_date")
    private LocalDateTime bookingDate;
    
    @Column(name = "participants")
    private Integer participants;
    
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private BookingStatus status;
    
    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    public enum BookingStatus {
        PENDING, CONFIRMED, CANCELLED, COMPLETED
    }
}
