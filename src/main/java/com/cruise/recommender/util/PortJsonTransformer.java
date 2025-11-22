package com.cruise.recommender.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.*;

/**
 * Utility class to transform all_RCI_ports.json to ports_A.json
 * following the schema defined in port-json-schema.txt
 */
@Slf4j
public class PortJsonTransformer {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public static void main(String[] args) {
        try {
            transformPortsJson();
            log.info("Successfully transformed ports JSON");
        } catch (Exception e) {
            log.error("Error transforming ports JSON", e);
        }
    }
    
    public static void transformPortsJson() throws Exception {
        // Read source file
        File sourceFile = new File("data/all_RCI_ports.json");
        if (!sourceFile.exists()) {
            ClassPathResource resource = new ClassPathResource("data/all_RCI_ports.json");
            if (resource.exists()) {
                // Copy to temp file first
                try (InputStream is = resource.getInputStream()) {
                    java.nio.file.Files.copy(is, sourceFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                throw new RuntimeException("Port data file not found");
            }
        }
        
        // Use a more lenient JSON parser
        objectMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS, true);
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        // Read file content
        List<Map<String, Object>> sourcePorts;
        try (java.io.FileInputStream fis = new java.io.FileInputStream(sourceFile)) {
            sourcePorts = objectMapper.readValue(
                fis, 
                new TypeReference<List<Map<String, Object>>>() {}
            );
        } catch (Exception e) {
            log.error("Error parsing JSON file: {}", e.getMessage());
            throw new RuntimeException("Failed to parse JSON file", e);
        }
        
        log.info("Loaded {} ports from source file", sourcePorts.size());
        
        ArrayNode transformedPorts = objectMapper.createArrayNode();
        int successCount = 0;
        int errorCount = 0;
        
        for (Map<String, Object> sourcePort : sourcePorts) {
            try {
                ObjectNode transformedPort = transformPort(sourcePort);
                if (transformedPort != null) {
                    transformedPorts.add(transformedPort);
                    successCount++;
                } else {
                    errorCount++;
                }
            } catch (Exception e) {
                errorCount++;
                log.warn("Error transforming port: {}", sourcePort.get("port"), e);
            }
        }
        
        log.info("Successfully transformed {} ports, {} errors", successCount, errorCount);
        
        log.info("Transformed {} ports", transformedPorts.size());
        
        // Write to output file
        File outputFile = new File("data/ports_A.json");
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(fos, transformedPorts);
        }
        
        log.info("Successfully wrote transformed ports to {}", outputFile.getAbsolutePath());
    }
    
    private static ObjectNode transformPort(Map<String, Object> sourcePort) {
        try {
            ObjectNode port = objectMapper.createObjectNode();
            
            // Basic fields from source
            port.put("geo", getStringValue(sourcePort, "geo", ""));
            port.put("port_code", getStringValue(sourcePort, "code", ""));
            port.put("name", getStringValue(sourcePort, "port", ""));
            port.put("country", getStringValue(sourcePort, "country", ""));
            port.put("tourism1", getStringValue(sourcePort, "tourism1", ""));
            
            // Parse coordinates
            String latStr = getStringValue(sourcePort, "latitude", "");
            String lngStr = getStringValue(sourcePort, "longitude", "");
            double latitude = parseCoordinate(latStr);
            double longitude = parseCoordinate(lngStr);
            port.put("latitude", latitude);
            port.put("longitude", longitude);
            
            // Extract region and city from port name if possible
            String portName = getStringValue(sourcePort, "port", "");
            String[] nameParts = extractRegionAndCity(portName, getStringValue(sourcePort, "country", ""));
            port.put("region", nameParts[0]);
            port.put("city", nameParts[1]);
            
            // Facilities object
            ObjectNode facilities = objectMapper.createObjectNode();
            facilities.put("terminals", estimateTerminals(latitude, longitude));
            facilities.put("docking_berths", estimateDockingBerths(latitude, longitude));
            facilities.put("tender_boats", estimateTenderBoats(latitude, longitude));
            facilities.put("customs", true);
            facilities.put("immigration", true);
            facilities.put("medical_facilities", true);
            facilities.put("shopping_center", estimateShoppingCenter(latitude, longitude));
            facilities.put("restaurants", estimateRestaurants(latitude, longitude));
            facilities.put("parking_spaces", estimateParkingSpaces(latitude, longitude));
            port.set("facilities", facilities);
            
            // Amenities array
            ArrayNode amenities = objectMapper.createArrayNode();
            amenities.add("free_wifi");
            amenities.add("currency_exchange");
            amenities.add("atm");
            amenities.add("luggage_storage");
            amenities.add("tourist_information");
            amenities.add("taxi_stand");
            amenities.add("bus_station");
            
            // Add region-specific amenities
            addRegionSpecificAmenities(amenities, latitude, longitude, getStringValue(sourcePort, "country", ""));
            port.set("amenities", amenities);
            
            // Foodie information from source
            if (sourcePort.containsKey("foodie_main")) {
                Object foodieMainObj = sourcePort.get("foodie_main");
                if (foodieMainObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> foodieMain = (Map<String, Object>) foodieMainObj;
                    ObjectNode foodieMainNode = objectMapper.createObjectNode();
                    foodieMainNode.put("name", getStringValue(foodieMain, "name", ""));
                    foodieMainNode.put("recipe", getStringValue(foodieMain, "recipe", ""));
                    port.set("foodie_main", foodieMainNode);
                }
            }
            
            if (sourcePort.containsKey("foodie_dessert")) {
                Object foodieDessertObj = sourcePort.get("foodie_dessert");
                if (foodieDessertObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> foodieDessert = (Map<String, Object>) foodieDessertObj;
                    ObjectNode foodieDessertNode = objectMapper.createObjectNode();
                    foodieDessertNode.put("name", getStringValue(foodieDessert, "name", ""));
                    foodieDessertNode.put("recipe", getStringValue(foodieDessert, "recipe", ""));
                    port.set("foodie_dessert", foodieDessertNode);
                }
            }
            
            // Estimated values
            port.put("capacity", estimateCapacity(latitude, longitude));
            port.put("docking_fees", estimateDockingFees(latitude, longitude));
            port.put("currency", estimateCurrency(getStringValue(sourcePort, "country", "")));
            port.put("timezone", estimateTimezone(latitude, longitude, getStringValue(sourcePort, "country", "")));
            port.put("language", estimateLanguage(getStringValue(sourcePort, "country", "")));
            
            return port;
        } catch (Exception e) {
            log.warn("Error transforming port: {}", sourcePort.get("port"), e);
            return null;
        }
    }
    
    private static double parseCoordinate(String coord) {
        if (coord == null || coord.isEmpty()) {
            return 0.0;
        }
        try {
            coord = coord.trim();
            
            // Try parsing as decimal first
            try {
                return Double.parseDouble(coord);
            } catch (NumberFormatException e) {
                // Not a decimal, try degrees format
            }
            
            // Format: "37°56'N" or "23°38'E"
            boolean isNegative = coord.contains("S") || coord.contains("W");
            
            String[] parts = coord.split("[°'\"\\s]+");
            if (parts.length >= 2) {
                double degrees = Double.parseDouble(parts[0]);
                double minutes = Double.parseDouble(parts[1].replaceAll("[^0-9.]", ""));
                double seconds = 0.0;
                
                if (parts.length >= 3) {
                    try {
                        seconds = Double.parseDouble(parts[2].replaceAll("[^0-9.]", ""));
                    } catch (NumberFormatException e) {
                        // Ignore
                    }
                }
                
                double decimal = degrees + (minutes / 60.0) + (seconds / 3600.0);
                return isNegative ? -decimal : decimal;
            }
        } catch (Exception e) {
            log.warn("Error parsing coordinate: {}", coord);
        }
        return 0.0;
    }
    
    private static String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    private static String[] extractRegionAndCity(String portName, String country) {
        String region = "";
        String city = portName;
        
        // Try to extract city from port name patterns like "City (Location)" or "City - Location"
        if (portName.contains("(")) {
            String[] parts = portName.split("\\(");
            city = parts[0].trim();
            region = parts.length > 1 ? parts[1].replace(")", "").trim() : "";
        } else if (portName.contains("-")) {
            String[] parts = portName.split("-");
            city = parts[0].trim();
            region = parts.length > 1 ? parts[1].trim() : "";
        }
        
        // Use country as region if region is empty
        if (region.isEmpty() && !country.isEmpty()) {
            region = country;
        }
        
        return new String[]{region, city};
    }
    
    private static int estimateTerminals(double lat, double lng) {
        // Estimate based on location - major ports have more terminals
        if (isMajorPort(lat, lng)) {
            return 4 + new Random((long)(lat * 1000 + lng)).nextInt(4); // 4-7
        }
        return 1 + new Random((long)(lat * 1000 + lng)).nextInt(3); // 1-3
    }
    
    private static int estimateDockingBerths(double lat, double lng) {
        if (isMajorPort(lat, lng)) {
            return 8 + new Random((long)(lat * 1000 + lng + 1)).nextInt(7); // 8-14
        }
        return 2 + new Random((long)(lat * 1000 + lng + 1)).nextInt(4); // 2-5
    }
    
    private static int estimateTenderBoats(double lat, double lng) {
        if (isMajorPort(lat, lng)) {
            return 6 + new Random((long)(lat * 1000 + lng + 2)).nextInt(6); // 6-11
        }
        return 3 + new Random((long)(lat * 1000 + lng + 2)).nextInt(5); // 3-7
    }
    
    private static boolean estimateShoppingCenter(double lat, double lng) {
        return isMajorPort(lat, lng);
    }
    
    private static int estimateRestaurants(double lat, double lng) {
        if (isMajorPort(lat, lng)) {
            return 10 + new Random((long)(lat * 1000 + lng + 3)).nextInt(15); // 10-24
        }
        return 3 + new Random((long)(lat * 1000 + lng + 3)).nextInt(7); // 3-9
    }
    
    private static int estimateParkingSpaces(double lat, double lng) {
        if (isMajorPort(lat, lng)) {
            return 1500 + new Random((long)(lat * 1000 + lng + 4)).nextInt(1500); // 1500-2999
        }
        return 200 + new Random((long)(lat * 1000 + lng + 4)).nextInt(800); // 200-999
    }
    
    private static int estimateCapacity(double lat, double lng) {
        if (isMajorPort(lat, lng)) {
            return 10000 + new Random((long)(lat * 1000 + lng + 5)).nextInt(10000); // 10000-19999
        }
        return 2000 + new Random((long)(lat * 1000 + lng + 5)).nextInt(8000); // 2000-9999
    }
    
    private static double estimateDockingFees(double lat, double lng) {
        if (isMajorPort(lat, lng)) {
            return 2000.0 + new Random((long)(lat * 1000 + lng + 6)).nextInt(1000); // 2000-2999
        }
        return 800.0 + new Random((long)(lat * 1000 + lng + 6)).nextInt(1200); // 800-1999
    }
    
    private static boolean isMajorPort(double lat, double lng) {
        // Check if coordinates are near major cruise ports
        // Barcelona: 41.3851, 2.1734
        // Venice: 45.4408, 12.3155
        // Miami: 25.7617, -80.1918
        // etc.
        return (Math.abs(lat - 41.3851) < 5 && Math.abs(lng - 2.1734) < 5) || // Barcelona area
               (Math.abs(lat - 45.4408) < 5 && Math.abs(lng - 12.3155) < 5) || // Venice area
               (Math.abs(lat - 25.7617) < 5 && Math.abs(lng + 80.1918) < 5) || // Miami area
               (Math.abs(lat - 37.9755) < 5 && Math.abs(lng - 23.7348) < 5) || // Athens area
               (Math.abs(lat - 41.0082) < 5 && Math.abs(lng - 28.9784) < 5); // Istanbul area
    }
    
    private static void addRegionSpecificAmenities(ArrayNode amenities, double lat, double lng, String country) {
        // Add metro access for major European cities
        if (lat > 35 && lat < 60 && lng > -10 && lng < 40) {
            amenities.add("metro_access");
        }
        
        // Add ferry terminal for island/coastal ports
        if (country.toLowerCase().contains("island") || 
            country.toLowerCase().contains("greece") ||
            country.toLowerCase().contains("italy")) {
            amenities.add("ferry_terminal");
        }
        
        // Add water taxi for Venice
        if (Math.abs(lat - 45.4408) < 1 && Math.abs(lng - 12.3155) < 1) {
            amenities.add("water_taxi");
            amenities.add("vaporetto_access");
            amenities.add("gondola_service");
        }
        
        // Add cable car for mountainous regions
        if (lat > 36 && lat < 43 && lng > 18 && lng < 28) {
            amenities.add("cable_car_access");
        }
        
        // Add tram for certain European cities
        if (lat > 40 && lat < 50 && lng > 0 && lng < 10) {
            amenities.add("tram_access");
        }
        
        // Add bike rental for bike-friendly cities
        if (lat > 45 && lat < 55 && lng > -5 && lng < 15) {
            amenities.add("bike_rental");
        }
    }
    
    private static String estimateCurrency(String country) {
        Map<String, String> currencyMap = new HashMap<>();
        currencyMap.put("Spain", "EUR");
        currencyMap.put("Italy", "EUR");
        currencyMap.put("France", "EUR");
        currencyMap.put("Greece", "EUR");
        currencyMap.put("Germany", "EUR");
        currencyMap.put("Netherlands", "EUR");
        currencyMap.put("Portugal", "EUR");
        currencyMap.put("Turkey", "TRY");
        currencyMap.put("Croatia", "HRK");
        currencyMap.put("United States", "USD");
        currencyMap.put("USA", "USD");
        currencyMap.put("United Kingdom", "GBP");
        currencyMap.put("UK", "GBP");
        currencyMap.put("Canada", "CAD");
        currencyMap.put("Australia", "AUD");
        currencyMap.put("New Zealand", "NZD");
        currencyMap.put("Japan", "JPY");
        currencyMap.put("China", "CNY");
        
        for (Map.Entry<String, String> entry : currencyMap.entrySet()) {
            if (country.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        return "USD"; // Default
    }
    
    private static String estimateTimezone(double lat, double lng, String country) {
        // Estimate timezone based on longitude and country
        if (country.contains("Spain") || country.contains("France") || country.contains("Italy") || 
            country.contains("Germany") || country.contains("Netherlands")) {
            return "Europe/" + getCapitalCity(country);
        }
        if (country.contains("Greece")) {
            return "Europe/Athens";
        }
        if (country.contains("Turkey")) {
            return "Europe/Istanbul";
        }
        if (country.contains("United Kingdom") || country.contains("UK")) {
            return "Europe/London";
        }
        if (country.contains("United States") || country.contains("USA")) {
            if (lng < -100) {
                return "America/Los_Angeles";
            } else if (lng < -85) {
                return "America/Chicago";
            } else {
                return "America/New_York";
            }
        }
        if (country.contains("Canada")) {
            return "America/Toronto";
        }
        if (country.contains("Australia")) {
            if (lng > 145) {
                return "Australia/Sydney";
            } else {
                return "Australia/Melbourne";
            }
        }
        if (country.contains("New Zealand")) {
            return "Pacific/Auckland";
        }
        if (country.contains("Japan")) {
            return "Asia/Tokyo";
        }
        if (country.contains("China")) {
            return "Asia/Shanghai";
        }
        
        // Default based on longitude
        int offset = (int)(lng / 15);
        return "UTC" + (offset >= 0 ? "+" : "") + offset;
    }
    
    private static String getCapitalCity(String country) {
        Map<String, String> capitals = new HashMap<>();
        capitals.put("Spain", "Madrid");
        capitals.put("France", "Paris");
        capitals.put("Italy", "Rome");
        capitals.put("Germany", "Berlin");
        capitals.put("Netherlands", "Amsterdam");
        capitals.put("Portugal", "Lisbon");
        
        for (Map.Entry<String, String> entry : capitals.entrySet()) {
            if (country.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        return "London"; // Default
    }
    
    private static String estimateLanguage(String country) {
        Map<String, String> languages = new HashMap<>();
        languages.put("Spain", "Spanish, English");
        languages.put("Italy", "Italian, English");
        languages.put("France", "French, English");
        languages.put("Greece", "Greek, English");
        languages.put("Germany", "German, English");
        languages.put("Netherlands", "Dutch, English");
        languages.put("Portugal", "Portuguese, English");
        languages.put("Turkey", "Turkish, English");
        languages.put("Croatia", "Croatian, English");
        languages.put("United States", "English");
        languages.put("USA", "English");
        languages.put("United Kingdom", "English");
        languages.put("UK", "English");
        languages.put("Canada", "English, French");
        languages.put("Australia", "English");
        languages.put("New Zealand", "English");
        languages.put("Japan", "Japanese, English");
        languages.put("China", "Chinese, English");
        
        for (Map.Entry<String, String> entry : languages.entrySet()) {
            if (country.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        return "English"; // Default
    }
}

