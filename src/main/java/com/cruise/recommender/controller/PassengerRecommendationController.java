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
        Map<String, Object> sparqlResponse = portRdfService.findPortFeaturesByInterestsWithQuery(
                port.getPortCode(), interestKeywords, interestCategories);
        
        @SuppressWarnings("unchecked")
        List<QuerySolution> sparqlResults = (List<QuerySolution>) sparqlResponse.get("results");
        String executedQuery = (String) sparqlResponse.get("query");
        
        log.info("SPARQL query returned {} matching features for passenger {} at port {}", 
                sparqlResults.size(), passengerId, port.getPortCode());
        
        // Convert results to response format
        List<Map<String, String>> features = sparqlResults.stream()
                .map(solution -> {
                    Map<String, String> feature = new HashMap<>();
                    if (solution.get("concept") != null) {
                        feature.put("concept", solution.get("concept").toString());
                        // Extract category from concept URI
                        String conceptUri = solution.get("concept").toString();
                        if (conceptUri.contains("/concept/")) {
                            String[] parts = conceptUri.split("/concept/");
                            if (parts.length > 1) {
                                String category = parts[1].split("/")[0];
                                feature.put("category", category);
                            }
                        }
                    }
                    if (solution.get("label") != null) {
                        feature.put("label", solution.get("label").toString());
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
