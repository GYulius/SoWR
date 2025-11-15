package com.cruise.recommender.service;

import com.cruise.recommender.entity.AisData;
import com.cruise.recommender.entity.CruiseShip;
import com.cruise.recommender.repository.AisDataRepository;
import com.cruise.recommender.repository.CruiseShipRepository;
import com.cruise.recommender.repository.elasticsearch.AisDataDocument;
import com.cruise.recommender.repository.elasticsearch.AisDataDocumentMapper;
import com.cruise.recommender.repository.elasticsearch.AisDataElasticsearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for processing AIS (Automatic Identification System) data
 * Handles real-time ship tracking and position updates
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AisDataService {
    
    private final AisDataRepository aisDataRepository;
    private final CruiseShipRepository cruiseShipRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ElasticsearchOperations elasticsearchOperations;
    private final AisDataElasticsearchRepository aisDataElasticsearchRepository;
    private final AisDataDocumentMapper documentMapper;
    private final KnowledgeGraphSparkService knowledgeGraphService;
    
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
            
            // Publish to WebSocket for real-time updates
            publishPositionUpdate(ship, aisData);
            
            log.info("Successfully processed AIS data for ship: {}", ship.getName());
            
        } catch (Exception e) {
            log.error("Error processing AIS data for MMSI: {}", message.getMmsi(), e);
        }
    }
    
    /**
     * Find or create cruise ship based on AIS data
     */
    private CruiseShip findOrCreateShip(AisDataMessage message) {
        Optional<CruiseShip> existingShip = cruiseShipRepository.findByMmsi(message.getMmsi());
        
        if (existingShip.isPresent()) {
            return existingShip.get();
        }
        
        // Create new ship record
        // Extract cruise line from ship name if possible (e.g., "Royal Caribbean Harmony" -> "Royal Caribbean")
        String cruiseLine = extractCruiseLine(message.getShipName());
        
        CruiseShip newShip = CruiseShip.builder()
                .name(message.getShipName())
                .cruiseLine(cruiseLine)
                .capacity(estimateCapacityFromShipName(message.getShipName())) // Estimate capacity
                .mmsi(message.getMmsi())
                .imo(message.getImo())
                .callSign(message.getCallSign())
                .aisEnabled(true)
                .trackingStatus(CruiseShip.TrackingStatus.TRACKED)
                .build();
        
        return cruiseShipRepository.save(newShip);
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
     */
    private void updateShipPosition(CruiseShip ship, AisData aisData) {
        ship.setCurrentLatitude(aisData.getLatitude());
        ship.setCurrentLongitude(aisData.getLongitude());
        ship.setCurrentSpeed(aisData.getSpeed());
        ship.setCurrentCourse(aisData.getCourse());
        ship.setLastAisUpdate(aisData.getTimestamp());
        
        // Update tracking status based on signal quality
        if ("NONE".equals(aisData.getSignalQuality())) {
            ship.setTrackingStatus(CruiseShip.TrackingStatus.NO_SIGNAL);
        } else if (aisData.getStationRange() != null && aisData.getStationRange() > 50) {
            ship.setTrackingStatus(CruiseShip.TrackingStatus.OUT_OF_RANGE);
        } else {
            ship.setTrackingStatus(CruiseShip.TrackingStatus.TRACKED);
        }
        
        cruiseShipRepository.save(ship);
    }
    
    /**
     * Index AIS data in Elasticsearch for fast search
     */
    private void indexInElasticsearch(AisData aisData) {
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
     */
    @Transactional(readOnly = true)
    public List<CruiseShip> getShipsNearPort(Double portLatitude, Double portLongitude, Double radiusNauticalMiles) {
        // Calculate bounding box
        double latRange = radiusNauticalMiles / 60.0; // 1 nautical mile ≈ 1/60 degree latitude
        double lngRange = radiusNauticalMiles / (60.0 * Math.cos(Math.toRadians(portLatitude)));
        
        return cruiseShipRepository.findShipsInArea(
                portLatitude - latRange,
                portLatitude + latRange,
                portLongitude - lngRange,
                portLongitude + lngRange
        );
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
        log.info("Searching AIS data by ship name: {}", shipName);
        return aisDataElasticsearchRepository.findByShipNameContaining(shipName);
    }
    
    /**
     * Get AIS data history for a specific ship using Elasticsearch
     */
    @Transactional(readOnly = true)
    public List<AisDataDocument> getShipHistory(String mmsi) {
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
        log.info("Searching AIS data in area using Elasticsearch");
        return aisDataElasticsearchRepository.findByLatitudeBetweenAndLongitudeBetweenAndTimestampGreaterThanEqual(
                minLat, maxLat, minLng, maxLng, since);
    }
    
    /**
     * Get recent AIS data using Elasticsearch
     */
    @Transactional(readOnly = true)
    public List<AisDataDocument> getRecentAisData(int minutes) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutes);
        log.info("Getting recent AIS data since {} minutes ago", minutes);
        return aisDataElasticsearchRepository.findByTimestampGreaterThanEqual(since);
    }
    
    /**
     * Search AIS data by ship type using Elasticsearch
     */
    @Transactional(readOnly = true)
    public List<AisDataDocument> findAisDataByShipType(String shipType) {
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
