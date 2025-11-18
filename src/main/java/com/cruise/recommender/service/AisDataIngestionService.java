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
    private final OpenAisApiClient openAisApiClient;
    
    @Value("${ais.data.source.api.url:}")
    private String aisApiUrl;
    
    @Value("${ais.data.source.api.key:}")
    private String aisApiKey;
    
    @Value("${ais.data.source.api.provider:MARINETRAFFIC}")
    private String aisProvider;
    
    @Value("${ais.data.simulation.enabled:false}")
    private boolean simulationEnabled;
    
    @Value("${openais.enabled:false}")
    private boolean openAisEnabled;
    
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
            } else if (openAisEnabled) {
                // Fetch from Open-AIS (free Norwegian coastguard data)
                log.debug("Fetching AIS data from Open-AIS");
                aisDataList = openAisApiClient.fetchAisData();
            } else if (aisApiUrl != null && !aisApiUrl.isEmpty()) {
                // Fetch from external API (MarineTraffic, VesselFinder, etc.)
                aisDataList = fetchFromExternalApi();
            } else {
                log.warn("No AIS data source configured. Enable simulation, Open-AIS, or configure API URL.");
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
     * Supports various AIS data providers (MarineTraffic, VesselFinder, AISHub, etc.)
     */
    private List<AisDataMessage> fetchFromExternalApi() {
        List<AisDataMessage> aisDataList = new ArrayList<>();
        
        if (aisApiUrl == null || aisApiUrl.isEmpty()) {
            log.warn("AIS API URL is not configured. Cannot fetch real AIS data.");
            return aisDataList;
        }
        
        if (aisApiKey == null || aisApiKey.isEmpty()) {
            log.warn("AIS API key is not configured. Cannot fetch real AIS data.");
            return aisDataList;
        }
        
        try {
            String url = buildApiUrl();
            log.debug("Fetching AIS data from: {}", url.replace(aisApiKey, "***"));
            
            // Make API call (adjust based on actual API response format)
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            // Parse response and convert to AisDataMessage
            // Different providers have different response formats
            if (response != null) {
                List<Map<String, Object>> vessels = parseApiResponse(response);
                
                for (Map<String, Object> vessel : vessels) {
                    AisDataMessage message = convertApiResponseToMessage(vessel);
                    if (message != null && message.getMmsi() != null && !message.getMmsi().trim().isEmpty()) {
                        aisDataList.add(message);
                        log.debug("Fetched AIS data for MMSI: {}, Ship: {}, Source: {}", 
                                message.getMmsi(), message.getShipName(), message.getDataSource());
                    }
                }
            }
            
            log.info("Fetched {} AIS data records from {} provider", aisDataList.size(), aisProvider);
            
        } catch (Exception e) {
            log.error("Error fetching AIS data from external API: {}", e.getMessage(), e);
        }
        
        return aisDataList;
    }
    
    /**
     * Build API URL based on provider type
     */
    private String buildApiUrl() {
        String provider = aisProvider.toUpperCase();
        
        switch (provider) {
            case "MARINETRAFFIC":
                // MarineTraffic API format
                return aisApiUrl + "?api_key=" + aisApiKey + "&timespan=10&protocol=jsono";
            case "VESSELFINDER":
                // VesselFinder API format
                return aisApiUrl + "?api_key=" + aisApiKey;
            case "AISHUB":
                // AISHub API format
                return aisApiUrl + "?key=" + aisApiKey + "&format=json";
            case "OPENAIS":
                // Open-AIS uses PG_FeatureServ API (handled separately)
                return aisApiUrl;
            default:
                // Generic format
                return aisApiUrl + "?api_key=" + aisApiKey;
        }
    }
    
    /**
     * Parse API response based on provider format
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseApiResponse(Map<String, Object> response) {
        String provider = aisProvider.toUpperCase();
        List<Map<String, Object>> vessels = new ArrayList<>();
        
        switch (provider) {
            case "MARINETRAFFIC":
                // MarineTraffic returns data in "data" array
                if (response.containsKey("data")) {
                    Object dataObj = response.get("data");
                    if (dataObj instanceof List) {
                        vessels = (List<Map<String, Object>>) dataObj;
                    }
                }
                break;
            case "VESSELFINDER":
                // VesselFinder may return data directly or in "vessels" array
                if (response.containsKey("vessels")) {
                    Object vesselsObj = response.get("vessels");
                    if (vesselsObj instanceof List) {
                        vessels = (List<Map<String, Object>>) vesselsObj;
                    }
                } else if (response.containsKey("data")) {
                    Object dataObj = response.get("data");
                    if (dataObj instanceof List) {
                        vessels = (List<Map<String, Object>>) dataObj;
                    }
                }
                break;
            case "AISHUB":
                // AISHub returns data in "positions" array
                if (response.containsKey("positions")) {
                    Object positionsObj = response.get("positions");
                    if (positionsObj instanceof List) {
                        vessels = (List<Map<String, Object>>) positionsObj;
                    }
                }
                break;
            default:
                // Try common field names
                if (response.containsKey("data")) {
                    Object dataObj = response.get("data");
                    if (dataObj instanceof List) {
                        vessels = (List<Map<String, Object>>) dataObj;
                    }
                } else if (response.containsKey("vessels")) {
                    Object vesselsObj = response.get("vessels");
                    if (vesselsObj instanceof List) {
                        vessels = (List<Map<String, Object>>) vesselsObj;
                    }
                }
        }
        
        return vessels;
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
                .signalQuality(String.valueOf(vessel.getOrDefault("signal_quality", vessel.getOrDefault("signalQuality", "GOOD"))).trim())
                .dataSource(determineDataSource(vessel))
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
            
            // Ensure MMSI is valid before building
            String validMmsi = mmsi.trim();
            if (validMmsi.isEmpty() || "null".equalsIgnoreCase(validMmsi)) {
                log.warn("Skipping ship {}: Invalid MMSI '{}'", shipNames[shipIndex], mmsi);
                continue;
            }
            
            AisDataMessage message = AisDataMessage.builder()
                .mmsi(validMmsi)
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
            
            // Double-check MMSI is set after building - this should never happen if builder works correctly
            if (message.getMmsi() == null || message.getMmsi().trim().isEmpty()) {
                log.error("CRITICAL: MMSI is null after building message for ship: {}. This indicates a builder issue.", shipNames[shipIndex]);
                continue;
            }
            
            // Validate the entire message before adding
            if (message.getShipName() == null || message.getLatitude() == null || message.getLongitude() == null) {
                log.warn("Skipping incomplete message for ship: {}", shipNames[shipIndex]);
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
    
    /**
     * Determine data source from vessel data
     * Real AIS data providers indicate source (terrestrial, satellite, or both)
     */
    private String determineDataSource(Map<String, Object> vessel) {
        // Check for explicit data source field
        Object sourceObj = vessel.getOrDefault("data_source", vessel.getOrDefault("dataSource", vessel.getOrDefault("source", null)));
        if (sourceObj != null) {
            String source = String.valueOf(sourceObj).trim().toUpperCase();
            if (source.contains("SATELLITE") || source.contains("SAT")) {
                return "SATELLITE";
            } else if (source.contains("TERRESTRIAL") || source.contains("TERR")) {
                return "TERRESTRIAL";
            } else if (source.contains("BOTH") || source.contains("HYBRID")) {
                return "BOTH";
            }
        }
        
        // Check station range - satellite typically has longer range
        Object rangeObj = vessel.get("station_range");
        if (rangeObj != null) {
            try {
                double range = Double.parseDouble(String.valueOf(rangeObj));
                if (range > 50) {
                    return "SATELLITE"; // Long range typically indicates satellite
                } else {
                    return "TERRESTRIAL"; // Short range typically indicates terrestrial
                }
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        
        // Default based on provider
        String provider = aisProvider.toUpperCase();
        if (provider.contains("SATELLITE") || provider.contains("SAT")) {
            return "SATELLITE";
        } else {
            return "TERRESTRIAL"; // Most providers are terrestrial-based
        }
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

