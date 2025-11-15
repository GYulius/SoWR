package com.cruise.recommender.service;

import com.cruise.recommender.entity.AisData;
import com.cruise.recommender.entity.CruiseShip;
import com.cruise.recommender.repository.AisDataRepository;
import com.cruise.recommender.repository.CruiseShipRepository;
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
    
    private static final String AIS_QUEUE = "ais.data.queue";
    private static final String AIS_EXCHANGE = "ais.exchange";
    
    /**
     * Process incoming AIS data from RabbitMQ
     */
    @RabbitListener(queues = AIS_QUEUE)
    public void processAisData(AisDataMessage message) {
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
        CruiseShip newShip = CruiseShip.builder()
                .name(message.getShipName())
                .mmsi(message.getMmsi())
                .imo(message.getImo())
                .callSign(message.getCallSign())
                .aisEnabled(true)
                .trackingStatus(CruiseShip.TrackingStatus.TRACKED)
                .build();
        
        return cruiseShipRepository.save(newShip);
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
            elasticsearchOperations.save(aisData);
            log.debug("Indexed AIS data in Elasticsearch for MMSI: {}", aisData.getMmsi());
        } catch (Exception e) {
            log.error("Error indexing AIS data in Elasticsearch", e);
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
    
    // DTOs for message handling
    @lombok.Data
    @lombok.Builder
    public static class AisDataMessage {
        private String mmsi;
        private String shipName;
        private Double latitude;
        private Double longitude;
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
