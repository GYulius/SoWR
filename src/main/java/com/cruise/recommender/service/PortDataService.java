package com.cruise.recommender.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service to load and manage port data from all_RCI_ports.json
 */
@Service
@Slf4j
public class PortDataService {
    
    private List<PortData> allPorts = new ArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Port code mappings for common ports
    private static final Map<String, String> PORT_CODE_MAPPINGS = Map.of(
        "BCN", "BCN",      // Barcelona
        "ROM", "ITROM",    // Rome (might need to search)
        "ATH", "GRATH",    // Athens (Piraeus)
        "IST", "TRIST",    // Istanbul (might need to search)
        "NCE", "FRNCE",    // Nice (might need to search)
        "VEN", "ITVEN"     // Venice (might need to search)
    );
    
    @PostConstruct
    public void loadPorts() {
        try {
            // Try classpath first, then file system
            ClassPathResource resource = new ClassPathResource("data/all_RCI_ports.json");
            InputStream inputStream;
            if (resource.exists()) {
                inputStream = resource.getInputStream();
            } else {
                // Try file system path
                java.io.File file = new java.io.File("data/all_RCI_ports.json");
                if (file.exists()) {
                    inputStream = new java.io.FileInputStream(file);
                } else {
                    log.warn("Port data file not found. Port map features will be limited.");
                    allPorts = new ArrayList<>();
                    return;
                }
            }
            allPorts = objectMapper.readValue(inputStream, new TypeReference<List<PortData>>() {});
            log.info("Loaded {} ports from all_RCI_ports.json", allPorts.size());
        } catch (Exception e) {
            log.error("Error loading ports from JSON file", e);
            allPorts = new ArrayList<>();
        }
    }
    
    /**
     * Get all ports
     */
    public List<PortData> getAllPorts() {
        return allPorts;
    }
    
    /**
     * Get featured Mediterranean ports by code
     * Includes: Barcelona (BCN), Athens/Piraeus (GRATH), Venice (ITVEN), 
     * Istanbul (TRIST), Nice (FRNCE), Rome (ITROM)
     */
    public List<PortData> getFeaturedPorts() {
        // Search for ports by code or name patterns
        List<PortData> featured = new ArrayList<>();
        
        // Barcelona - 41.3851° N, 2.1734° E
        PortData barcelona = findByCode("BCN").orElse(null);
        if (barcelona == null) {
            barcelona = allPorts.stream()
                .filter(p -> p.getPort() != null && p.getPort().equalsIgnoreCase("Barcelona"))
                .findFirst().orElse(null);
        }
        if (barcelona != null) {
            if (barcelona.getLatitude() == null || barcelona.getLatitude().isEmpty()) {
                barcelona.setLatitude("41°23'N");
                barcelona.setLongitude("2°10'E");
            }
            if (barcelona.getCode() == null || barcelona.getCode().isEmpty()) {
                barcelona.setCode("BCN");
            }
            featured.add(barcelona);
        }
        
        // Athens/Piraeus - 37.9440° N, 23.6470° E
        PortData athens = findByCode("GRATH").orElse(null);
        if (athens == null) {
            athens = allPorts.stream()
                .filter(p -> p.getPort() != null && p.getPort().toLowerCase().contains("athens"))
                .findFirst().orElse(null);
        }
        if (athens != null) {
            if (athens.getLatitude() == null || athens.getLatitude().isEmpty()) {
                athens.setLatitude("37°56'N");
                athens.setLongitude("23°38'E");
            }
            if (athens.getCode() == null || athens.getCode().isEmpty()) {
                athens.setCode("ATH");
            }
            featured.add(athens);
        }
        
        // Venice - 45.4371° N, 12.3326° E
        PortData venice = allPorts.stream()
            .filter(p -> p.getPort() != null && p.getPort().toLowerCase().contains("venice"))
            .findFirst().orElse(null);
        if (venice != null) {
            if (venice.getLatitude() == null || venice.getLatitude().isEmpty()) {
                venice.setLatitude("45°26'N");
                venice.setLongitude("12°19'E");
            }
            if (venice.getCode() == null || venice.getCode().isEmpty()) {
                venice.setCode("VEN");
            }
            featured.add(venice);
        }
        
        // Istanbul - 41.0082° N, 28.9784° E
        PortData istanbul = allPorts.stream()
            .filter(p -> p.getPort() != null && p.getPort().toLowerCase().contains("istanbul"))
            .findFirst().orElse(null);
        if (istanbul != null) {
            if (istanbul.getLatitude() == null || istanbul.getLatitude().isEmpty()) {
                istanbul.setLatitude("41°00'N");
                istanbul.setLongitude("28°58'E");
            }
            if (istanbul.getCode() == null || istanbul.getCode().isEmpty()) {
                istanbul.setCode("IST");
            }
            featured.add(istanbul);
        }
        
        // Nice - 43.7102° N, 7.2620° E
        PortData nice = allPorts.stream()
            .filter(p -> p.getPort() != null && p.getPort().toLowerCase().contains("nice"))
            .findFirst().orElse(null);
        if (nice != null) {
            if (nice.getLatitude() == null || nice.getLatitude().isEmpty()) {
                nice.setLatitude("43°42'N");
                nice.setLongitude("7°15'E");
            }
            if (nice.getCode() == null || nice.getCode().isEmpty()) {
                nice.setCode("NCE");
            }
            featured.add(nice);
        }
        
        // Rome (Civitavecchia) - 42.0931° N, 11.7967° E
        PortData rome = allPorts.stream()
            .filter(p -> p.getPort() != null && p.getPort().toLowerCase().contains("rome"))
            .findFirst().orElse(null);
        if (rome != null) {
            if (rome.getLatitude() == null || rome.getLatitude().isEmpty()) {
                rome.setLatitude("42°05'N");
                rome.setLongitude("11°47'E");
            }
            if (rome.getCode() == null || rome.getCode().isEmpty()) {
                rome.setCode("ROM");
            }
            featured.add(rome);
        }
        
        log.info("Found {} featured ports for dropdown", featured.size());
        return featured;
    }
    
    /**
     * Find port by code
     */
    public Optional<PortData> findByCode(String code) {
        return allPorts.stream()
                .filter(port -> code.equalsIgnoreCase(port.getCode()))
                .findFirst();
    }
    
    /**
     * Convert latitude/longitude from degrees format to decimal
     * Supports formats like: "37°56'N", "23°38'E", "41.3851", "-2.1734"
     */
    public static double parseCoordinate(String coord) {
        if (coord == null || coord.isEmpty()) {
            return 0.0;
        }
        try {
            coord = coord.trim();
            
            // Try parsing as decimal first (e.g., "41.3851" or "-2.1734")
            try {
                return Double.parseDouble(coord);
            } catch (NumberFormatException e) {
                // Not a decimal, try degrees format
            }
            
            // Format: "37°56'N" or "23°38'E" or "37°56'30\"N"
            boolean isNegative = coord.contains("S") || coord.contains("W");
            
            // Try splitting by degree symbol
            String[] parts = coord.split("[°'\"\\s]+");
            if (parts.length >= 2) {
                double degrees = Double.parseDouble(parts[0]);
                double minutes = Double.parseDouble(parts[1].replaceAll("[^0-9.]", ""));
                double seconds = 0.0;
                
                // Check for seconds if present
                if (parts.length >= 3) {
                    try {
                        seconds = Double.parseDouble(parts[2].replaceAll("[^0-9.]", ""));
                    } catch (NumberFormatException e) {
                        // Ignore seconds parsing errors
                    }
                }
                
                double decimal = degrees + (minutes / 60.0) + (seconds / 3600.0);
                return isNegative ? -decimal : decimal;
            }
            
            // Try alternative format: "N 37° 56'" or "E 23° 38'"
            if (coord.matches("^[NSWE]\\s*\\d+")) {
                String[] altParts = coord.split("[°'\"\\s]+");
                if (altParts.length >= 2) {
                    boolean neg = altParts[0].equals("S") || altParts[0].equals("W");
                    double deg = Double.parseDouble(altParts[1]);
                    double min = altParts.length > 2 ? Double.parseDouble(altParts[2].replaceAll("[^0-9.]", "")) : 0.0;
                    double dec = deg + (min / 60.0);
                    return neg ? -dec : dec;
                }
            }
        } catch (Exception e) {
            log.warn("Error parsing coordinate: {} - {}", coord, e.getMessage());
        }
        return 0.0;
    }
    
    @Data
    public static class PortData {
        private String geo;
        private String country;
        private String port;
        private String code;
        private String latitude;
        private String longitude;
        private String tourism1;
        private FoodieMain foodie_main;
        private FoodieDessert foodie_dessert;
        
        public double getLatitudeDecimal() {
            return parseCoordinate(latitude);
        }
        
        public double getLongitudeDecimal() {
            return parseCoordinate(longitude);
        }
    }
    
    @Data
    public static class FoodieMain {
        private String name;
        private String recipe;
    }
    
    @Data
    public static class FoodieDessert {
        private String name;
        private String recipe;
    }
}

