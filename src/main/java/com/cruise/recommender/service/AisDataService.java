package com.cruise.recommender.service;

import com.cruise.recommender.entity.AisData;
import com.cruise.recommender.entity.CruiseShip;
import com.cruise.recommender.repository.AisDataRepository;
import com.cruise.recommender.repository.CruiseShipRepository;
import com.cruise.recommender.repository.elasticsearch.AisDataDocument;
import com.cruise.recommender.repository.elasticsearch.AisDataDocumentMapper;
import com.cruise.recommender.repository.elasticsearch.AisDataElasticsearchRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Service for processing AIS (Automatic Identification System) data
 * Handles real-time ship tracking and position updates
 * 
 * Note: Elasticsearch is optional - service will work without it, but search features will be limited
 */
@Service
@Slf4j
@Transactional
public class AisDataService {
    
    private final AisDataRepository aisDataRepository;
    private final CruiseShipRepository cruiseShipRepository;
    private final RabbitTemplate rabbitTemplate;
    private final AisDataDocumentMapper documentMapper;
    private final KnowledgeGraphSparkService knowledgeGraphService;
    
    // Optional Elasticsearch dependencies - will be null if Elasticsearch is disabled
    private final ElasticsearchOperations elasticsearchOperations;
    private final AisDataElasticsearchRepository aisDataElasticsearchRepository;
    
    @Autowired
    public AisDataService(
            AisDataRepository aisDataRepository,
            CruiseShipRepository cruiseShipRepository,
            RabbitTemplate rabbitTemplate,
            AisDataDocumentMapper documentMapper,
            KnowledgeGraphSparkService knowledgeGraphService,
            @Autowired(required = false) ElasticsearchOperations elasticsearchOperations,
            @Autowired(required = false) AisDataElasticsearchRepository aisDataElasticsearchRepository) {
        this.aisDataRepository = aisDataRepository;
        this.cruiseShipRepository = cruiseShipRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.documentMapper = documentMapper;
        this.knowledgeGraphService = knowledgeGraphService;
        this.elasticsearchOperations = elasticsearchOperations;
        this.aisDataElasticsearchRepository = aisDataElasticsearchRepository;
        
        if (aisDataElasticsearchRepository == null) {
            log.warn("Elasticsearch is not available. AIS data search features will be limited.");
        }
    }
    
    private static final String AIS_QUEUE = "ais.data.queue";
    private static final String AIS_EXCHANGE = "ais.exchange";
    
    /**
     * Process incoming AIS data from RabbitMQ
     */
    @RabbitListener(queues = AIS_QUEUE)
    public void processAisData(AisDataMessage message) {
        // Validate message
        if (message == null) {
            log.warn("Received null AIS data message, skipping");
            return;
        }
        
        // Validate MMSI - it's required
        String mmsi = message.getMmsi();
        if (mmsi == null || mmsi.trim().isEmpty() || "null".equalsIgnoreCase(mmsi)) {
            log.warn("Received AIS data message with null or empty MMSI, skipping. " +
                    "Ship name: {}, Data source: {}, Message: {}", 
                    message.getShipName(), 
                    message.getDataSource() != null ? message.getDataSource() : "unknown",
                    message);
            
            // This might be an old message from before validation was added
            // Consider purging the RabbitMQ queue if this persists
            return;
        }
        
        // Ensure MMSI is trimmed
        message.setMmsi(mmsi.trim());
        
        log.info("Processing AIS data for MMSI: {}", message.getMmsi());
        
        try {
            // Find or create cruise ship
            CruiseShip ship = findOrCreateShip(message);
            
            // Skip processing if ship could not be found/created (e.g., missing MMSI)
            if (ship == null) {
                log.warn("Cannot process AIS data: ship not found or created. MMSI: {}, Ship name: {}", 
                        message.getMmsi(), message.getShipName());
                return;
            }
            
            // Create AIS data record
            AisData aisData = AisData.builder()
                    .mmsi(message.getMmsi())
                    .shipName(message.getShipName())
                    .latitude(message.getLatitude())
                    .longitude(message.getLongitude())
                    .timestamp(message.getTimestamp())
                    .speed(message.getSpeed())
                    .course(message.getCourse())
                    .heading(message.getHeading())
                    .shipType(message.getShipType())
                    .destination(message.getDestination())
                    .eta(message.getEta())
                    .imo(message.getImo())
                    .callSign(message.getCallSign())
                    .stationRange(message.getStationRange())
                    .signalQuality(message.getSignalQuality())
                    .dataSource(message.getDataSource())
                    .cruiseShip(ship)
                    .build();
            
            // Save to MySQL
            aisDataRepository.save(aisData);
            
            // Index in Elasticsearch for fast search
            indexInElasticsearch(aisData);
            
            // Update ship's current position
            updateShipPosition(ship, aisData);
            
            // Process to Knowledge Graph (RDF)
            try {
                knowledgeGraphService.processAisDataToRDF(message);
            } catch (Exception e) {
                log.warn("Failed to process AIS data to Knowledge Graph, continuing anyway", e);
            }
            
            // Trigger recommendation orchestrator (if ship is near a port)
            try {
                // This will be injected via constructor
                // recommendationOrchestratorService.handleAisDataUpdate(message);
            } catch (Exception e) {
                log.warn("Failed to trigger recommendation orchestrator, continuing anyway", e);
            }
            
            // Publish to WebSocket for real-time updates
            publishPositionUpdate(ship, aisData);
            
            log.info("Successfully processed AIS data for ship: {}", ship.getName());
            
        } catch (Exception e) {
            log.error("Error processing AIS data for MMSI: {}", message.getMmsi(), e);
        }
    }
    
    /**
     * Find or create cruise ship based on AIS data
     * Tries to match by MMSI first, then by ship name if MMSI is missing
     * Returns null only if ship cannot be found or created
     */
    private CruiseShip findOrCreateShip(AisDataMessage message) {
        // Try to find by MMSI first (preferred method)
        if (message.getMmsi() != null && !message.getMmsi().trim().isEmpty()) {
            Optional<CruiseShip> existingShip = cruiseShipRepository.findByMmsi(message.getMmsi().trim());
            if (existingShip.isPresent()) {
                return existingShip.get();
            }
        }
        
        // If MMSI lookup failed or MMSI is missing, try to find by ship name
        if (message.getShipName() != null && !message.getShipName().trim().isEmpty()) {
            String shipName = message.getShipName().trim();
            // Try exact match first
            List<CruiseShip> shipsByName = cruiseShipRepository.findAll().stream()
                .filter(ship -> ship.getName() != null && ship.getName().trim().equalsIgnoreCase(shipName))
                .collect(java.util.stream.Collectors.toList());
            
            if (!shipsByName.isEmpty()) {
                CruiseShip foundShip = shipsByName.get(0);
                log.info("Found ship by name match: {} (MMSI: {})", foundShip.getName(), foundShip.getMmsi());
                
                // If the message has MMSI but ship in DB doesn't, update the ship
                if (message.getMmsi() != null && !message.getMmsi().trim().isEmpty() && 
                    (foundShip.getMmsi() == null || foundShip.getMmsi().trim().isEmpty())) {
                    foundShip.setMmsi(message.getMmsi().trim());
                    if (message.getImo() != null && !message.getImo().trim().isEmpty()) {
                        foundShip.setImo(message.getImo().trim());
                    }
                    if (message.getCallSign() != null && !message.getCallSign().trim().isEmpty()) {
                        foundShip.setCallSign(message.getCallSign().trim());
                    }
                    cruiseShipRepository.save(foundShip);
                }
                
                return foundShip;
            }
            
            // Try partial match (contains)
            shipsByName = cruiseShipRepository.findAll().stream()
                .filter(ship -> ship.getName() != null && 
                       (ship.getName().trim().equalsIgnoreCase(shipName) ||
                        ship.getName().trim().toLowerCase().contains(shipName.toLowerCase()) ||
                        shipName.toLowerCase().contains(ship.getName().trim().toLowerCase())))
                .collect(java.util.stream.Collectors.toList());
            
            if (!shipsByName.isEmpty()) {
                CruiseShip foundShip = shipsByName.get(0);
                log.info("Found ship by partial name match: {} (MMSI: {})", foundShip.getName(), foundShip.getMmsi());
                
                // Update MMSI if provided
                if (message.getMmsi() != null && !message.getMmsi().trim().isEmpty() && 
                    (foundShip.getMmsi() == null || foundShip.getMmsi().trim().isEmpty())) {
                    foundShip.setMmsi(message.getMmsi().trim());
                    if (message.getImo() != null && !message.getImo().trim().isEmpty()) {
                        foundShip.setImo(message.getImo().trim());
                    }
                    if (message.getCallSign() != null && !message.getCallSign().trim().isEmpty()) {
                        foundShip.setCallSign(message.getCallSign().trim());
                    }
                    cruiseShipRepository.save(foundShip);
                }
                
                return foundShip;
            }
        }
        
        // If MMSI is present, create new ship record
        if (message.getMmsi() != null && !message.getMmsi().trim().isEmpty()) {
            // Extract cruise line from ship name if possible
            String cruiseLine = extractCruiseLine(message.getShipName());
            
            CruiseShip newShip = CruiseShip.builder()
                    .name(message.getShipName() != null ? message.getShipName().trim() : "Unknown")
                    .cruiseLine(cruiseLine)
                    .capacity(estimateCapacityFromShipName(message.getShipName())) // Estimate capacity
                    .mmsi(message.getMmsi().trim())
                    .imo(message.getImo() != null && !message.getImo().trim().isEmpty() ? message.getImo().trim() : null)
                    .callSign(message.getCallSign() != null && !message.getCallSign().trim().isEmpty() ? message.getCallSign().trim() : null)
                    .aisEnabled(true)
                    .trackingStatus(CruiseShip.TrackingStatus.TRACKED)
                    .build();
            
            return cruiseShipRepository.save(newShip);
        }
        
        // Cannot create ship without MMSI and no matching ship found by name
        log.warn("Cannot find or create ship: MMSI missing and no matching ship found by name '{}'", 
                message.getShipName());
        return null;
    }
    
    /**
     * Extract cruise line from ship name
     */
    private String extractCruiseLine(String shipName) {
        if (shipName == null || shipName.isEmpty()) {
            return "Unknown Cruise Line";
        }
        
        // Common cruise line patterns
        String[] cruiseLines = {
            "Royal Caribbean", "MSC", "Carnival", "Norwegian", 
            "Celebrity", "Princess", "Holland America", "Costa"
        };
        
        for (String line : cruiseLines) {
            if (shipName.contains(line)) {
                return line;
            }
        }
        
        // If no match, try to extract first word(s)
        String[] parts = shipName.split("\\s+");
        if (parts.length >= 2) {
            return parts[0] + " " + parts[1]; // First two words
        }
        
        return "Unknown Cruise Line";
    }
    
    /**
     * Estimate ship capacity based on ship name patterns
     */
    private Integer estimateCapacityFromShipName(String shipName) {
        if (shipName == null || shipName.isEmpty()) {
            return 2000; // Default medium-sized ship
        }
        
        String lowerName = shipName.toLowerCase();
        
        // Large ships (5000+)
        if (lowerName.contains("harmony") || lowerName.contains("symphony") || 
            lowerName.contains("grandiosa") || lowerName.contains("mardi gras")) {
            return 5000 + (int)(Math.random() * 2000); // 5000-7000
        }
        
        // Medium-large ships (3000-5000)
        if (lowerName.contains("vista") || lowerName.contains("escape") || 
            lowerName.contains("edge") || lowerName.contains("regal")) {
            return 3000 + (int)(Math.random() * 2000); // 3000-5000
        }
        
        // Medium ships (2000-3000)
        return 2000 + (int)(Math.random() * 1000); // 2000-3000
    }
    
    /**
     * Update ship's current position
     * Uses optimistic locking to prevent deadlocks
     */
    private void updateShipPosition(CruiseShip ship, AisData aisData) {
        try {
            // Reload ship to get latest version and avoid deadlocks
            Optional<CruiseShip> reloadedShip = cruiseShipRepository.findById(ship.getId());
            if (reloadedShip.isEmpty()) {
                log.warn("Ship {} not found when updating position", ship.getId());
                return;
            }
            
            CruiseShip shipToUpdate = reloadedShip.get();
            shipToUpdate.setCurrentLatitude(aisData.getLatitude());
            shipToUpdate.setCurrentLongitude(aisData.getLongitude());
            shipToUpdate.setCurrentSpeed(aisData.getSpeed());
            shipToUpdate.setCurrentCourse(aisData.getCourse());
            shipToUpdate.setLastAisUpdate(aisData.getTimestamp());
            
            // Update tracking status based on signal quality
            if ("NONE".equals(aisData.getSignalQuality())) {
                shipToUpdate.setTrackingStatus(CruiseShip.TrackingStatus.NO_SIGNAL);
            } else if (aisData.getStationRange() != null && aisData.getStationRange() > 50) {
                shipToUpdate.setTrackingStatus(CruiseShip.TrackingStatus.OUT_OF_RANGE);
            } else {
                shipToUpdate.setTrackingStatus(CruiseShip.TrackingStatus.TRACKED);
            }
            
            cruiseShipRepository.save(shipToUpdate);
        } catch (org.springframework.dao.CannotAcquireLockException e) {
            log.warn("Deadlock detected when updating ship position for ship {}. Retrying...", ship.getId());
            // Retry once after a short delay
            try {
                Thread.sleep(100 + (long)(Math.random() * 100)); // Random delay 100-200ms
                Optional<CruiseShip> retryShip = cruiseShipRepository.findById(ship.getId());
                if (retryShip.isPresent()) {
                    CruiseShip shipToUpdate = retryShip.get();
                    shipToUpdate.setCurrentLatitude(aisData.getLatitude());
                    shipToUpdate.setCurrentLongitude(aisData.getLongitude());
                    shipToUpdate.setCurrentSpeed(aisData.getSpeed());
                    shipToUpdate.setCurrentCourse(aisData.getCourse());
                    shipToUpdate.setLastAisUpdate(aisData.getTimestamp());
                    cruiseShipRepository.save(shipToUpdate);
                }
            } catch (Exception retryException) {
                log.error("Failed to retry ship position update after deadlock", retryException);
            }
        } catch (Exception e) {
            log.error("Error updating ship position", e);
        }
    }
    
    /**
     * Index AIS data in Elasticsearch for fast search
     */
    private void indexInElasticsearch(AisData aisData) {
        if (aisDataElasticsearchRepository == null) {
            // Elasticsearch not available, skip indexing
            return;
        }
        
        try {
            AisDataDocument document = documentMapper.toDocument(aisData);
            aisDataElasticsearchRepository.save(document);
            log.debug("Indexed AIS data in Elasticsearch for MMSI: {}", aisData.getMmsi());
        } catch (Exception e) {
            log.error("Error indexing AIS data in Elasticsearch", e);
            // Don't fail the transaction if Elasticsearch indexing fails
        }
    }
    
    /**
     * Publish position update to WebSocket subscribers
     */
    private void publishPositionUpdate(CruiseShip ship, AisData aisData) {
        ShipPositionUpdate update = ShipPositionUpdate.builder()
                .shipId(ship.getId())
                .shipName(ship.getName())
                .latitude(aisData.getLatitude())
                .longitude(aisData.getLongitude())
                .speed(aisData.getSpeed())
                .course(aisData.getCourse())
                .timestamp(aisData.getTimestamp())
                .trackingStatus(ship.getTrackingStatus().name())
                .build();
        
        rabbitTemplate.convertAndSend(AIS_EXCHANGE, "ship.position.update", update);
    }
    
    /**
     * Get current positions of all tracked ships
     */
    @Transactional(readOnly = true)
    public List<CruiseShip> getCurrentShipPositions() {
        return cruiseShipRepository.findByAisEnabledTrue();
    }
    
    /**
     * Get ships within range of a port
     * Uses both CruiseShip entities and recent AIS data from Elasticsearch
     */
    @Transactional(readOnly = true)
    public List<CruiseShip> getShipsNearPort(Double portLatitude, Double portLongitude, Double radiusNauticalMiles) {
        // Calculate bounding box
        double latRange = radiusNauticalMiles / 60.0; // 1 nautical mile ≈ 1/60 degree latitude
        double lngRange = radiusNauticalMiles / (60.0 * Math.cos(Math.toRadians(portLatitude)));
        
        List<CruiseShip> ships = cruiseShipRepository.findShipsInArea(
                portLatitude - latRange,
                portLatitude + latRange,
                portLongitude - lngRange,
                portLongitude + lngRange
        );
        
        // Also check recent AIS data from Elasticsearch (last hour) for more accurate positions
        if (aisDataElasticsearchRepository != null) {
            try {
                LocalDateTime since = LocalDateTime.now().minusHours(1);
                List<AisDataDocument> recentAisData = aisDataElasticsearchRepository
                    .findByLatitudeBetweenAndLongitudeBetweenAndTimestampGreaterThanEqual(
                        portLatitude - latRange,
                        portLatitude + latRange,
                        portLongitude - lngRange,
                        portLongitude + lngRange,
                        since
                    );
                
                // Update ship positions from recent AIS data
                for (AisDataDocument aisDoc : recentAisData) {
                    if (aisDoc.getCruiseShipId() != null) {
                        Optional<CruiseShip> shipOpt = cruiseShipRepository.findById(aisDoc.getCruiseShipId());
                        if (shipOpt.isPresent()) {
                            CruiseShip ship = shipOpt.get();
                            // Update position if AIS data is more recent
                            if (ship.getLastAisUpdate() == null || 
                                (aisDoc.getTimestamp() != null && 
                                 aisDoc.getTimestamp().isAfter(ship.getLastAisUpdate()))) {
                                ship.setCurrentLatitude(aisDoc.getLatitude());
                                ship.setCurrentLongitude(aisDoc.getLongitude());
                                ship.setCurrentSpeed(aisDoc.getSpeed());
                                ship.setCurrentCourse(aisDoc.getCourse());
                                // Don't save here - just update in memory for response
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error fetching AIS data from Elasticsearch: {}", e.getMessage());
            }
        }
        
        return ships;
    }
    
    /**
     * Scheduled task to check for ships that haven't reported in a while
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void checkStaleShips() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(1);
        List<CruiseShip> staleShips = cruiseShipRepository.findByLastAisUpdateBefore(threshold);
        
        for (CruiseShip ship : staleShips) {
            if (ship.getTrackingStatus() != CruiseShip.TrackingStatus.OFFLINE) {
                ship.setTrackingStatus(CruiseShip.TrackingStatus.NO_SIGNAL);
                cruiseShipRepository.save(ship);
                log.warn("Ship {} has not reported AIS data for over 1 hour", ship.getName());
            }
        }
    }
    
    /**
     * Search AIS data using Elasticsearch (fast full-text search)
     */
    @Transactional(readOnly = true)
    public List<AisDataDocument> searchAisDataByShipName(String shipName) {
        if (aisDataElasticsearchRepository == null) {
            log.warn("Elasticsearch not available. Cannot search by ship name.");
            return Collections.emptyList();
        }
        log.info("Searching AIS data by ship name: {}", shipName);
        return aisDataElasticsearchRepository.findByShipNameContaining(shipName);
    }
    
    /**
     * Get AIS data history for a specific ship using Elasticsearch
     */
    @Transactional(readOnly = true)
    public List<AisDataDocument> getShipHistory(String mmsi) {
        if (aisDataElasticsearchRepository == null) {
            log.warn("Elasticsearch not available. Cannot get ship history.");
            return Collections.emptyList();
        }
        log.info("Getting AIS history for MMSI: {}", mmsi);
        return aisDataElasticsearchRepository.findByMmsiOrderByTimestampDesc(mmsi);
    }
    
    /**
     * Find AIS data in geographic area using Elasticsearch (faster than MySQL)
     */
    @Transactional(readOnly = true)
    public List<AisDataDocument> findAisDataInArea(
            Double minLat, Double maxLat, 
            Double minLng, Double maxLng, 
            LocalDateTime since) {
        if (aisDataElasticsearchRepository == null) {
            log.warn("Elasticsearch not available. Cannot search by area.");
            return Collections.emptyList();
        }
        log.info("Searching AIS data in area using Elasticsearch");
        return aisDataElasticsearchRepository.findByLatitudeBetweenAndLongitudeBetweenAndTimestampGreaterThanEqual(
                minLat, maxLat, minLng, maxLng, since);
    }
    
    /**
     * Get recent AIS data using Elasticsearch
     */
    @Transactional(readOnly = true)
    public List<AisDataDocument> getRecentAisData(int minutes) {
        if (aisDataElasticsearchRepository == null) {
            log.warn("Elasticsearch not available. Cannot get recent AIS data.");
            return Collections.emptyList();
        }
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutes);
        log.info("Getting recent AIS data since {} minutes ago", minutes);
        return aisDataElasticsearchRepository.findByTimestampGreaterThanEqual(since);
    }
    
    /**
     * Search AIS data by ship type using Elasticsearch
     */
    @Transactional(readOnly = true)
    public List<AisDataDocument> findAisDataByShipType(String shipType) {
        if (aisDataElasticsearchRepository == null) {
            log.warn("Elasticsearch not available. Cannot search by ship type.");
            return Collections.emptyList();
        }
        log.info("Searching AIS data by ship type: {}", shipType);
        return aisDataElasticsearchRepository.findByShipType(shipType);
    }
    
    // DTOs for message handling
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class AisDataMessage {
        @com.fasterxml.jackson.annotation.JsonProperty(value = "mmsi", required = true)
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        private String mmsi;
        private String shipName;
        private Double latitude;
        private Double longitude;
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime timestamp;
        private Double speed;
        private Double course;
        private Integer heading;
        private String shipType;
        private String destination;
        private String eta;
        private String imo;
        private String callSign;
        private Double stationRange;
        private String signalQuality;
        private String dataSource;
        
        /**
         * Custom setter to ensure MMSI is trimmed and validated
         * This is called by Jackson during deserialization
         */
        public void setMmsi(String mmsi) {
            if (mmsi != null && !mmsi.trim().isEmpty() && !"null".equalsIgnoreCase(mmsi)) {
                this.mmsi = mmsi.trim();
            } else {
                // Don't set null - this helps identify deserialization issues
                this.mmsi = null;
            }
        }
        
    }
    
    @lombok.Data
    @lombok.Builder
    public static class ShipPositionUpdate {
        private Long shipId;
        private String shipName;
        private Double latitude;
        private Double longitude;
        private Double speed;
        private Double course;
        private LocalDateTime timestamp;
        private String trackingStatus;
    }
}
