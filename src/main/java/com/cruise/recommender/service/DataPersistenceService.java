package com.cruise.recommender.service;

import com.cruise.recommender.entity.AisData;
import com.cruise.recommender.entity.Passenger;
import com.cruise.recommender.entity.PassengerInterest;
import com.cruise.recommender.entity.Port;
import com.cruise.recommender.entity.User;
import com.cruise.recommender.repository.AisDataRepository;
import com.cruise.recommender.repository.PassengerInterestRepository;
import com.cruise.recommender.repository.PassengerRepository;
import com.cruise.recommender.repository.PortRepository;
import com.cruise.recommender.repository.UserRepository;
import com.cruise.recommender.repository.elasticsearch.AisDataDocument;
import com.cruise.recommender.repository.elasticsearch.AisDataDocumentMapper;
import com.cruise.recommender.repository.elasticsearch.AisDataElasticsearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for persisting processed data to MySQL and Elasticsearch
 * Receives data from Knowledge Graph processing and stores it in both databases
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataPersistenceService {
    
    private final AisDataRepository aisDataRepository;
    private final AisDataElasticsearchRepository aisDataElasticsearchRepository;
    private final AisDataDocumentMapper documentMapper;
    private final PassengerInterestRepository passengerInterestRepository;
    private final PassengerRepository passengerRepository;
    private final UserRepository userRepository;
    private final PortRepository portRepository;
    private final KnowledgeGraphSparkService knowledgeGraphService;
    
    /**
     * Process and persist AIS data from Knowledge Graph
     */
    @RabbitListener(queues = "knowledge.graph.queue")
    @Transactional
    public void persistAisDataFromKnowledgeGraph(Object message) {
        log.info("Persisting AIS data from Knowledge Graph");
        
        try {
            if (message instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) message;
                
                // Query Knowledge Graph for ship data
                String mmsi = String.valueOf(data.get("mmsi"));
                List<Map<String, String>> shipData = knowledgeGraphService.executeSparqlQuery(
                    String.format(
                        "PREFIX cruise: <http://cruise.recommender.org/kg/> " +
                        "SELECT ?name ?lat ?long ?timestamp " +
                        "WHERE { " +
                        "  ?ship cruise:hasMMSI \"%s\" . " +
                        "  ?ship cruise:hasName ?name . " +
                        "  ?ship cruise:hasLocation ?location . " +
                        "  ?location geo:lat ?lat . " +
                        "  ?location geo:long ?long . " +
                        "  ?ship cruise:hasTimestamp ?timestamp . " +
                        "}",
                        mmsi
                    )
                );
                
                if (!shipData.isEmpty()) {
                    Map<String, String> ship = shipData.get(0);
                    
                    // Create AIS data entity
                    AisData aisData = AisData.builder()
                        .mmsi(mmsi)
                        .shipName(ship.get("name"))
                        .latitude(Double.parseDouble(ship.get("lat")))
                        .longitude(Double.parseDouble(ship.get("long")))
                        .timestamp(LocalDateTime.parse(ship.get("timestamp")))
                        .build();
                    
                    // Save to MySQL
                    AisData savedAisData = aisDataRepository.save(aisData);
                    
                    // Index in Elasticsearch
                    AisDataDocument document = documentMapper.toDocument(savedAisData);
                    aisDataElasticsearchRepository.save(document);
                    
                    log.info("Persisted AIS data for MMSI: {}", mmsi);
                }
            }
            
        } catch (Exception e) {
            log.error("Error persisting AIS data from Knowledge Graph", e);
        }
    }
    
    /**
     * Process and persist passenger interests from Knowledge Graph
     */
    @Transactional
    public void persistPassengerInterests(String passengerId) {
        log.info("Persisting passenger interests for: {}", passengerId);
        
        try {
            // Query Knowledge Graph for passenger interests
            List<Map<String, String>> interests = knowledgeGraphService.findPassengerInterests(passengerId);
            
            // Find or create user and passenger
            // Use email as identifier (passengerId could be email or username)
            String email = passengerId.contains("@") ? passengerId : passengerId + "@example.com";
            Optional<User> userOpt = userRepository.findByEmail(email);
            User user = userOpt.orElseGet(() -> {
                User newUser = User.builder()
                    .email(email)
                    .firstName(passengerId)
                    .lastName("")
                    .passwordHash("") // Empty password - should be set properly in production
                    .isActive(true)
                    .build();
                return userRepository.save(newUser);
            });
            
            // Find passenger
            List<Passenger> passengers = passengerRepository.findByUserId(user.getId());
            if (passengers.isEmpty()) {
                log.warn("No passenger found for user: {}. Skipping interest persistence.", passengerId);
                return;
            }
            
            Passenger passenger = passengers.get(0);
            
            // Create passenger interest entities
            for (Map<String, String> interest : interests) {
                String keyword = interest.get("keyword");
                String location = interest.get("location");
                
                // Categorize interest keyword
                String category = categorizeInterest(keyword);
                
                PassengerInterest passengerInterest = PassengerInterest.builder()
                    .passenger(passenger)
                    .interestCategory(category)
                    .interestKeyword(keyword)
                    .source(PassengerInterest.InterestSource.SOCIAL_MEDIA)
                    .confidenceScore(0.8) // Default confidence
                    .isExplicit(false) // Inferred from social media
                    .expressedAt(LocalDateTime.now())
                    .build();
                
                passengerInterestRepository.save(passengerInterest);
                
                log.debug("Created passenger interest: {} for location: {}", keyword, location);
            }
            
            log.info("Persisted {} passenger interests for user: {}", interests.size(), passengerId);
            
        } catch (Exception e) {
            log.error("Error persisting passenger interests", e);
        }
    }
    
    /**
     * Batch persist multiple passenger interests
     */
    @Transactional
    public void batchPersistPassengerInterests(List<String> passengerIds) {
        log.info("Batch persisting interests for {} passengers", passengerIds.size());
        
        for (String passengerId : passengerIds) {
            try {
                persistPassengerInterests(passengerId);
            } catch (Exception e) {
                log.error("Error persisting interests for passenger: {}", passengerId, e);
            }
        }
    }
    
    /**
     * Process and persist popular interests by location
     */
    @Transactional
    public void persistPopularInterestsByLocation(String location) {
        log.info("Persisting popular interests for location: {}", location);
        
        try {
            List<Map<String, String>> popularInterests = knowledgeGraphService.findPopularInterestsByLocation(location);
            
            // Find port by location (using the query method that exists)
            List<Port> ports = portRepository.findByNameContaining(location);
            Optional<Port> portOpt = ports.isEmpty() ? Optional.empty() : Optional.of(ports.get(0));
            
            if (portOpt.isPresent()) {
                Port port = portOpt.get();
                
                // Update port metadata with popular interests
                // This could be stored in a separate table or as JSON in port metadata
                log.info("Found {} popular interests for port: {}", popularInterests.size(), port.getName());
                
                // You could create Activity entities or update Port metadata here
            }
            
        } catch (Exception e) {
            log.error("Error persisting popular interests by location", e);
        }
    }
    
    /**
     * Sync data from Knowledge Graph to MySQL and Elasticsearch
     * This method can be called periodically to ensure data consistency
     */
    @Transactional
    public void syncKnowledgeGraphToDatabases() {
        log.info("Starting Knowledge Graph to databases sync");
        
        try {
            // Query all ships from Knowledge Graph
            List<Map<String, String>> ships = knowledgeGraphService.executeSparqlQuery(
                "PREFIX cruise: <http://cruise.recommender.org/kg/> " +
                "SELECT DISTINCT ?mmsi " +
                "WHERE { " +
                "  ?ship cruise:hasMMSI ?mmsi . " +
                "}"
            );
            
            for (Map<String, String> ship : ships) {
                String mmsi = ship.get("mmsi");
                
                // Get latest position for this ship
                List<Map<String, String>> positions = knowledgeGraphService.executeSparqlQuery(
                    String.format(
                        "PREFIX cruise: <http://cruise.recommender.org/kg/> " +
                        "PREFIX geo: <http://www.w3.org/2003/01/geo/wgs84_pos#> " +
                        "SELECT ?name ?lat ?long ?timestamp " +
                        "WHERE { " +
                        "  ?ship cruise:hasMMSI \"%s\" . " +
                        "  ?ship cruise:hasName ?name . " +
                        "  ?ship cruise:hasLocation ?location . " +
                        "  ?location geo:lat ?lat . " +
                        "  ?location geo:long ?long . " +
                        "  ?ship cruise:hasTimestamp ?timestamp . " +
                        "} " +
                        "ORDER BY DESC(?timestamp) " +
                        "LIMIT 1",
                        mmsi
                    )
                );
                
                if (!positions.isEmpty()) {
                    Map<String, String> position = positions.get(0);
                    
                    // Check if AIS data already exists
                    List<AisData> existingData = aisDataRepository.findByMmsiOrderByTimestampDesc(mmsi);
                    
                    if (existingData.isEmpty() || 
                        !existingData.get(0).getTimestamp().toString().equals(position.get("timestamp"))) {
                        
                        // Create new AIS data entry
                        AisData aisData = AisData.builder()
                            .mmsi(mmsi)
                            .shipName(position.get("name"))
                            .latitude(Double.parseDouble(position.get("lat")))
                            .longitude(Double.parseDouble(position.get("long")))
                            .timestamp(LocalDateTime.parse(position.get("timestamp")))
                            .build();
                        
                        AisData saved = aisDataRepository.save(aisData);
                        
                        // Index in Elasticsearch
                        AisDataDocument document = documentMapper.toDocument(saved);
                        aisDataElasticsearchRepository.save(document);
                        
                        log.debug("Synced AIS data for ship: {}", mmsi);
                    }
                }
            }
            
            log.info("Completed Knowledge Graph to databases sync");
            
        } catch (Exception e) {
            log.error("Error syncing Knowledge Graph to databases", e);
        }
    }
    
    /**
     * Create a passenger entity for a user
     * Note: This is a simplified version - in production, you'd need a CruiseSchedule
     */
    private Passenger createPassengerForUser(User user) {
        // For now, we'll skip creating a Passenger if no CruiseSchedule exists
        // In production, you'd either create a default schedule or handle this differently
        log.warn("Cannot create Passenger without CruiseSchedule for user: {}", user.getEmail());
        return null; // Return null - caller should handle this
    }
    
    /**
     * Categorize interest keyword into a category
     */
    private String categorizeInterest(String keyword) {
        String lowerKeyword = keyword.toLowerCase();
        
        if (lowerKeyword.contains("art") || lowerKeyword.contains("gallery") || lowerKeyword.contains("museum")) {
            return "ART";
        } else if (lowerKeyword.contains("history") || lowerKeyword.contains("historic") || lowerKeyword.contains("monument")) {
            return "HISTORY";
        } else if (lowerKeyword.contains("nature") || lowerKeyword.contains("beach") || lowerKeyword.contains("park")) {
            return "NATURE";
        } else if (lowerKeyword.contains("food") || lowerKeyword.contains("restaurant") || lowerKeyword.contains("dining")) {
            return "FOOD";
        } else if (lowerKeyword.contains("shop") || lowerKeyword.contains("market") || lowerKeyword.contains("souvenir")) {
            return "SHOPPING";
        } else if (lowerKeyword.contains("adventure") || lowerKeyword.contains("snorkel") || lowerKeyword.contains("dive")) {
            return "ADVENTURE";
        } else {
            return "GENERAL";
        }
    }
}

