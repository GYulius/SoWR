package com.cruise.recommender.service;

import com.cruise.recommender.entity.Port;
import com.cruise.recommender.repository.PortRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for syncing RDF data from Fuseki cruise_kg dataset to MySQL ports table
 * Populates JSON columns: activities, excursions, general_interests, restaurants, meal_venues, culinary_ingredients
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortRdfSyncService {
    
    private final PortRepository portRepository;
    private final PortRdfService portRdfService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${knowledge.graph.endpoint:http://localhost:3030/cruise_kg/sparql}")
    private String sparqlEndpoint;
    
    @Value("${knowledge.graph.username:}")
    private String fusekiUsername;
    
    @Value("${knowledge.graph.password:}")
    private String fusekiPassword;
    
    /**
     * Sync RDF data for all ports to MySQL database
     * @return Map with sync statistics
     */
    @Transactional
    public Map<String, Object> syncAllPortsFromRdf() {
        log.info("Starting RDF to MySQL sync for all ports");
        
        Map<String, Object> stats = new HashMap<>();
        int successCount = 0;
        int errorCount = 0;
        int skippedCount = 0;
        
        try {
            // Get all ports from database
            List<Port> ports = portRepository.findAll();
            log.info("Found {} ports in database to sync", ports.size());
            
            for (Port port : ports) {
                try {
                    boolean updated = syncPortFromRdf(port.getPortCode());
                    if (updated) {
                        successCount++;
                    } else {
                        skippedCount++;
                    }
                } catch (Exception e) {
                    log.error("Error syncing port {}: {}", port.getPortCode(), e.getMessage());
                    errorCount++;
                }
            }
            
            stats.put("totalPorts", ports.size());
            stats.put("successCount", successCount);
            stats.put("errorCount", errorCount);
            stats.put("skippedCount", skippedCount);
            
            log.info("RDF sync completed: {} successful, {} errors, {} skipped", 
                    successCount, errorCount, skippedCount);
            
        } catch (Exception e) {
            log.error("Error during RDF sync", e);
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }
    
    /**
     * Sync RDF data for a single port to MySQL database
     * @param portCode Port code to sync
     * @return true if port was updated, false if not found or no data
     */
    @Transactional
    public boolean syncPortFromRdf(String portCode) {
        log.debug("Syncing port {} from RDF", portCode);
        
        Optional<Port> portOpt = portRepository.findByPortCode(portCode);
        if (portOpt.isEmpty()) {
            log.warn("Port {} not found in database", portCode);
            return false;
        }
        
        Port port = portOpt.get();
        
        // Query RDF for all features of this port
        Map<String, List<String>> features = queryPortFeaturesFromRdf(portCode);
        
        if (features.isEmpty()) {
            log.debug("No RDF data found for port {}", portCode);
            return false;
        }
        
        boolean updated = false;
        
        // Update activities
        if (features.containsKey("activity") && !features.get("activity").isEmpty()) {
            String activitiesJson = convertToJsonArray(features.get("activity"));
            port.setActivityKeywords(activitiesJson);
            updated = true;
            log.debug("Updated activities for port {}: {}", portCode, features.get("activity").size());
        }
        
        // Update excursions
        if (features.containsKey("excursion") && !features.get("excursion").isEmpty()) {
            String excursionsJson = convertToJsonArray(features.get("excursion"));
            port.setExcursions(excursionsJson);
            updated = true;
            log.debug("Updated excursions for port {}: {}", portCode, features.get("excursion").size());
        }
        
        // Update general_interests
        if (features.containsKey("generalInterest") && !features.get("generalInterest").isEmpty()) {
            String generalInterestsJson = convertToJsonArray(features.get("generalInterest"));
            port.setGeneralInterests(generalInterestsJson);
            updated = true;
            log.debug("Updated general_interests for port {}: {}", portCode, features.get("generalInterest").size());
        }
        
        // Update restaurants
        if (features.containsKey("restaurantInfo") && !features.get("restaurantInfo").isEmpty()) {
            String restaurantsJson = convertToJsonArray(features.get("restaurantInfo"));
            port.setRestaurantKeywords(restaurantsJson);
            updated = true;
            log.debug("Updated restaurants for port {}: {}", portCode, features.get("restaurantInfo").size());
        }
        
        // Update meal_venues
        if (features.containsKey("mealVenueInfo") && !features.get("mealVenueInfo").isEmpty()) {
            String mealVenuesJson = convertToJsonArray(features.get("mealVenueInfo"));
            port.setMealVenues(mealVenuesJson);
            updated = true;
            log.debug("Updated meal_venues for port {}: {}", portCode, features.get("mealVenueInfo").size());
        }
        
        // Update culinary_ingredients
        if (features.containsKey("culinaryIngredient") && !features.get("culinaryIngredient").isEmpty()) {
            String culinaryIngredientsJson = convertToJsonArray(features.get("culinaryIngredient"));
            port.setCulinaryIngredients(culinaryIngredientsJson);
            updated = true;
            log.debug("Updated culinary_ingredients for port {}: {}", portCode, features.get("culinaryIngredient").size());
        }
        
        if (updated) {
            portRepository.save(port);
            log.info("Successfully synced RDF data for port {}", portCode);
        }
        
        return updated;
    }
    
    /**
     * Query RDF dataset for all features of a port
     * @param portCode Port code to query
     * @return Map of property types to feature lists
     */
    private Map<String, List<String>> queryPortFeaturesFromRdf(String portCode) {
        Map<String, List<String>> features = new HashMap<>();
        
        // Try to find full UNLOCODE from database first
        String fullPortCode = portCode.toUpperCase();
        try {
            Optional<Port> portOpt = portRepository.findByPortCode(portCode);
            if (portOpt.isPresent() && portOpt.get().getPortCode() != null) {
                fullPortCode = portOpt.get().getPortCode().toUpperCase();
            }
        } catch (Exception e) {
            log.debug("Could not lookup port code from database, using provided code: {}", portCode);
        }
        
        // Build query to get all features for this port
        String query = String.format("""
            PREFIX ex: <http://example.org/port-property/>
            PREFIX schema: <http://schema.org/>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            
            SELECT DISTINCT ?property ?feature WHERE {
                ?port rdf:type schema:Marina .
                ?port ex:code ?code .
                FILTER (?code = "%s" || CONTAINS(?code, "%s"))
                {
                    { ?port ex:activity ?feature . BIND("activity" AS ?property) }
                    UNION
                    { ?port ex:excursion ?feature . BIND("excursion" AS ?property) }
                    UNION
                    { ?port ex:generalInterest ?feature . BIND("generalInterest" AS ?property) }
                    UNION
                    { ?port ex:restaurantInfo ?feature . BIND("restaurantInfo" AS ?property) }
                    UNION
                    { ?port ex:mealVenueInfo ?feature . BIND("mealVenueInfo" AS ?property) }
                    UNION
                    { ?port ex:culinaryIngredient ?feature . BIND("culinaryIngredient" AS ?property) }
                }
            }
            ORDER BY ?property ?feature
            """, fullPortCode, portCode.toUpperCase());
        
        try {
            // Use PortRdfService's public queryPorts method
            List<QuerySolution> results = portRdfService.queryPorts(query);
            
            for (QuerySolution solution : results) {
                if (solution.get("property") != null && solution.get("feature") != null) {
                    String property = solution.get("property").toString();
                    String feature = solution.get("feature").toString();
                    
                    features.computeIfAbsent(property, k -> new ArrayList<>()).add(feature);
                }
            }
            
            log.debug("Found {} feature types with {} total features for port {}", 
                    features.size(), 
                    features.values().stream().mapToInt(List::size).sum(),
                    portCode);
            
        } catch (Exception e) {
            log.error("Error querying RDF for port {}: {}", portCode, e.getMessage());
        }
        
        return features;
    }
    
    /**
     * Convert list of strings to JSON array string
     */
    private String convertToJsonArray(List<String> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            log.error("Error converting list to JSON: {}", e.getMessage());
            // Fallback: simple JSON array
            return "[" + items.stream()
                    .map(item -> "\"" + item.replace("\"", "\\\"") + "\"")
                    .collect(Collectors.joining(", ")) + "]";
        }
    }
}
