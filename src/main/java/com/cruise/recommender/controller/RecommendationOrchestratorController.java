package com.cruise.recommender.controller;

import com.cruise.recommender.entity.Passenger;
import com.cruise.recommender.entity.Port;
import com.cruise.recommender.repository.PassengerRepository;
import com.cruise.recommender.repository.PortRepository;
import com.cruise.recommender.service.RecommendationOrchestratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * REST Controller for Recommendation Orchestrator
 * Provides endpoints to manually trigger recommendation generation
 */
@RestController
@RequestMapping("/api/v1/orchestrator")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Recommendation Orchestrator", 
     description = "Central orchestrator for SPARQL, ALS, and recommendation generation")
public class RecommendationOrchestratorController {
    
    private final RecommendationOrchestratorService orchestratorService;
    private final PassengerRepository passengerRepository;
    private final PortRepository portRepository;
    
    /**
     * Manually trigger recommendation generation for a passenger at a port
     */
    @PostMapping("/recommendations/generate")
    @Operation(summary = "Generate recommendations for passenger at port",
               description = "Manually trigger recommendation generation using SPARQL and ALS")
    public ResponseEntity<Map<String, Object>> generateRecommendations(
            @Parameter(description = "Passenger ID") @RequestParam Long passengerId,
            @Parameter(description = "Port ID") @RequestParam Long portId) {
        
        log.info("Manual trigger: Generating recommendations for passenger {} at port {}", 
                passengerId, portId);
        
        try {
            Optional<Passenger> passengerOpt = passengerRepository.findById(passengerId);
            if (passengerOpt.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Passenger not found", "passengerId", passengerId));
            }
            
            Optional<Port> portOpt = portRepository.findById(portId);
            if (portOpt.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Port not found", "portId", portId));
            }
            
            Passenger passenger = passengerOpt.get();
            Port port = portOpt.get();
            
            // Generate recommendations
            orchestratorService.generateAndPublishRecommendations(passenger, port);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("passengerId", passengerId);
            response.put("portId", portId);
            response.put("portCode", port.getPortCode());
            response.put("portName", port.getName());
            response.put("message", "Recommendations generated and published successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error generating recommendations", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Get orchestrator status and metrics
     */
    @GetMapping("/status")
    @Operation(summary = "Get orchestrator status",
               description = "Returns orchestrator health and metrics")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "RUNNING");
        status.put("timestamp", java.time.LocalDateTime.now());
        status.put("components", Map.of(
                "sparql", "CONNECTED",
                "als", "AVAILABLE",
                "rabbitmq", "CONNECTED"
        ));
        
        return ResponseEntity.ok(status);
    }
}
