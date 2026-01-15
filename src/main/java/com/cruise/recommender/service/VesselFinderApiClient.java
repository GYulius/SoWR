package com.cruise.recommender.service;

import com.cruise.recommender.service.AisDataService.AisDataMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Client for fetching AIS data from VesselFinder API
 * VesselFinder provides commercial AIS data with free account support
 * Free account includes DEFAULT FLEET with 10 ships
 * 
 * API Documentation: https://www.vesselfinder.com/
 * API Endpoint: https://www.vesselfinder.com/api
 * 
 * Free Account Setup:
 * 1. Sign up at https://www.vesselfinder.com/
 * 2. Get API key from account dashboard
 * 3. Add ships to DEFAULT FLEET (up to 10 ships)
 * 4. Use fleet=DEFAULT parameter in API calls
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VesselFinderApiClient {
    
    private final RestTemplate restTemplate;
    
    @Value("${ais.data.source.api.url:https://www.vesselfinder.com/api}")
    private String apiBaseUrl;
    
    @Value("${ais.data.source.api.key:}")
    private String apiKey;
    
    @Value("${ais.data.source.api.fleet:DEFAULT}")
    private String fleet;
    
    @Value("${ais.data.source.api.timeout:30000}")
    private int timeout;
    
    /**
     * Fetch latest AIS positions from VesselFinder API
     * Uses the DEFAULT FLEET for free accounts
     */
    public List<AisDataMessage> fetchAisData() {
        return fetchAisData(null, null, null, null);
    }
    
    /**
     * Fetch AIS data within a bounding box
     * @param minLat Minimum latitude
     * @param maxLat Maximum latitude
     * @param minLng Minimum longitude
     * @param maxLng Maximum longitude
     */
    public List<AisDataMessage> fetchAisData(Double minLat, Double maxLat, Double minLng, Double maxLng) {
        return fetchAisData(minLat, maxLat, minLng, maxLng, null);
    }
    
    /**
     * Fetch AIS data with filters
     * @param minLat Minimum latitude
     * @param maxLat Maximum latitude
     * @param minLng Minimum longitude
     * @param maxLng Maximum longitude
     * @param mmsi Optional MMSI filter
     */
    public List<AisDataMessage> fetchAisData(Double minLat, Double maxLat, Double minLng, Double maxLng, 
                                             String mmsi) {
        List<AisDataMessage> aisDataList = new ArrayList<>();
        
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("VesselFinder API key is not configured. Cannot fetch AIS data.");
            return aisDataList;
        }
        
        try {
            String url = buildApiUrl(minLat, maxLat, minLng, maxLng, mmsi);
            log.debug("Fetching AIS data from VesselFinder API: {}", url.replace(apiKey, "***"));
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null) {
                List<Map<String, Object>> vessels = parseApiResponse(response);
                
                for (Map<String, Object> vessel : vessels) {
                    AisDataMessage message = convertVesselToMessage(vessel);
                    if (message != null && message.getMmsi() != null && !message.getMmsi().trim().isEmpty()) {
                        aisDataList.add(message);
                    }
                }
            }
            
            log.info("Fetched {} AIS data records from VesselFinder", aisDataList.size());
            
        } catch (Exception e) {
            log.error("Error fetching AIS data from VesselFinder API: {}", e.getMessage(), e);
        }
        
        return aisDataList;
    }
    
    /**
     * Build API URL for VesselFinder
     * Format: https://www.vesselfinder.com/api?api_key=KEY&fleet=DEFAULT
     */
    private String buildApiUrl(Double minLat, Double maxLat, Double minLng, Double maxLng, String mmsi) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(apiBaseUrl)
                .queryParam("api_key", apiKey)
                .queryParam("fleet", fleet);
        
        // Add bounding box if provided
        if (minLat != null && maxLat != null && minLng != null && maxLng != null) {
            // VesselFinder may support bbox parameter
            builder.queryParam("bbox", String.format("%f,%f,%f,%f", minLng, minLat, maxLng, maxLat));
        }
        
        // Add MMSI filter if provided
        if (mmsi != null && !mmsi.trim().isEmpty()) {
            builder.queryParam("mmsi", mmsi.trim());
        }
        
        return builder.toUriString();
    }
    
    /**
     * Parse VesselFinder API response
     * VesselFinder may return data in "vessels" array or directly as array
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseApiResponse(Map<String, Object> response) {
        List<Map<String, Object>> vessels = new ArrayList<>();
        
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
        } else if (response instanceof Map && !response.isEmpty()) {
            // If response is a single vessel object, wrap it in a list
            // Check if it looks like vessel data (has mmsi or MMSI field)
            if (response.containsKey("mmsi") || response.containsKey("MMSI")) {
                vessels.add(response);
            }
        }
        
        return vessels;
    }
    
    /**
     * Convert VesselFinder vessel data to AisDataMessage
     */
    @SuppressWarnings("unchecked")
    private AisDataMessage convertVesselToMessage(Map<String, Object> vessel) {
        try {
            // Extract MMSI - required field
            String mmsi = getStringValue(vessel, "mmsi", "MMSI", "vessel_id");
            if (mmsi == null || mmsi.isEmpty() || "null".equalsIgnoreCase(mmsi)) {
                log.debug("Skipping vessel: MMSI is missing");
                return null;
            }
            
            // Extract coordinates
            Double latitude = getDoubleValue(vessel, "latitude", "lat", "y");
            Double longitude = getDoubleValue(vessel, "longitude", "lng", "lon", "x");
            
            // Extract timestamp
            LocalDateTime timestamp = LocalDateTime.now();
            Object timestampObj = vessel.get("timestamp");
            if (timestampObj == null) {
                timestampObj = vessel.get("time");
            }
            if (timestampObj != null) {
                try {
                    String timestampStr = String.valueOf(timestampObj);
                    if (timestampStr.contains("T")) {
                        timestamp = LocalDateTime.parse(timestampStr.substring(0, 19), 
                                DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    } else if (timestampStr.length() >= 19) {
                        timestamp = LocalDateTime.parse(timestampStr.substring(0, 19), 
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    }
                } catch (Exception e) {
                    log.debug("Could not parse timestamp: {}", timestampObj);
                }
            }
            
            // Extract vessel properties
            String shipName = getStringValue(vessel, "ship_name", "name", "vessel_name");
            String imo = getStringValue(vessel, "imo", "IMO");
            String callSign = getStringValue(vessel, "callsign", "call_sign", "callSign");
            String flag = getStringValue(vessel, "flag", "country");
            
            // Extract vessel type
            String shipType = getStringValue(vessel, "ship_type", "type", "vessel_type");
            
            // Extract navigation data
            Double speed = getDoubleValue(vessel, "speed", "sog", "speed_over_ground");
            Double course = getDoubleValue(vessel, "course", "cog", "course_over_ground");
            Integer heading = getIntegerValue(vessel, "heading", "hdg");
            
            // Extract destination and ETA
            String destination = getStringValue(vessel, "destination", "dest");
            String eta = getStringValue(vessel, "eta", "eta_time");
            
            // Determine data source (VesselFinder provides both terrestrial and satellite)
            String dataSource = getStringValue(vessel, "data_source", "source");
            if (dataSource == null || dataSource.isEmpty()) {
                dataSource = "BOTH"; // VesselFinder typically provides both
            }
            
            return AisDataMessage.builder()
                    .mmsi(mmsi)
                    .shipName(shipName != null ? shipName : "Unknown")
                    .latitude(latitude)
                    .longitude(longitude)
                    .timestamp(timestamp)
                    .speed(speed)
                    .course(course)
                    .heading(heading)
                    .shipType(shipType)
                    .destination(destination)
                    .eta(eta)
                    .imo(imo)
                    .callSign(callSign)
                    .stationRange(null) // Not typically provided by VesselFinder
                    .signalQuality("GOOD") // Assume good quality from commercial provider
                    .dataSource(dataSource)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error converting VesselFinder vessel to AIS message", e);
            return null;
        }
    }
    
    private String getStringValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                String str = String.valueOf(value).trim();
                if (!str.isEmpty() && !"null".equalsIgnoreCase(str)) {
                    return str;
                }
            }
        }
        return null;
    }
    
    private Double getDoubleValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value == null) continue;
            
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException e) {
                // Try next key
            }
        }
        return null;
    }
    
    private Integer getIntegerValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value == null) continue;
            
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException e) {
                // Try next key
            }
        }
        return null;
    }
    
    /**
     * Fetch latest position for a specific MMSI
     */
    public AisDataMessage fetchVesselByMmsi(String mmsi) {
        List<AisDataMessage> results = fetchAisData(null, null, null, null, mmsi);
        return results.isEmpty() ? null : results.get(0);
    }
    
    /**
     * Check API health
     */
    public boolean checkHealth() {
        try {
            String url = apiBaseUrl + "?api_key=" + apiKey + "&fleet=" + fleet;
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            return response != null;
        } catch (Exception e) {
            log.error("Error checking VesselFinder API health", e);
            return false;
        }
    }
}
