package com.cruise.recommender.controller;

import com.cruise.recommender.dto.PassengerRecommendationRequest;
import com.cruise.recommender.dto.PassengerRecommendationResponse;
import com.cruise.recommender.entity.*;
import com.cruise.recommender.repository.*;
import com.cruise.recommender.service.MealVenueRecommendationService;
import com.cruise.recommender.service.PortRdfService;
import com.cruise.recommender.service.ShoreExcursionRecommendationService;
import com.cruise.recommender.service.SocialMediaAnalysisService;
import org.apache.jena.query.QuerySolution;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST Controller for Passenger-Focused Recommendations
 * Provides personalized recommendations based on passenger interests and social media analysis
 */
@RestController
@RequestMapping("/passengers")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Passenger Recommendations", 
     description = "Passenger-focused recommendations for shore excursions and meal venues")
public class PassengerRecommendationController {
    
    private final ShoreExcursionRecommendationService shoreExcursionService;
    private final MealVenueRecommendationService mealVenueService;
    private final SocialMediaAnalysisService socialMediaService;
    private final PortRdfService portRdfService;
    private final PassengerRepository passengerRepository;
    private final PassengerInterestRepository passengerInterestRepository;
    private final com.cruise.recommender.repository.UserRepository userRepository;
    private final CruiseScheduleRepository cruiseScheduleRepository;
    private final CruiseShipRepository cruiseShipRepository;
    private final PortRepository portRepository;
    private final com.cruise.recommender.service.PortProximityService portProximityService;
    private final com.cruise.recommender.service.RecommendationOrchestratorService recommendationOrchestratorService;
    
    /**
     * Get or create a passenger profile for a user
     * Creates a default cruise schedule if needed
     */
    @Transactional
    public Passenger getOrCreatePassengerForUser(Long userId) {
        // Check if passenger already exists
        List<Passenger> existingPassengers = passengerRepository.findByUserId(userId);
        if (!existingPassengers.isEmpty()) {
            return existingPassengers.get(0);
        }
        
        // Get user
        com.cruise.recommender.entity.User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Find or create a default cruise schedule
        CruiseSchedule defaultSchedule = findOrCreateDefaultSchedule();
        
        // Create passenger
        Passenger passenger = Passenger.builder()
                .user(user)
                .cruiseSchedule(defaultSchedule)
                .socialMediaConsent(false)
                .build();
        
        return passengerRepository.save(passenger);
    }
    
    /**
     * Find or create a default cruise schedule
     */
    private CruiseSchedule findOrCreateDefaultSchedule() {
        // Try to find an existing schedule
        List<CruiseSchedule> schedules = cruiseScheduleRepository.findAll();
        if (!schedules.isEmpty()) {
            return schedules.get(0);
        }
        
        // If no schedules exist, create a default one
        // First, get or create a default ship
        List<CruiseShip> ships = cruiseShipRepository.findAll();
        CruiseShip defaultShip;
        if (ships.isEmpty()) {
            // Create a default ship
            defaultShip = CruiseShip.builder()
                    .name("Default Cruise Ship")
                    .cruiseLine("Default Cruise Line")
                    .capacity(1000)
                    .aisEnabled(false)
                    .build();
            defaultShip = cruiseShipRepository.save(defaultShip);
        } else {
            defaultShip = ships.get(0);
        }
        
        // Get or create a default port
        List<Port> ports = portRepository.findAll();
        Port defaultPort;
        if (ports.isEmpty()) {
            // Create a default port
            defaultPort = Port.builder()
                    .name("Default Port")
                    .portCode("DEF")
                    .country("Unknown")
                    .latitude(0.0)
                    .longitude(0.0)
                    .build();
            defaultPort = portRepository.save(defaultPort);
        } else {
            defaultPort = ports.get(0);
        }
        
        // Create default schedule
        CruiseSchedule schedule = CruiseSchedule.builder()
                .ship(defaultShip)
                .port(defaultPort)
                .arrivalDatetime(LocalDateTime.now().plusDays(30))
                .departureDatetime(LocalDateTime.now().plusDays(31))
                .estimatedPassengers(100)
                .status(CruiseSchedule.ScheduleStatus.SCHEDULED)
                .build();
        
        return cruiseScheduleRepository.save(schedule);
    }
    
    @GetMapping
    @Operation(summary = "Get passengers by user ID", 
               description = "Get all passengers associated with a user ID. Creates a passenger if none exists.")
    public ResponseEntity<List<Passenger>> getPassengersByUserId(
            @Parameter(description = "User ID") @RequestParam Long userId) {
        
        log.info("Getting passengers for user ID: {}", userId);
        List<Passenger> passengers = passengerRepository.findByUserId(userId);
        
        // If no passenger exists, create one
        if (passengers.isEmpty()) {
            log.info("No passenger found for user {}, creating default passenger profile", userId);
            Passenger newPassenger = getOrCreatePassengerForUser(userId);
            passengers = List.of(newPassenger);
        }
        
        return ResponseEntity.ok(passengers);
    }
    
    @GetMapping("/{passengerId}/recommendations")
    @Operation(summary = "Get comprehensive recommendations for passenger", 
               description = "Get personalized recommendations including shore excursions and meal venues")
    public ResponseEntity<PassengerRecommendationResponse> getPassengerRecommendations(
            @Parameter(description = "Passenger ID") @PathVariable Long passengerId,
            @Parameter(description = "Port ID") @RequestParam Long portId,
            @Parameter(description = "Include shore excursions") @RequestParam(defaultValue = "true") Boolean includeExcursions,
            @Parameter(description = "Include meal venues") @RequestParam(defaultValue = "true") Boolean includeMeals) {
        
        log.info("Getting comprehensive recommendations for passenger {} at port {}", 
                passengerId, portId);
        
        PassengerRecommendationResponse response = PassengerRecommendationResponse.builder()
                .passengerId(passengerId)
                .portId(portId)
                .build();
        
        if (includeExcursions) {
            // Get shore excursion recommendations
            var excursions = shoreExcursionService.recommendShoreExcursions(passengerId, portId);
            response.setShoreExcursions(excursions);
            
            // Get must-see highlights
            var highlights = shoreExcursionService.getPersonalizedMustSeeHighlights(passengerId, portId);
            response.setMustSeeHighlights(highlights);
        }
        
        if (includeMeals) {
            // Get breakfast recommendations (default 8 AM)
            var breakfast = mealVenueService.recommendBreakfastVenues(
                    passengerId, portId, LocalTime.of(8, 0));
            response.setBreakfastVenues(breakfast);
            
            // Get lunch recommendations (default 1 PM)
            var lunch = mealVenueService.recommendLunchVenues(
                    passengerId, portId, LocalTime.of(13, 0));
            response.setLunchVenues(lunch);
        }
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/{passengerId}/shore-excursions")
    @Operation(summary = "Get shore excursion recommendations", 
               description = "Get personalized shore excursion recommendations based on passenger interests")
    public ResponseEntity<List<ShoreExcursionRecommendationService.ShoreExcursionRecommendation>> 
            getShoreExcursionRecommendations(
            @Parameter(description = "Passenger ID") @PathVariable Long passengerId,
            @Parameter(description = "Port ID") @RequestParam Long portId) {
        
        log.info("Getting shore excursion recommendations for passenger {} at port {}", 
                passengerId, portId);
        
        var recommendations = shoreExcursionService.recommendShoreExcursions(passengerId, portId);
        
        return ResponseEntity.ok(recommendations);
    }
    
    @GetMapping("/{passengerId}/must-see-highlights")
    @Operation(summary = "Get must-see highlights", 
               description = "Get personalized must-see highlights based on passenger interests")
    public ResponseEntity<List<ShoreExcursionRecommendationService.ShoreExcursionRecommendation>> 
            getMustSeeHighlights(
            @Parameter(description = "Passenger ID") @PathVariable Long passengerId,
            @Parameter(description = "Port ID") @RequestParam Long portId) {
        
        log.info("Getting must-see highlights for passenger {} at port {}", passengerId, portId);
        
        var highlights = shoreExcursionService.getPersonalizedMustSeeHighlights(passengerId, portId);
        
        return ResponseEntity.ok(highlights);
    }
    
    @GetMapping("/{passengerId}/breakfast-venues")
    @Operation(summary = "Get breakfast venue recommendations", 
               description = "Get locally active breakfast venues for port call")
    public ResponseEntity<List<MealVenueRecommendationService.MealVenueRecommendation>> 
            getBreakfastVenues(
            @Parameter(description = "Passenger ID") @PathVariable Long passengerId,
            @Parameter(description = "Port ID") @RequestParam Long portId,
            @Parameter(description = "Preferred time (HH:mm)") @RequestParam(required = false) String preferredTime) {
        
        log.info("Getting breakfast venue recommendations for passenger {} at port {}", 
                passengerId, portId);
        
        LocalTime time = preferredTime != null ? 
                LocalTime.parse(preferredTime) : LocalTime.of(8, 0);
        
        var recommendations = mealVenueService.recommendBreakfastVenues(passengerId, portId, time);
        
        return ResponseEntity.ok(recommendations);
    }
    
    @GetMapping("/{passengerId}/lunch-venues")
    @Operation(summary = "Get lunch venue recommendations", 
               description = "Get locally active lunch venues for port call")
    public ResponseEntity<List<MealVenueRecommendationService.MealVenueRecommendation>> 
            getLunchVenues(
            @Parameter(description = "Passenger ID") @PathVariable Long passengerId,
            @Parameter(description = "Port ID") @RequestParam Long portId,
            @Parameter(description = "Preferred time (HH:mm)") @RequestParam(required = false) String preferredTime) {
        
        log.info("Getting lunch venue recommendations for passenger {} at port {}", 
                passengerId, portId);
        
        LocalTime time = preferredTime != null ? 
                LocalTime.parse(preferredTime) : LocalTime.of(13, 0);
        
        var recommendations = mealVenueService.recommendLunchVenues(passengerId, portId, time);
        
        return ResponseEntity.ok(recommendations);
    }
    
    @PostMapping("/{passengerId}/analyze-social-media")
    @Operation(summary = "Analyze passenger social media", 
               description = "Trigger social media analysis for passenger to extract interests")
    public ResponseEntity<Void> analyzeSocialMedia(
            @Parameter(description = "Passenger ID") @PathVariable Long passengerId) {
        
        log.info("Triggering social media analysis for passenger: {}", passengerId);
        
        socialMediaService.analyzePassengerSocialMedia(passengerId);
        
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/{passengerId}/interests")
    @Operation(summary = "Get passenger interests", 
               description = "Get all interests for a passenger")
    public ResponseEntity<List<PassengerInterestResponse>> getPassengerInterests(
            @Parameter(description = "Passenger ID") @PathVariable Long passengerId) {
        
        log.info("Getting interests for passenger: {}", passengerId);
        
        Passenger passenger = passengerRepository.findById(passengerId)
                .orElseThrow(() -> new RuntimeException("Passenger not found"));
        
        List<PassengerInterest> interests = passengerInterestRepository.findByPassenger(passenger);
        
        List<PassengerInterestResponse> responses = interests.stream()
                .map(interest -> new PassengerInterestResponse(
                        interest.getId(),
                        interest.getInterestCategory(),
                        interest.getInterestKeyword(),
                        interest.getSource() != null ? interest.getSource().name() : null,
                        interest.getIsExplicit() != null ? interest.getIsExplicit() : false
                ))
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(responses);
    }
    
    @GetMapping("/{passengerId}/ship-info")
    @Operation(summary = "Get passenger ship and approaching port info", 
               description = "Get the cruise ship the passenger is on and the next approaching port based on AIS route data")
    public ResponseEntity<Map<String, Object>> getPassengerShipInfo(
            @Parameter(description = "Passenger ID") @PathVariable Long passengerId) {
        
        log.info("Getting ship info for passenger: {}", passengerId);
        
        Passenger passenger = passengerRepository.findById(passengerId)
                .orElseThrow(() -> new RuntimeException("Passenger not found"));
        
        Map<String, Object> response = new HashMap<>();
        
        // Get passenger interests to find preferred ship
        List<PassengerInterest> interests = passengerInterestRepository.findByPassenger(passenger);
        Optional<PassengerInterest> cruiseShipInterest = interests.stream()
            .filter(i -> "CRUISE_SHIP".equalsIgnoreCase(i.getInterestCategory()))
            .findFirst();
        
        CruiseShip ship = null;
        String preferredShipName = null;
        
        // Priority 1: Use ship from CRUISE_SHIP interest if available
        if (cruiseShipInterest.isPresent()) {
            preferredShipName = cruiseShipInterest.get().getInterestKeyword();
            log.info("Found CRUISE_SHIP interest: {}", preferredShipName);
            
            // Extract to final variable for lambda
            final String shipNameToFind = preferredShipName;
            
            // Try to find ship by name (exact match first, then partial)
            List<CruiseShip> shipsByName = cruiseShipRepository.findAll().stream()
                .filter(s -> s.getName() != null && 
                    (s.getName().equalsIgnoreCase(shipNameToFind) ||
                     s.getName().toLowerCase().contains(shipNameToFind.toLowerCase()) ||
                     shipNameToFind.toLowerCase().contains(s.getName().toLowerCase())))
                .collect(Collectors.toList());
            
            if (!shipsByName.isEmpty()) {
                ship = shipsByName.get(0);
                log.info("Found ship from interest: {} (MMSI: {})", ship.getName(), ship.getMmsi());
            } else {
                log.warn("Ship '{}' from interests not found in database", preferredShipName);
            }
        }
        
        // Priority 2: Use ship from passenger's schedule if no interest ship found
        if (ship == null) {
            CruiseSchedule schedule = passenger.getCruiseSchedule();
            if (schedule != null && schedule.getShip() != null) {
                ship = schedule.getShip();
                log.info("Using ship from schedule: {} (MMSI: {})", ship.getName(), ship.getMmsi());
            }
        }
        
        if (ship != null) {
            response.put("shipName", ship.getName());
            response.put("shipMmsi", ship.getMmsi());
            response.put("preferredShipName", preferredShipName != null ? preferredShipName : ship.getName());
            
            // Find approaching port based on preferred ship's current position from AIS data
            if (ship.getCurrentLatitude() != null && ship.getCurrentLongitude() != null) {
                log.info("Using ship position for port calculation: lat={}, lng={}", 
                        ship.getCurrentLatitude(), ship.getCurrentLongitude());
                
                Optional<com.cruise.recommender.service.PortProximityService.PortProximityEvent> proximityEvent = 
                    portProximityService.checkPortProximity(
                        ship.getCurrentLatitude(),
                        ship.getCurrentLongitude(),
                        50.0 // 50 nautical miles threshold
                    );
                
                if (proximityEvent.isPresent()) {
                    Port approachingPort = proximityEvent.get().getPort();
                    log.info("Ship {} is approaching port: {} ({} nm away)", 
                            ship.getName(), approachingPort.getName(), 
                            proximityEvent.get().getDistanceNauticalMiles());
                    response.put("approachingPort", Map.of(
                        "id", approachingPort.getId(),
                        "name", approachingPort.getName(),
                        "portCode", approachingPort.getPortCode() != null ? approachingPort.getPortCode() : "",
                        "country", approachingPort.getCountry() != null ? approachingPort.getCountry() : "",
                        "distanceNauticalMiles", proximityEvent.get().getDistanceNauticalMiles()
                    ));
                } else {
                    // Find nearest port even if not within threshold
                    // Extract ship position to final variables for lambda
                    final Double shipLat = ship.getCurrentLatitude();
                    final Double shipLng = ship.getCurrentLongitude();
                    
                    List<Port> allPorts = portRepository.findAll();
                    Port nearestPort = allPorts.stream()
                        .min((p1, p2) -> {
                            double dist1 = calculateDistance(
                                shipLat, shipLng,
                                p1.getLatitude(), p1.getLongitude());
                            double dist2 = calculateDistance(
                                shipLat, shipLng,
                                p2.getLatitude(), p2.getLongitude());
                            return Double.compare(dist1, dist2);
                        })
                        .orElse(null);
                    
                    if (nearestPort != null) {
                        double distance = calculateDistance(
                            ship.getCurrentLatitude(), ship.getCurrentLongitude(),
                            nearestPort.getLatitude(), nearestPort.getLongitude());
                        log.info("Ship {} nearest port: {} ({} nm away)", 
                                ship.getName(), nearestPort.getName(), distance);
                        response.put("approachingPort", Map.of(
                            "id", nearestPort.getId(),
                            "name", nearestPort.getName(),
                            "portCode", nearestPort.getPortCode() != null ? nearestPort.getPortCode() : "",
                            "country", nearestPort.getCountry() != null ? nearestPort.getCountry() : "",
                            "distanceNauticalMiles", distance
                        ));
                    } else {
                        log.warn("No ports found in database for ship position calculation");
                    }
                }
            } else {
                log.warn("Ship {} has no current position (AIS data) - cannot calculate approaching port", ship.getName());
            }
        } else {
            log.warn("No ship found for passenger {} - neither from interests nor schedule", passengerId);
            // Still set preferred ship name if we have it from interests
            if (preferredShipName != null) {
                response.put("preferredShipName", preferredShipName);
            }
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Calculate distance between two points in nautical miles using Haversine formula
     */
    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        double earthRadiusKm = 6371.0;
        double earthRadiusNm = earthRadiusKm / 1.852; // Convert to nautical miles
        
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLng = Math.toRadians(lng2 - lng1);
        
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return earthRadiusNm * c;
    }
    
    @GetMapping("/{passengerId}/als-recommendations")
    @Operation(summary = "Get ALS-based recommendations for passenger at port", 
               description = "Uses SparkML Alternating Least Squares (ALS) collaborative filtering to generate personalized recommendations")
    public ResponseEntity<Map<String, Object>> getAlsRecommendations(
            @Parameter(description = "Passenger ID") @PathVariable Long passengerId,
            @Parameter(description = "Port ID") @RequestParam Long portId) {
        
        log.info("Getting ALS-based recommendations for passenger {} at port {}", 
                passengerId, portId);
        
        try {
            Passenger passenger = passengerRepository.findById(passengerId)
                    .orElseThrow(() -> new RuntimeException("Passenger not found"));
            
            Port port = portRepository.findById(portId)
                    .orElseThrow(() -> new RuntimeException("Port not found"));
            
            // Generate ALS recommendations using the orchestrator service
            // This internally uses SPARQL to get port features and ALS to generate recommendations
            List<com.cruise.recommender.service.RecommendationOrchestratorService.RecommendationItem> recommendations = 
                recommendationOrchestratorService.generateRecommendationsForPassenger(passenger, port);
            
            // Format recommendations for response
            List<Map<String, Object>> formattedRecommendations = recommendations.stream()
                .map(rec -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("itemName", rec.getItemName());
                    item.put("category", rec.getCategory());
                    item.put("predictedRating", rec.getPredictedRating());
                    item.put("reason", rec.getReason());
                    return item;
                })
                .collect(Collectors.toList());
            
            Map<String, Object> response = new HashMap<>();
            response.put("passengerId", passengerId);
            response.put("portId", portId);
            response.put("portName", port.getName());
            response.put("portCode", port.getPortCode());
            response.put("recommendations", formattedRecommendations);
            response.put("count", formattedRecommendations.size());
            response.put("algorithm", "SparkML ALS (Alternating Least Squares)");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting ALS-based recommendations for passenger {} at port {}: {}", 
                    passengerId, portId, e.getMessage(), e);
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "error", "Failed to generate ALS recommendations",
                        "message", e.getMessage(),
                        "passengerId", passengerId,
                        "portId", portId
                    ));
        }
    }
    
    @PostMapping("/{passengerId}/interests")
    @Operation(summary = "Save passenger interests", 
               description = "Save or update passenger interests")
    public ResponseEntity<List<PassengerInterestResponse>> savePassengerInterests(
            @Parameter(description = "Passenger ID") @PathVariable Long passengerId,
            @RequestBody List<PassengerInterestRequest> requests) {
        
        log.info("Saving {} interests for passenger: {}", requests.size(), passengerId);
        
        Passenger passenger = passengerRepository.findById(passengerId)
                .orElseThrow(() -> new RuntimeException("Passenger not found"));
        
        // Delete existing explicit interests
        List<PassengerInterest> existingInterests = passengerInterestRepository.findByPassengerAndIsExplicitTrue(passenger);
        passengerInterestRepository.deleteAll(existingInterests);
        
        // Save new interests
        for (PassengerInterestRequest request : requests) {
            PassengerInterest interest = PassengerInterest.builder()
                    .passenger(passenger)
                    .interestCategory(request.getCategory())
                    .interestKeyword(request.getKeyword())
                    .source(PassengerInterest.InterestSource.MANUAL_ENTRY)
                    .isExplicit(true)
                    .confidenceScore(1.0)
                    .expressedAt(LocalDateTime.now())
                    .build();
            
            passengerInterestRepository.save(interest);
        }
        
        // Return updated list
        List<PassengerInterest> allInterests = passengerInterestRepository.findByPassenger(passenger);
        List<PassengerInterestResponse> responses = allInterests.stream()
                .map(interest -> new PassengerInterestResponse(
                        interest.getId(),
                        interest.getInterestCategory(),
                        interest.getInterestKeyword(),
                        interest.getSource() != null ? interest.getSource().name() : null,
                        interest.getIsExplicit() != null ? interest.getIsExplicit() : false
                ))
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(responses);
    }
    
    @GetMapping("/{passengerId}/sparql-recommendations")
    @Operation(summary = "Get SPARQL-based recommendations for passenger at port", 
               description = "Uses SPARQL queries on knowledge graph to find port features matching passenger interests")
    public ResponseEntity<Map<String, Object>> getSparqlRecommendations(
            @Parameter(description = "Passenger ID") @PathVariable Long passengerId,
            @Parameter(description = "Port ID") @RequestParam Long portId) {
        
        log.info("Getting SPARQL-based recommendations for passenger {} at port {}", 
                passengerId, portId);
        
        Passenger passenger = passengerRepository.findById(passengerId)
                .orElseThrow(() -> new RuntimeException("Passenger not found"));
        
        Port port = portRepository.findById(portId)
                .orElseThrow(() -> new RuntimeException("Port not found"));
        
        // Get passenger interests
        List<PassengerInterest> interests = passengerInterestRepository.findByPassenger(passenger);
        
        log.info("Found {} interests for passenger {}: {}", interests.size(), passengerId, 
                interests.stream()
                    .map(i -> i.getInterestCategory() + ":" + i.getInterestKeyword())
                    .collect(Collectors.joining(", ")));
        
        // Extract keywords and categories
        List<String> interestKeywords = interests.stream()
                .map(PassengerInterest::getInterestKeyword)
                .filter(kw -> kw != null && !kw.trim().isEmpty())
                .collect(Collectors.toList());
        
        List<String> interestCategories = interests.stream()
                .map(PassengerInterest::getInterestCategory)
                .filter(cat -> cat != null && !cat.trim().isEmpty())
                .distinct()
                .collect(Collectors.toList());
        
        log.info("Extracted {} keywords and {} categories for SPARQL query. Port: {} (code: {})", 
                interestKeywords.size(), interestCategories.size(), port.getName(), port.getPortCode());
        
        // Query SPARQL knowledge graph
        Map<String, Object> sparqlResponse;
        try {
            sparqlResponse = portRdfService.findPortFeaturesByInterestsWithQuery(
                    port.getPortCode(), interestKeywords, interestCategories);
        } catch (Exception e) {
            log.error("Failed to execute SPARQL query for passenger {} at port {}: {}", 
                    passengerId, port.getPortCode(), e.getMessage(), e);
            
            // Check if it's a connectivity issue
            String errorMessage = e.getMessage();
            if (errorMessage != null && (errorMessage.contains("Service Unavailable") || 
                    errorMessage.contains("Connection refused") || 
                    errorMessage.contains("connect"))) {
                errorMessage = "Fuseki SPARQL server is not available. Please ensure Fuseki is running. " +
                        "Check Docker container 'cruise_recommender_fuseki' is running and accessible at http://localhost:3030";
            }
            
            return ResponseEntity.status(500)
                    .body(Map.of(
                            "error", "Failed to execute SPARQL query: " + errorMessage,
                            "query", "",
                            "results", List.of()
                    ));
        }
        
        @SuppressWarnings("unchecked")
        List<QuerySolution> sparqlResults = (List<QuerySolution>) sparqlResponse.get("results");
        String executedQuery = (String) sparqlResponse.get("query");
        
        log.info("SPARQL query returned {} matching features for passenger {} at port {}", 
                sparqlResults.size(), passengerId, port.getPortCode());
        
        // Convert results to response format
        // The SPARQL query returns: ?property (category) and ?feature (feature text)
        List<Map<String, Object>> features = sparqlResults.stream()
                .map(solution -> {
                    Map<String, Object> feature = new HashMap<>();
                    
                    // Get feature text (the actual feature description)
                    if (solution.get("feature") != null) {
                        String featureText = solution.get("feature").toString();
                        feature.put("feature", featureText);
                        feature.put("label", featureText); // Keep for backward compatibility
                    }
                    
                    // Get property/category (e.g., "activity", "excursion", "touristAttraction")
                    if (solution.get("property") != null) {
                        String property = solution.get("property").toString();
                        feature.put("property", property);
                        feature.put("category", property); // Keep for backward compatibility
                        
                        // Map property to user-friendly category name
                        String categoryDisplay = mapPropertyToCategoryDisplay(property);
                        feature.put("categoryDisplay", categoryDisplay);
                    }
                    
                    // Find which user interest keywords matched this feature
                    if (solution.get("feature") != null) {
                        String featureText = solution.get("feature").toString().toLowerCase();
                        List<String> matchedKeywords = interestKeywords.stream()
                                .filter(keyword -> featureText.contains(keyword.toLowerCase()))
                                .collect(Collectors.toList());
                        if (!matchedKeywords.isEmpty()) {
                            feature.put("matchedInterests", matchedKeywords);
                        }
                    }
                    
                    return feature;
                })
                .collect(Collectors.toList());
        
        // Build debug information
        Map<String, Object> debugInfo = new HashMap<>();
        debugInfo.put("portCode", port.getPortCode());
        debugInfo.put("interestKeywords", interestKeywords);
        debugInfo.put("interestCategories", interestCategories);
        debugInfo.put("sparqlResultsCount", sparqlResults.size());
        debugInfo.put("executedQuery", executedQuery);
        
        Map<String, Object> response = new HashMap<>();
        response.put("passengerId", passengerId);
        response.put("portId", portId);
        response.put("portCode", port.getPortCode());
        response.put("portName", port.getName());
        response.put("interestCount", interests.size());
        response.put("matchingFeaturesCount", features.size());
        response.put("matchingFeatures", features);
        response.put("debug", debugInfo);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Map SPARQL property name to user-friendly category display name
     */
    private String mapPropertyToCategoryDisplay(String property) {
        if (property == null) return "Feature";
        
        return switch (property.toLowerCase()) {
            case "touristattraction" -> "Tourist Attraction";
            case "iconicattraction" -> "Iconic Attraction";
            case "activity" -> "Activity";
            case "excursion" -> "Excursion";
            case "generalinterest" -> "General Interest";
            case "mealvenueinfo" -> "Meal Venue";
            case "restaurantinfo" -> "Restaurant";
            case "localspecialtymain" -> "Local Specialty (Main)";
            case "localspecialtydessert" -> "Local Specialty (Dessert)";
            case "culinaryingredient" -> "Culinary Ingredient";
            case "servescuisine" -> "Cuisine";
            default -> property.substring(0, 1).toUpperCase() + property.substring(1).replaceAll("([A-Z])", " $1");
        };
    }
    
    // DTOs
    @Data
    public static class PassengerInterestRequest {
        private String category;
        private String keyword;
    }
    
    @Data
    public static class PassengerInterestResponse {
        private Long id;
        private String category;
        private String keyword;
        private String source;
        private Boolean isExplicit;
        
        public PassengerInterestResponse(Long id, String category, String keyword, String source, Boolean isExplicit) {
            this.id = id;
            this.category = category;
            this.keyword = keyword;
            this.source = source;
            this.isExplicit = isExplicit;
        }
    }
}
