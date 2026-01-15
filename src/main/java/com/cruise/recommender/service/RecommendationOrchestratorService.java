package com.cruise.recommender.service;

import com.cruise.recommender.config.PrometheusMetricsService;
import com.cruise.recommender.entity.*;
import com.cruise.recommender.repository.*;
import com.cruise.recommender.service.PortProximityService.PortProximityEvent;
import com.cruise.recommender.service.UserItemMatrixService.UserItemMatrix;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Central Orchestrator Service
 * 
 * This service acts as the "glue" that connects:
 * - MySQL (AIS data, user preferences)
 * - Jena Fuseki (Knowledge Graph with SPARQL)
 * - Apache Spark MLlib (ALS Collaborative Filtering)
 * - RabbitMQ (Pub/Sub for recommendations)
 * 
 * Workflow:
 * 1. Monitor AIS data for port proximity triggers
 * 2. Query Fuseki for port-specific attractions/dishes based on social preferences
 * 3. Build user-item interaction matrix from MySQL and SPARQL results
 * 4. Train/use ALS model to generate recommendations
 * 5. Publish recommendations via RabbitMQ to passenger devices
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationOrchestratorService {
    
    private final AisDataService aisDataService;
    private final PortProximityService portProximityService;
    private final UserItemMatrixService userItemMatrixService;
    private final AlsRecommendationService alsRecommendationService;
    private final RecommendationPublisherService recommendationPublisherService;
    private final PortRdfService portRdfService;
    private final SocialMediaRdfQueryService socialMediaRdfQueryService;
    private final PassengerRepository passengerRepository;
    private final UserRepository userRepository;
    private final PortRepository portRepository;
    private final CruiseShipRepository cruiseShipRepository;
    private final PrometheusMetricsService prometheusMetricsService;
    
    // Port proximity threshold in nautical miles
    private static final double PORT_PROXIMITY_THRESHOLD = 10.0; // 10 nautical miles
    
    /**
     * Main orchestration method triggered by AIS data updates
     * This is called by AisDataService after processing AIS data
     * Note: We don't listen directly to avoid queue conflicts
     */
    @Transactional
    public void handleAisDataUpdate(AisDataService.AisDataMessage aisMessage) {
        log.info("Orchestrator received AIS data update for MMSI: {}", aisMessage.getMmsi());
        
        try {
            // Step 1: Check if ship is approaching a port
            Optional<PortProximityEvent> proximityEvent = portProximityService.checkPortProximity(
                    aisMessage.getLatitude(), 
                    aisMessage.getLongitude(),
                    PORT_PROXIMITY_THRESHOLD
            );
            
            if (proximityEvent.isEmpty()) {
                log.debug("Ship {} not near any port, skipping recommendation generation", aisMessage.getMmsi());
                return;
            }
            
            PortProximityEvent event = proximityEvent.get();
            log.info("Ship {} is approaching port {} ({} nm away)", 
                    aisMessage.getMmsi(), event.getPort().getName(), event.getDistanceNauticalMiles());
            
            // Step 2: Get all passengers on this ship
            List<Passenger> passengers = getPassengersOnShip(aisMessage.getMmsi());
            
            if (passengers.isEmpty()) {
                log.warn("No passengers found for ship MMSI: {}", aisMessage.getMmsi());
                return;
            }
            
            log.info("Found {} passengers on ship {}, generating recommendations for port {}", 
                    passengers.size(), aisMessage.getMmsi(), event.getPort().getName());
            
            // Step 3: Generate recommendations for each passenger
            for (Passenger passenger : passengers) {
                try {
                    generateAndPublishRecommendations(passenger, event.getPort());
                } catch (Exception e) {
                    log.error("Error generating recommendations for passenger {}: {}", 
                            passenger.getId(), e.getMessage(), e);
                    prometheusMetricsService.incrementCounter("recommendation_errors_total", 
                            "passenger_id", String.valueOf(passenger.getId()));
                }
            }
            
            prometheusMetricsService.incrementCounter("recommendations_generated_total",
                    "port_code", event.getPort().getPortCode());
            
        } catch (Exception e) {
            log.error("Error in orchestrator handling AIS data update", e);
            prometheusMetricsService.incrementCounter("orchestrator_errors_total");
        }
    }
    
    /**
     * Scheduled task to periodically check for ships approaching ports
     * Runs every 5 minutes
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    @Transactional
    public void checkApproachingShips() {
        log.debug("Running scheduled check for ships approaching ports");
        
        try {
            // Get all active cruise ships with recent AIS data
            List<CruiseShip> activeShips = cruiseShipRepository.findByAisEnabledTrue();
            
            for (CruiseShip ship : activeShips) {
                if (ship.getCurrentLatitude() == null || ship.getCurrentLongitude() == null) {
                    continue;
                }
                
                Optional<PortProximityEvent> proximityEvent = portProximityService.checkPortProximity(
                        ship.getCurrentLatitude(),
                        ship.getCurrentLongitude(),
                        PORT_PROXIMITY_THRESHOLD
                );
                
                if (proximityEvent.isPresent()) {
                    PortProximityEvent event = proximityEvent.get();
                    log.info("Scheduled check: Ship {} is approaching port {}", 
                            ship.getName(), event.getPort().getName());
                    
                    // Get passengers and generate recommendations
                    List<Passenger> passengers = getPassengersOnShip(ship.getMmsi());
                    for (Passenger passenger : passengers) {
                        generateAndPublishRecommendations(passenger, event.getPort());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error in scheduled port proximity check", e);
        }
    }
    
    /**
     * Generate ALS recommendations for a passenger at a port (returns recommendations without publishing)
     */
    public List<RecommendationItem> generateRecommendationsForPassenger(Passenger passenger, Port port) {
        log.info("Generating ALS recommendations for passenger {} at port {}", 
                passenger.getId(), port.getName());
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Step 1: Get passenger's social preferences from SPARQL (or fallback to MySQL)
            List<String> socialInterests = getSocialPreferences(passenger);
            
            // Step 2: Query SPARQL for port features matching interests
            List<PortFeature> portFeatures = queryPortFeaturesFromSparql(port, socialInterests);
            
            // Step 3: Build user-item interaction matrix
            UserItemMatrix matrix = userItemMatrixService.buildMatrix(
                    passenger.getId(),
                    port.getId(),
                    socialInterests,
                    portFeatures
            );
            
            // Step 4: Generate ALS recommendations
            List<RecommendationItem> recommendations = alsRecommendationService.generateRecommendations(
                    passenger.getId(),
                    matrix,
                    portFeatures,
                    10 // Top 10 recommendations
            );
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Successfully generated {} ALS recommendations for passenger {} in {} ms", 
                    recommendations.size(), passenger.getId(), duration);
            
            prometheusMetricsService.recordTimer("recommendation_generation_duration_ms", duration);
            
            return recommendations;
            
        } catch (Exception e) {
            log.error("Error generating ALS recommendations for passenger {} at port {}", 
                    passenger.getId(), port.getName(), e);
            // Return empty list on error rather than throwing
            // This prevents transaction rollback issues
            return new ArrayList<>();
        }
    }
    
    /**
     * Core recommendation generation workflow
     */
    @Transactional
    public void generateAndPublishRecommendations(Passenger passenger, Port port) {
        log.info("Generating recommendations for passenger {} at port {}", 
                passenger.getId(), port.getName());
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Step 1: Get passenger's social preferences from SPARQL (or fallback to MySQL)
            List<String> socialInterests = getSocialPreferences(passenger);
            
            // Step 2: Query SPARQL for port features matching interests
            List<PortFeature> portFeatures = queryPortFeaturesFromSparql(port, socialInterests);
            
            // Step 3: Build user-item interaction matrix
            UserItemMatrix matrix = userItemMatrixService.buildMatrix(
                    passenger.getId(),
                    port.getId(),
                    socialInterests,
                    portFeatures
            );
            
            // Step 4: Generate ALS recommendations
            List<RecommendationItem> recommendations = alsRecommendationService.generateRecommendations(
                    passenger.getId(),
                    matrix,
                    portFeatures,
                    10 // Top 10 recommendations
            );
            
            // Step 5: Publish recommendations via RabbitMQ
            recommendationPublisherService.publishRecommendations(
                    passenger,
                    port,
                    recommendations
            );
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Successfully generated {} recommendations for passenger {} in {} ms", 
                    recommendations.size(), passenger.getId(), duration);
            
            prometheusMetricsService.recordTimer("recommendation_generation_duration_ms", duration);
            
        } catch (Exception e) {
            log.error("Error generating recommendations for passenger {} at port {}", 
                    passenger.getId(), port.getName(), e);
            throw e;
        }
    }
    
    /**
     * Get social preferences for passenger
     * Priority: SPARQL (social media) > MySQL (voluntary preferences)
     */
    private List<String> getSocialPreferences(Passenger passenger) {
        List<String> preferences = new ArrayList<>();
        
        try {
            // Try to get preferences from SPARQL (social media)
            User user = passenger.getUser();
            String userUri = buildUserUri(user.getId());
            
            // Query SPARQL for social interests
            List<Map<String, String>> socialPosts = socialMediaRdfQueryService.findPostsMatchingInterests(
                    extractInterestsFromPassenger(passenger)
            );
            
            // Extract unique interests from social media posts
            Set<String> socialInterests = socialPosts.stream()
                    .map(post -> post.get("keyword"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            
            preferences.addAll(socialInterests);
            
            log.debug("Found {} social preferences from SPARQL for passenger {}", 
                    socialInterests.size(), passenger.getId());
            
        } catch (Exception e) {
            log.warn("Could not fetch social preferences from SPARQL, falling back to MySQL: {}", 
                    e.getMessage());
        }
        
        // Fallback: Get preferences from MySQL (voluntary interests)
        if (preferences.isEmpty() && passenger.getVoluntaryInterests() != null) {
            try {
                // Parse JSON interests
                // Assuming format: ["interest1", "interest2", ...]
                String interestsJson = passenger.getVoluntaryInterests();
                // Simple parsing - in production, use proper JSON parser
                if (interestsJson.startsWith("[") && interestsJson.endsWith("]")) {
                    String cleaned = interestsJson.substring(1, interestsJson.length() - 1);
                    String[] interests = cleaned.split(",");
                    for (String interest : interests) {
                        String cleanedInterest = interest.trim().replaceAll("^\"|\"$", "");
                        if (!cleanedInterest.isEmpty()) {
                            preferences.add(cleanedInterest);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error parsing voluntary interests from MySQL: {}", e.getMessage());
            }
        }
        
        log.info("Retrieved {} preferences for passenger {} (social: {}, voluntary: {})", 
                preferences.size(), passenger.getId(), 
                preferences.size(), preferences.size());
        
        return preferences;
    }
    
    /**
     * Query SPARQL for port features matching passenger interests
     */
    private List<PortFeature> queryPortFeaturesFromSparql(Port port, List<String> interests) {
        log.debug("Querying SPARQL for port {} features matching {} interests", 
                port.getPortCode(), interests.size());
        
        try {
            // Use existing SPARQL service to find port features
            Map<String, Object> sparqlResponse = portRdfService.findPortFeaturesByInterestsWithQuery(
                    port.getPortCode(),
                    interests,
                    Collections.emptyList() // Categories not needed for this query
            );
            
            @SuppressWarnings("unchecked")
            List<org.apache.jena.query.QuerySolution> results = 
                    (List<org.apache.jena.query.QuerySolution>) sparqlResponse.get("results");
            
            List<PortFeature> features = new ArrayList<>();
            for (org.apache.jena.query.QuerySolution solution : results) {
                PortFeature feature = new PortFeature();
                
                if (solution.get("feature") != null) {
                    feature.setName(solution.get("feature").toString());
                }
                if (solution.get("property") != null) {
                    feature.setCategory(solution.get("property").toString());
                }
                if (solution.get("port") != null) {
                    feature.setPortUri(solution.get("port").toString());
                }
                
                features.add(feature);
            }
            
            log.info("Found {} port features from SPARQL for port {}", 
                    features.size(), port.getPortCode());
            
            return features;
            
        } catch (Exception e) {
            log.error("Error querying SPARQL for port features", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Get passengers on a specific ship
     */
    private List<Passenger> getPassengersOnShip(String mmsi) {
        Optional<CruiseShip> shipOpt = cruiseShipRepository.findByMmsi(mmsi);
        if (shipOpt.isEmpty()) {
            return Collections.emptyList();
        }
        
        CruiseShip ship = shipOpt.get();
        // Get passengers through cruise schedules
        // This is a simplified version - adjust based on your schema
        return passengerRepository.findAll().stream()
                .filter(p -> {
                    CruiseSchedule schedule = p.getCruiseSchedule();
                    return schedule != null && schedule.getShip() != null && 
                           schedule.getShip().getId().equals(ship.getId());
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Extract interests from passenger entity
     */
    private List<String> extractInterestsFromPassenger(Passenger passenger) {
        List<String> interests = new ArrayList<>();
        
        if (passenger.getVoluntaryInterests() != null) {
            // Parse JSON - simplified
            String json = passenger.getVoluntaryInterests();
            if (json.startsWith("[") && json.endsWith("]")) {
                String cleaned = json.substring(1, json.length() - 1);
                String[] parts = cleaned.split(",");
                for (String part : parts) {
                    String cleanedPart = part.trim().replaceAll("^\"|\"$", "");
                    if (!cleanedPart.isEmpty()) {
                        interests.add(cleanedPart);
                    }
                }
            }
        }
        
        return interests;
    }
    
    /**
     * Build unified URI for user (Identity Bridge)
     * Maps MySQL user_id to RDF Subject in Fuseki
     */
    private String buildUserUri(Long userId) {
        return "http://cruise.recommender.org/kg/passenger/" + userId;
    }
    
    /**
     * Port Feature DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PortFeature {
        private String name;
        private String category;
        private String portUri;
    }
    
    /**
     * Recommendation Item DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RecommendationItem {
        private String itemId;
        private String itemName;
        private String category;
        private Double predictedRating;
        private String reason; // Why this was recommended
    }
}
