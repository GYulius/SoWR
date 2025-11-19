package com.cruise.recommender.service;

import com.cruise.recommender.entity.Port;
import com.cruise.recommender.repository.PortRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Service for initializing data from JSON files
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataInitializationService implements CommandLineRunner {
    
    private final PortRepository portRepository;
    private final ObjectMapper objectMapper;
    
    @Override
    public void run(String... args) throws Exception {
        log.info("Initializing data from JSON files...");
        
        if (portRepository.count() == 0) {
            initializePortsFromJson();
            log.info("Ports data initialized successfully from ports_A.json");
        } else {
            log.info("Ports data already exists ({} ports), skipping initialization", portRepository.count());
        }
    }
    
    private void initializePortsFromJson() {
        try {
            // Try to read from file system first, then classpath
            File portsFile = new File("data/ports_A.json");
            InputStream inputStream;
            
            if (portsFile.exists()) {
                inputStream = new FileInputStream(portsFile);
                log.info("Loading ports from file: {}", portsFile.getAbsolutePath());
            } else {
                ClassPathResource resource = new ClassPathResource("data/ports_A.json");
                if (resource.exists()) {
                    inputStream = resource.getInputStream();
                    log.info("Loading ports from classpath: data/ports_A.json");
                } else {
                    log.warn("ports_A.json not found. Skipping port initialization.");
                    return;
                }
            }
            
            List<Map<String, Object>> portsData = objectMapper.readValue(
                inputStream,
                new TypeReference<List<Map<String, Object>>>() {}
            );
            
            log.info("Found {} ports in JSON file", portsData.size());
            
            int savedCount = 0;
            int skippedCount = 0;
            
            for (Map<String, Object> portData : portsData) {
                try {
                    Port port = mapJsonToPort(portData);
                    if (port != null) {
                        // Check if port already exists by port_code
                        if (portRepository.findByPortCode(port.getPortCode()).isEmpty()) {
                            portRepository.save(port);
                            savedCount++;
                        } else {
                            skippedCount++;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error processing port: {}", portData.get("name"), e);
                }
            }
            
            log.info("Successfully initialized {} ports, skipped {} duplicates", savedCount, skippedCount);
            
        } catch (Exception e) {
            log.error("Error initializing ports from JSON", e);
        }
    }
    
    private Port mapJsonToPort(Map<String, Object> portData) {
        try {
            Port.PortBuilder builder = Port.builder();
            
            builder.portCode(getStringValue(portData, "port_code", ""));
            builder.name(getStringValue(portData, "name", ""));
            builder.country(getStringValue(portData, "country", ""));
            builder.geo(getStringValue(portData, "geo", ""));
            builder.region(getStringValue(portData, "region", ""));
            builder.city(getStringValue(portData, "city", ""));
            
            Object latObj = portData.get("latitude");
            Object lngObj = portData.get("longitude");
            if (latObj != null && lngObj != null) {
                builder.latitude(convertToDouble(latObj));
                builder.longitude(convertToDouble(lngObj));
            }
            
            Object capacityObj = portData.get("capacity");
            if (capacityObj != null) {
                builder.berthsCapacity(convertToInteger(capacityObj));
            }
            
            // Convert facilities and amenities to JSON strings
            Object facilitiesObj = portData.get("facilities");
            if (facilitiesObj != null) {
                builder.facilities(objectMapper.writeValueAsString(facilitiesObj));
            }
            
            Object amenitiesObj = portData.get("amenities");
            if (amenitiesObj != null) {
                builder.amenities(objectMapper.writeValueAsString(amenitiesObj));
            }
            
            Object dockingFeesObj = portData.get("docking_fees");
            if (dockingFeesObj != null) {
                builder.dockingFees(new BigDecimal(convertToDouble(dockingFeesObj).toString()));
            }
            
            builder.currency(getStringValue(portData, "currency", "USD"));
            builder.timezone(getStringValue(portData, "timezone", ""));
            builder.language(getStringValue(portData, "language", ""));
            builder.tourism1(getStringValue(portData, "tourism1", ""));
            
            // Convert foodie_main and foodie_dessert to JSON strings
            Object foodieMainObj = portData.get("foodie_main");
            if (foodieMainObj != null) {
                builder.foodieMain(objectMapper.writeValueAsString(foodieMainObj));
            }
            
            Object foodieDessertObj = portData.get("foodie_dessert");
            if (foodieDessertObj != null) {
                builder.foodieDessert(objectMapper.writeValueAsString(foodieDessertObj));
            }
            
            return builder.build();
            
        } catch (Exception e) {
            log.error("Error mapping JSON to Port entity", e);
            return null;
        }
    }
    
    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    private Double convertToDouble(Object value) {
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
    
    private Integer convertToInteger(Object value) {
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
}
