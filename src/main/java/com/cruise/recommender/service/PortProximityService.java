package com.cruise.recommender.service;

import com.cruise.recommender.entity.Port;
import com.cruise.recommender.repository.PortRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service for detecting when ships are approaching ports
 * Uses geospatial calculations to determine proximity
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortProximityService {
    
    private final PortRepository portRepository;
    
    // Earth's radius in nautical miles
    private static final double EARTH_RADIUS_NM = 3440.065; // nautical miles
    
    /**
     * Check if a ship's position is within proximity threshold of any port
     * 
     * @param shipLatitude Ship's current latitude
     * @param shipLongitude Ship's current longitude
     * @param thresholdNauticalMiles Proximity threshold in nautical miles
     * @return Optional PortProximityEvent if ship is near a port
     */
    public Optional<PortProximityEvent> checkPortProximity(
            Double shipLatitude, 
            Double shipLongitude,
            Double thresholdNauticalMiles) {
        
        if (shipLatitude == null || shipLongitude == null) {
            log.warn("Invalid ship coordinates: lat={}, lng={}", shipLatitude, shipLongitude);
            return Optional.empty();
        }
        
        // Get all ports
        List<Port> ports = portRepository.findAll();
        
        for (Port port : ports) {
            if (port.getLatitude() == null || port.getLongitude() == null) {
                continue;
            }
            
            double distance = calculateDistanceNauticalMiles(
                    shipLatitude, shipLongitude,
                    port.getLatitude(), port.getLongitude()
            );
            
            if (distance <= thresholdNauticalMiles) {
                log.info("Ship at ({}, {}) is {} nm from port {} ({})", 
                        shipLatitude, shipLongitude, distance, port.getName(), port.getPortCode());
                
                return Optional.of(PortProximityEvent.builder()
                        .port(port)
                        .shipLatitude(shipLatitude)
                        .shipLongitude(shipLongitude)
                        .distanceNauticalMiles(distance)
                        .timestamp(java.time.LocalDateTime.now())
                        .build());
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Calculate distance between two points using Haversine formula
     * Returns distance in nautical miles
     */
    private double calculateDistanceNauticalMiles(
            double lat1, double lon1,
            double lat2, double lon2) {
        
        // Convert to radians
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLatRad = Math.toRadians(lat2 - lat1);
        double deltaLonRad = Math.toRadians(lon2 - lon1);
        
        // Haversine formula
        double a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(deltaLonRad / 2) * Math.sin(deltaLonRad / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        // Distance in nautical miles
        return EARTH_RADIUS_NM * c;
    }
    
    /**
     * Port Proximity Event
     */
    @Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PortProximityEvent {
        private Port port;
        private Double shipLatitude;
        private Double shipLongitude;
        private Double distanceNauticalMiles;
        private java.time.LocalDateTime timestamp;
    }
}
