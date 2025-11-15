package com.cruise.recommender.service;

import com.cruise.recommender.config.RabbitMQConfig;
import com.cruise.recommender.service.AisDataService.AisDataMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

@SuppressWarnings("unchecked")

/**
 * Service for ingesting AIS (Automatic Identification System) data from external sources
 * Supports multiple data sources: APIs, webhooks, and simulated data for testing
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AisDataIngestionService {
    
    private final RabbitTemplate rabbitTemplate;
    private final RestTemplate restTemplate = new RestTemplate();
    
    @Value("${ais.data.source.api.url:}")
    private String aisApiUrl;
    
    @Value("${ais.data.source.api.key:}")
    private String aisApiKey;
    
    @Value("${ais.data.simulation.enabled:true}")
    private boolean simulationEnabled;
    
    @Value("${ais.data.ingestion.interval:30000}")
    private long ingestionInterval;
    
    private final Random random = new Random();
    
    /**
     * Scheduled task to fetch AIS data from external sources
     * Runs every 30 seconds by default
     */
    @Scheduled(fixedRateString = "${ais.data.ingestion.interval:30000}")
    public void ingestAisData() {
        log.debug("Starting AIS data ingestion cycle");
        
        try {
            List<AisDataMessage> aisDataList = new ArrayList<>();
            
            if (simulationEnabled) {
                // Generate simulated AIS data for testing
                aisDataList = generateSimulatedAisData();
            } else if (aisApiUrl != null && !aisApiUrl.isEmpty()) {
                // Fetch from external API
                aisDataList = fetchFromExternalApi();
            } else {
                log.warn("No AIS data source configured. Enable simulation or configure API URL.");
                return;
            }
            
            // Send each AIS data message to RabbitMQ
            for (AisDataMessage message : aisDataList) {
                // Validate message before sending
                if (message == null) {
                    log.warn("Skipping null AIS data message");
                    continue;
                }
                
                String mmsi = message.getMmsi();
                if (mmsi == null || mmsi.trim().isEmpty() || "null".equalsIgnoreCase(mmsi)) {
                    log.error("CRITICAL: Attempting to send AIS message with invalid MMSI. " +
                            "Ship: {}, Message object: {}", message.getShipName(), message);
                    continue;
                }
                
                // Log before sending to help debug
                log.debug("Sending AIS data to queue - MMSI: {}, Ship: {}", mmsi, message.getShipName());
                
                try {
                    rabbitTemplate.convertAndSend(
                        RabbitMQConfig.AIS_EXCHANGE,
                        "ais.data.raw",
                        message
                    );
                    log.debug("Successfully sent AIS data to queue for MMSI: {}", mmsi);
                } catch (Exception e) {
                    log.error("Error sending AIS data to queue for MMSI: {}", mmsi, e);
                }
            }
            
            log.info("Ingested {} AIS data records", aisDataList.size());
            
        } catch (Exception e) {
            log.error("Error during AIS data ingestion", e);
        }
    }
    
    /**
     * Fetch AIS data from external API
     * Supports various AIS data providers (MarineTraffic, VesselFinder, etc.)
     */
    private List<AisDataMessage> fetchFromExternalApi() {
        List<AisDataMessage> aisDataList = new ArrayList<>();
        
        try {
            // Example: MarineTraffic API or similar
            String url = aisApiUrl + "?api_key=" + aisApiKey;
            
            // Make API call (adjust based on actual API response format)
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            // Parse response and convert to AisDataMessage
            // This is a placeholder - adjust based on actual API response structure
            if (response != null && response.containsKey("data")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> vessels = (List<Map<String, Object>>) response.get("data");
                
                for (Map<String, Object> vessel : vessels) {
                    AisDataMessage message = convertApiResponseToMessage(vessel);
                    if (message != null) {
                        aisDataList.add(message);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Error fetching AIS data from external API", e);
        }
        
        return aisDataList;
    }
    
    /**
     * Convert API response to AisDataMessage
     */
    private AisDataMessage convertApiResponseToMessage(Map<String, Object> vessel) {
        try {
            // Extract and validate MMSI - it's required
            String mmsi = String.valueOf(vessel.getOrDefault("mmsi", "")).trim();
            if (mmsi.isEmpty() || "null".equalsIgnoreCase(mmsi)) {
                // Try alternative field names
                mmsi = String.valueOf(vessel.getOrDefault("MMSI", "")).trim();
                if (mmsi.isEmpty() || "null".equalsIgnoreCase(mmsi)) {
                    mmsi = String.valueOf(vessel.getOrDefault("vessel_id", "")).trim();
                }
            }
            
            // MMSI is required - skip if not available
            if (mmsi.isEmpty() || "null".equalsIgnoreCase(mmsi)) {
                log.warn("Skipping vessel data: MMSI is missing or invalid. Vessel data: {}", vessel);
                return null;
            }
            
            return AisDataMessage.builder()
                .mmsi(mmsi)
                .shipName(String.valueOf(vessel.getOrDefault("ship_name", vessel.getOrDefault("name", "Unknown"))).trim())
                .latitude(getDoubleValue(vessel, "latitude"))
                .longitude(getDoubleValue(vessel, "longitude"))
                .timestamp(LocalDateTime.now())
                .speed(getDoubleValue(vessel, "speed"))
                .course(getDoubleValue(vessel, "course"))
                .heading(getIntegerValue(vessel, "heading"))
                .shipType(String.valueOf(vessel.getOrDefault("ship_type", "")).trim())
                .destination(String.valueOf(vessel.getOrDefault("destination", "")).trim())
                .eta(String.valueOf(vessel.getOrDefault("eta", "")).trim())
                .imo(String.valueOf(vessel.getOrDefault("imo", "")).trim())
                .callSign(String.valueOf(vessel.getOrDefault("callsign", vessel.getOrDefault("call_sign", ""))).trim())
                .stationRange(getDoubleValue(vessel, "station_range"))
                .signalQuality(String.valueOf(vessel.getOrDefault("signal_quality", "GOOD")).trim())
                .dataSource(String.valueOf(vessel.getOrDefault("data_source", "API")).trim())
                .build();
        } catch (Exception e) {
            log.error("Error converting API response to AIS message", e);
            return null;
        }
    }
    
    /**
     * Generate simulated AIS data for testing and development
     */
    private List<AisDataMessage> generateSimulatedAisData() {
        List<AisDataMessage> aisDataList = new ArrayList<>();
        
        // Simulate 3-5 ships
        int shipCount = 3 + random.nextInt(3);
        
        // Sample cruise ship MMSIs and names
        String[] shipNames = {
            "Royal Caribbean Harmony", "MSC Grandiosa", "Carnival Vista",
            "Norwegian Escape", "Celebrity Edge", "Princess Regal"
        };
        
        String[] mmsis = {
            "311000123", "247041200", "310627000",
            "311000456", "247041300", "310627100"
        };
        
        // Sample port locations (Miami, Barcelona, Venice, etc.)
        double[][] portLocations = {
            {25.7617, -80.1918}, // Miami
            {41.3851, 2.1734},   // Barcelona
            {45.4408, 12.3155},  // Venice
            {40.7128, -74.0060}, // New York
            {33.7490, -84.3880}  // Atlanta (inland example)
        };
        
        for (int i = 0; i < shipCount; i++) {
            int shipIndex = i % shipNames.length;
            int portIndex = random.nextInt(portLocations.length);
            
            // Generate position near a port (within 50 nautical miles)
            double baseLat = portLocations[portIndex][0];
            double baseLng = portLocations[portIndex][1];
            
            // Add random offset (approximately 0.5-1.0 degrees = 30-60 nautical miles)
            double latOffset = (random.nextDouble() - 0.5) * 1.0;
            double lngOffset = (random.nextDouble() - 0.5) * 1.0;
            
            double latitude = baseLat + latOffset;
            double longitude = baseLng + lngOffset;
            
            // Generate realistic speed (cruise ships typically 15-25 knots)
            double speed = 15 + random.nextDouble() * 10;
            
            // Generate course (0-360 degrees)
            double course = random.nextDouble() * 360;
            
            // Ensure shipIndex is within bounds for mmsis array
            int mmsiIndex = shipIndex % mmsis.length;
            String mmsi = mmsis[mmsiIndex];
            
            // Validate MMSI before building message
            if (mmsi == null || mmsi.trim().isEmpty()) {
                log.warn("Skipping ship {}: MMSI is null or empty", shipNames[shipIndex]);
                continue;
            }
            
            AisDataMessage message = AisDataMessage.builder()
                .mmsi(mmsi.trim())
                .shipName(shipNames[shipIndex])
                .latitude(latitude)
                .longitude(longitude)
                .timestamp(LocalDateTime.now())
                .speed(speed)
                .course(course)
                .heading((int) course)
                .shipType("Passenger Ship")
                .destination(getRandomDestination())
                .eta(LocalDateTime.now().plusHours(2 + random.nextInt(24)).toString())
                .imo("IMO" + (9000000 + random.nextInt(1000000)))
                .callSign("CALL" + random.nextInt(1000))
                .stationRange(5 + random.nextDouble() * 20)
                .signalQuality(getRandomSignalQuality())
                .dataSource("SIMULATION")
                .build();
            
            // Double-check MMSI is set after building
            if (message.getMmsi() == null || message.getMmsi().trim().isEmpty()) {
                log.error("MMSI is null after building message for ship: {}", shipNames[shipIndex]);
                continue;
            }
            
            aisDataList.add(message);
            log.debug("Generated simulated AIS data for MMSI: {}, Ship: {}", message.getMmsi(), message.getShipName());
        }
        
        return aisDataList;
    }
    
    private String getRandomDestination() {
        String[] destinations = {
            "Miami, FL", "Barcelona, Spain", "Venice, Italy",
            "New York, NY", "Los Angeles, CA", "Rome, Italy"
        };
        return destinations[random.nextInt(destinations.length)];
    }
    
    private String getRandomSignalQuality() {
        String[] qualities = {"GOOD", "FAIR", "POOR"};
        return qualities[random.nextInt(qualities.length)];
    }
    
    private String getRandomDataSource() {
        String[] sources = {"SATELLITE", "TERRESTRIAL", "BOTH"};
        return sources[random.nextInt(sources.length)];
    }
    
    private Double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    private Integer getIntegerValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * Webhook endpoint for receiving AIS data from external systems
     * Can be called by external AIS transceivers or data providers
     */
    public void receiveAisWebhook(Map<String, Object> webhookData) {
        log.info("Received AIS data via webhook");
        
        try {
            AisDataMessage message = convertApiResponseToMessage(webhookData);
            if (message != null && message.getMmsi() != null && !message.getMmsi().trim().isEmpty()) {
                rabbitTemplate.convertAndSend(
                    RabbitMQConfig.AIS_EXCHANGE,
                    "ais.data.raw",
                    message
                );
                log.info("Processed AIS webhook data for MMSI: {}", message.getMmsi());
            } else {
                log.warn("Skipping AIS webhook data: invalid message or missing MMSI");
            }
        } catch (Exception e) {
            log.error("Error processing AIS webhook", e);
        }
    }
}

