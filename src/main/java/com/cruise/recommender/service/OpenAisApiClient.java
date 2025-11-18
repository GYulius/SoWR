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
 * Client for fetching AIS data from Open-AIS PG_FeatureServ API
 * Open-AIS provides free AIS data from Norwegian coastguard's server
 * API Documentation: https://open-ais.org/Quick-Start/0/
 * 
 * Uses PG_FeatureServ API which is OGC and OpenAPI compliant
 * Collections available: latest_position, ship, traj, positions
 * Supports CQL (Common Query Language) filters for querying
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAisApiClient {
    
    private final RestTemplate restTemplate;
    
    @Value("${openais.api.base.url:http://localhost:9000}")
    private String apiBaseUrl;
    
    @Value("${openais.api.collection:latest_position}")
    private String defaultCollection;
    
    @Value("${openais.api.timeout:30000}")
    private int timeout;
    
    @Value("${openais.api.limit:1000}")
    private int defaultLimit;
    
    /**
     * Fetch latest AIS positions from Open-AIS API
     * Uses the 'latest_position' collection which returns the most recent position per MMSI
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
        return fetchAisData(minLat, maxLat, minLng, maxLng, null, null);
    }
    
    /**
     * Fetch AIS data with filters
     * @param minLat Minimum latitude
     * @param maxLat Maximum latitude
     * @param minLng Minimum longitude
     * @param maxLng Maximum longitude
     * @param mmsi Optional MMSI filter
     * @param timeWindow Optional time window filter (e.g., "bucket >= 2024-01-01")
     */
    public List<AisDataMessage> fetchAisData(Double minLat, Double maxLat, Double minLng, Double maxLng, 
                                             String mmsi, String timeWindow) {
        List<AisDataMessage> aisDataList = new ArrayList<>();
        
        try {
            String url = buildApiUrl(minLat, maxLat, minLng, maxLng, mmsi, timeWindow);
            log.debug("Fetching AIS data from Open-AIS API: {}", url);
            
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null && response.containsKey("features")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> features = (List<Map<String, Object>>) response.get("features");
                
                for (Map<String, Object> feature : features) {
                    AisDataMessage message = convertFeatureToMessage(feature);
                    if (message != null && message.getMmsi() != null && !message.getMmsi().trim().isEmpty()) {
                        aisDataList.add(message);
                    }
                }
            }
            
            log.info("Fetched {} AIS data records from Open-AIS", aisDataList.size());
            
        } catch (Exception e) {
            log.error("Error fetching AIS data from Open-AIS API: {}", e.getMessage(), e);
        }
        
        return aisDataList;
    }
    
    /**
     * Build API URL for PG_FeatureServ
     * PG_FeatureServ uses OGC API Features standard and supports CQL filters
     * Collections: latest_position, ship, traj, positions
     */
    private String buildApiUrl(Double minLat, Double maxLat, Double minLng, Double maxLng, 
                               String mmsi, String timeWindow) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(apiBaseUrl)
                .path("/collections/{collection}/items")
                .queryParam("limit", defaultLimit);
        
        // Build CQL filter for advanced filtering
        List<String> cqlFilters = new ArrayList<>();
        
        // Add bounding box filter (BBOX or CQL)
        if (minLat != null && maxLat != null && minLng != null && maxLng != null) {
            // Use BBOX parameter (more efficient than CQL for spatial queries)
            builder.queryParam("bbox", String.format("%f,%f,%f,%f", minLng, minLat, maxLng, maxLat));
        }
        
        // Add MMSI filter using CQL
        if (mmsi != null && !mmsi.trim().isEmpty()) {
            cqlFilters.add(String.format("mmsi = '%s'", mmsi.trim()));
        }
        
        // Add time window filter using CQL
        // Example: "bucket >= 2024-01-01" or "bucket < 2021-02-02"
        if (timeWindow != null && !timeWindow.trim().isEmpty()) {
            cqlFilters.add(timeWindow.trim());
        }
        
        // Combine CQL filters
        if (!cqlFilters.isEmpty()) {
            String cqlFilter = String.join(" AND ", cqlFilters);
            builder.queryParam("filter", cqlFilter);
            log.debug("Using CQL filter: {}", cqlFilter);
        }
        
        return builder.buildAndExpand(defaultCollection).toUriString();
    }
    
    /**
     * Convert GeoJSON feature to AisDataMessage
     * Open-AIS uses GeoJSON format with properties containing AIS data
     * Based on latest_position collection schema:
     * - MMSI, time_bucket, IMO, callsign, flag, name, type_and_cargo, type, sub_type
     * - draught, geom (position), COG, SOG, nav_status, nav_description
     */
    @SuppressWarnings("unchecked")
    private AisDataMessage convertFeatureToMessage(Map<String, Object> feature) {
        try {
            Map<String, Object> properties = (Map<String, Object>) feature.get("properties");
            Map<String, Object> geometry = (Map<String, Object>) feature.get("geometry");
            
            if (properties == null) {
                log.warn("Feature has no properties, skipping");
                return null;
            }
            
            // Extract MMSI - required field (indexed)
            Object mmsiObj = properties.get("mmsi");
            if (mmsiObj == null) {
                mmsiObj = properties.get("MMSI");
            }
            
            String mmsi = mmsiObj != null ? String.valueOf(mmsiObj).trim() : null;
            if (mmsi == null || mmsi.isEmpty() || "null".equalsIgnoreCase(mmsi)) {
                log.debug("Skipping feature: MMSI is missing");
                return null;
            }
            
            // Extract coordinates from geometry (Point geometry in 4326)
            Double latitude = null;
            Double longitude = null;
            if (geometry != null && "Point".equals(geometry.get("type"))) {
                List<Double> coordinates = (List<Double>) geometry.get("coordinates");
                if (coordinates != null && coordinates.size() >= 2) {
                    longitude = coordinates.get(0); // GeoJSON uses [lng, lat]
                    latitude = coordinates.get(1);
                }
            }
            
            // Extract timestamp from time_bucket (indexed)
            LocalDateTime timestamp = LocalDateTime.now();
            Object timeBucketObj = properties.get("time_bucket");
            if (timeBucketObj == null) {
                timeBucketObj = properties.get("bucket");
            }
            if (timeBucketObj != null) {
                try {
                    String timestampStr = String.valueOf(timeBucketObj);
                    // Parse timestamp - could be ISO format or custom format
                    if (timestampStr.contains("T")) {
                        timestamp = LocalDateTime.parse(timestampStr.substring(0, 19), 
                                DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    } else if (timestampStr.length() >= 19) {
                        timestamp = LocalDateTime.parse(timestampStr.substring(0, 19), 
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    }
                } catch (Exception e) {
                    log.debug("Could not parse time_bucket: {}", timeBucketObj);
                }
            }
            
            // Extract vessel properties from latest_position collection
            String shipName = getStringValue(properties, "name");
            String imo = getStringValue(properties, "imo");
            String callSign = getStringValue(properties, "callsign");
            String flag = getStringValue(properties, "flag");
            
            // Extract vessel type information
            String shipType = getStringValue(properties, "type");
            String subType = getStringValue(properties, "sub_type");
            String typeAndCargo = getStringValue(properties, "type_and_cargo");
            
            // Combine type information
            String fullShipType = shipType;
            if (subType != null && !subType.isEmpty()) {
                fullShipType = shipType + " - " + subType;
            }
            
            // Extract navigation and movement data
            Double speed = getDoubleValue(properties, "sog"); // Speed Over Ground (indexed)
            Double course = getDoubleValue(properties, "cog"); // Course Over Ground (indexed)
            Integer navStatus = getIntegerValue(properties, "nav_status");
            String navDescription = getStringValue(properties, "nav_description");
            Double draught = getDoubleValue(properties, "draught");
            
            // Use nav_status as heading if available, otherwise use course
            Integer heading = navStatus != null ? navStatus : (course != null ? course.intValue() : null);
            
            // Determine data source (Open-AIS uses Norwegian coastguard data - terrestrial)
            String dataSource = "TERRESTRIAL";
            
            return AisDataMessage.builder()
                    .mmsi(mmsi)
                    .shipName(shipName != null ? shipName : "Unknown")
                    .latitude(latitude)
                    .longitude(longitude)
                    .timestamp(timestamp)
                    .speed(speed)
                    .course(course)
                    .heading(heading)
                    .shipType(fullShipType != null ? fullShipType : shipType)
                    .destination(null) // Not available in latest_position collection
                    .eta(null) // Not available in latest_position collection
                    .imo(imo)
                    .callSign(callSign)
                    .stationRange(null) // Not available in latest_position collection
                    .signalQuality("GOOD") // Assume good quality from coastguard
                    .dataSource(dataSource)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error converting Open-AIS feature to AIS message", e);
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
     * Get available collections from Open-AIS API
     * Available collections: latest_position, ship, traj, positions, maritime_boundaries, heatmap_track_count
     */
    public List<String> getAvailableCollections() {
        try {
            String url = apiBaseUrl + "/collections";
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null && response.containsKey("collections")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> collections = (List<Map<String, Object>>) response.get("collections");
                List<String> collectionNames = new ArrayList<>();
                for (Map<String, Object> collection : collections) {
                    String name = (String) collection.get("id");
                    if (name != null) {
                        collectionNames.add(name);
                    }
                }
                return collectionNames;
            }
        } catch (Exception e) {
            log.error("Error fetching collections from Open-AIS API", e);
        }
        return new ArrayList<>();
    }
    
    /**
     * Fetch latest positions for a specific MMSI
     */
    public AisDataMessage fetchVesselByMmsi(String mmsi) {
        List<AisDataMessage> results = fetchAisData(null, null, null, null, mmsi, null);
        return results.isEmpty() ? null : results.get(0);
    }
    
    /**
     * Check API health using health_check function
     */
    public Map<String, Object> checkHealth() {
        try {
            String url = apiBaseUrl + "/functions/health_check/items";
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            return response;
        } catch (Exception e) {
            log.error("Error checking Open-AIS API health", e);
            return null;
        }
    }
}

