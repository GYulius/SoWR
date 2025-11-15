package com.cruise.recommender.controller;

import com.cruise.recommender.dto.ShipTrackingResponse;
import com.cruise.recommender.entity.CruiseShip;
import com.cruise.recommender.service.AisDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST Controller for Ship Tracking Dashboard
 * Provides real-time AIS data and ship positions for visualization
 */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Dashboard", description = "Ship tracking and analytics dashboard APIs")
public class DashboardController {
    
    private final AisDataService aisDataService;
    
    @GetMapping("/ships/positions")
    @Operation(summary = "Get current ship positions", 
               description = "Retrieve real-time positions of all tracked cruise ships")
    public ResponseEntity<List<ShipTrackingResponse>> getCurrentShipPositions() {
        log.info("Getting current ship positions for dashboard");
        
        List<CruiseShip> ships = aisDataService.getCurrentShipPositions();
        
        List<ShipTrackingResponse> responses = ships.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(responses);
    }
    
    @GetMapping("/ships/near-port")
    @Operation(summary = "Get ships near port", 
               description = "Retrieve ships within specified radius of a port")
    public ResponseEntity<List<ShipTrackingResponse>> getShipsNearPort(
            @Parameter(description = "Port latitude") @RequestParam Double latitude,
            @Parameter(description = "Port longitude") @RequestParam Double longitude,
            @Parameter(description = "Radius in nautical miles") @RequestParam(defaultValue = "50") Double radius) {
        
        log.info("Getting ships near port: lat={}, lng={}, radius={}", latitude, longitude, radius);
        
        List<CruiseShip> ships = aisDataService.getShipsNearPort(latitude, longitude, radius);
        
        List<ShipTrackingResponse> responses = ships.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(responses);
    }
    
    @GetMapping("/ships/{id}/tracking")
    @Operation(summary = "Get ship tracking history", 
               description = "Retrieve tracking history for a specific ship")
    public ResponseEntity<ShipTrackingResponse> getShipTracking(
            @Parameter(description = "Ship ID") @PathVariable Long id) {
        
        log.info("Getting tracking history for ship: {}", id);
        
        // This would fetch from AIS data repository
        // For now, return a placeholder
        return ResponseEntity.ok(ShipTrackingResponse.builder()
                .shipId(id)
                .build());
    }
    
    @GetMapping("/ships/statistics")
    @Operation(summary = "Get ship tracking statistics", 
               description = "Get overall statistics about ship tracking")
    public ResponseEntity<Object> getShipStatistics() {
        log.info("Getting ship tracking statistics");
        
        List<CruiseShip> allShips = aisDataService.getCurrentShipPositions();
        
        long tracked = allShips.stream()
                .filter(s -> s.getTrackingStatus() == CruiseShip.TrackingStatus.TRACKED)
                .count();
        
        long outOfRange = allShips.stream()
                .filter(s -> s.getTrackingStatus() == CruiseShip.TrackingStatus.OUT_OF_RANGE)
                .count();
        
        long noSignal = allShips.stream()
                .filter(s -> s.getTrackingStatus() == CruiseShip.TrackingStatus.NO_SIGNAL)
                .count();
        
        return ResponseEntity.ok(Map.of(
                "totalShips", allShips.size(),
                "tracked", tracked,
                "outOfRange", outOfRange,
                "noSignal", noSignal,
                "trackingRate", allShips.isEmpty() ? 0.0 : (double) tracked / allShips.size() * 100
        ));
    }
    
    private ShipTrackingResponse convertToResponse(CruiseShip ship) {
        return ShipTrackingResponse.builder()
                .shipId(ship.getId())
                .shipName(ship.getName())
                .cruiseLine(ship.getCruiseLine())
                .latitude(ship.getCurrentLatitude())
                .longitude(ship.getCurrentLongitude())
                .speed(ship.getCurrentSpeed())
                .course(ship.getCurrentCourse())
                .trackingStatus(ship.getTrackingStatus().name())
                .lastUpdate(ship.getLastAisUpdate())
                .build();
    }
}
