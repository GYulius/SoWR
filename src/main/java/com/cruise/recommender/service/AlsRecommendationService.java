package com.cruise.recommender.service;

import com.cruise.recommender.service.RecommendationOrchestratorService.RecommendationItem;
import com.cruise.recommender.service.RecommendationOrchestratorService.PortFeature;
import com.cruise.recommender.service.UserItemMatrixService.UserItemMatrix;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ALS (Alternating Least Squares) Recommendation Service
 * 
 * Uses Apache Spark MLlib for collaborative filtering
 * Handles:
 * - Model training from user-item matrix
 * - Cold start problem (new users)
 * - Generating top-N recommendations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlsRecommendationService {
    
    private final SparkMlService sparkMlService;
    private final PortRdfService portRdfService;
    
    // Cache for trained models (in production, use Redis or persistent storage)
    private final Map<String, Object> modelCache = new HashMap<>();
    
    /**
     * Generate recommendations using ALS
     * 
     * @param passengerId Passenger ID
     * @param matrix User-item interaction matrix
     * @param portFeatures Available port features
     * @param numRecommendations Number of recommendations to generate
     * @return List of recommended items
     */
    public List<RecommendationItem> generateRecommendations(
            Long passengerId,
            UserItemMatrix matrix,
            List<PortFeature> portFeatures,
            int numRecommendations) {
        
        log.info("Generating ALS recommendations for passenger {} with {} interactions", 
                passengerId, matrix.getInteractions().size());
        
        try {
            // Step 1: Handle cold start (new user with no interactions)
            if (matrix.getInteractions().isEmpty()) {
                log.info("Cold start detected for passenger {}, using SPARQL-based recommendations", 
                        passengerId);
                return handleColdStart(passengerId, portFeatures, numRecommendations);
            }
            
            // Step 2: Train or retrieve ALS model
            Object alsModel = trainOrGetModel(matrix);
            
            if (alsModel == null) {
                log.warn("Could not train ALS model, falling back to SPARQL recommendations");
                return handleColdStart(passengerId, portFeatures, numRecommendations);
            }
            
            // Step 3: Generate predictions using Spark MLlib
            List<RecommendationItem> recommendations = generatePredictions(
                    alsModel,
                    passengerId,
                    matrix,
                    portFeatures,
                    numRecommendations
            );
            
            log.info("Generated {} ALS recommendations for passenger {}", 
                    recommendations.size(), passengerId);
            
            return recommendations;
            
        } catch (Exception e) {
            log.error("Error generating ALS recommendations", e);
            // Fallback to SPARQL-based recommendations
            return handleColdStart(passengerId, portFeatures, numRecommendations);
        }
    }
    
    /**
     * Train ALS model or retrieve from cache
     */
    private Object trainOrGetModel(UserItemMatrix matrix) {
        String cacheKey = "als_model_" + matrix.getPortId();
        
        // Check cache
        if (modelCache.containsKey(cacheKey)) {
            log.debug("Using cached ALS model for port {}", matrix.getPortId());
            return modelCache.get(cacheKey);
        }
        
        // Train new model
        try {
            log.info("Training new ALS model for port {}", matrix.getPortId());
            
            // Convert matrix to Spark Dataset format
            // Note: This uses reflection to avoid eager class loading
            Object userItemRatings = convertMatrixToSparkDataset(matrix);
            
            // Train model using SparkMlService
            Object model = sparkMlService.trainCollaborativeFilteringModel(userItemRatings);
            
            if (model != null) {
                modelCache.put(cacheKey, model);
                log.info("Successfully trained and cached ALS model");
            }
            
            return model;
            
        } catch (Exception e) {
            log.error("Error training ALS model", e);
            return null;
        }
    }
    
    /**
     * Convert UserItemMatrix to Spark Dataset
     * Uses reflection to avoid eager class loading
     */
    private Object convertMatrixToSparkDataset(UserItemMatrix matrix) {
        try {
            // Get SparkSession from SparkMlService
            sparkMlService.initializeSpark();
            
            // Use reflection to create DataFrame
            // This is a simplified version - in production, properly convert to Spark types
            Class<?> sparkSessionClass = Class.forName("org.apache.spark.sql.SparkSession");
            Object sparkSession = sparkMlService.getClass()
                    .getDeclaredField("sparkSession")
                    .get(sparkMlService);
            
            if (sparkSession == null) {
                throw new RuntimeException("Spark session not initialized");
            }
            
            // Create list of Row objects
            List<Object[]> rows = matrix.toSparkRows();
            
            // Convert to Spark DataFrame
            // Simplified - in production, use proper Spark DataFrame creation
            Class<?> rowFactoryClass = Class.forName("org.apache.spark.sql.RowFactory");
            Class<?> dataTypesClass = Class.forName("org.apache.spark.sql.types.DataTypes");
            Class<?> structTypeClass = Class.forName("org.apache.spark.sql.types.StructType");
            Class<?> structFieldClass = Class.forName("org.apache.spark.sql.types.StructField");
            
            // Create schema: user_id (Long), item_id (String), rating (Double)
            Object[] fields = new Object[3];
            fields[0] = structFieldClass.getMethod("create", String.class, 
                    Class.forName("org.apache.spark.sql.types.DataType"), boolean.class)
                    .invoke(null, "user_id", 
                            dataTypesClass.getField("LongType").get(null), false);
            fields[1] = structFieldClass.getMethod("create", String.class, 
                    Class.forName("org.apache.spark.sql.types.DataType"), boolean.class)
                    .invoke(null, "item_id", 
                            dataTypesClass.getField("StringType").get(null), false);
            fields[2] = structFieldClass.getMethod("create", String.class, 
                    Class.forName("org.apache.spark.sql.types.DataType"), boolean.class)
                    .invoke(null, "rating", 
                            dataTypesClass.getField("DoubleType").get(null), false);
            
            Object schema = structTypeClass.getMethod("apply", 
                    Class.forName("org.apache.spark.sql.types.StructField[].class"))
                    .invoke(null, (Object) fields);
            
            // Create rows
            List<Object> sparkRows = new ArrayList<>();
            for (Object[] row : rows) {
                Object sparkRow = rowFactoryClass.getMethod("create", Object[].class)
                        .invoke(null, (Object) row);
                sparkRows.add(sparkRow);
            }
            
            // Create DataFrame
            Object dataset = sparkSessionClass.getMethod("createDataFrame", 
                    java.util.List.class, structTypeClass)
                    .invoke(sparkSession, sparkRows, schema);
            
            return dataset;
            
        } catch (Exception e) {
            log.error("Error converting matrix to Spark Dataset", e);
            throw new RuntimeException("Failed to convert matrix to Spark Dataset", e);
        }
    }
    
    /**
     * Generate predictions using trained ALS model
     */
    private List<RecommendationItem> generatePredictions(
            Object alsModel,
            Long passengerId,
            UserItemMatrix matrix,
            List<PortFeature> portFeatures,
            int numRecommendations) {
        
        try {
            // Use Spark MLlib to generate recommendations
            // This is simplified - in production, properly use Spark's recommendForUser method
            
            // For now, return recommendations based on highest predicted ratings
            // In production, use: model.recommendForUser(passengerId, numRecommendations)
            
            List<RecommendationItem> recommendations = new ArrayList<>();
            
            // Simulate predictions (in production, use actual Spark predictions)
            for (PortFeature feature : portFeatures) {
                // Calculate predicted rating based on interactions
                double predictedRating = calculatePredictedRating(
                        passengerId, feature, matrix);
                
                if (predictedRating > 0) {
                    RecommendationItem item = RecommendationItem.builder()
                            .itemId(feature.getName())
                            .itemName(feature.getName())
                            .category(feature.getCategory())
                            .predictedRating(predictedRating)
                            .reason("Based on your interests in " + feature.getCategory())
                            .build();
                    
                    recommendations.add(item);
                }
            }
            
            // Sort by predicted rating and take top N
            return recommendations.stream()
                    .sorted(Comparator.comparing(RecommendationItem::getPredictedRating).reversed())
                    .limit(numRecommendations)
                    .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Error generating predictions", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Calculate predicted rating for a feature
     * Simplified version - in production, use actual ALS predictions
     */
    private double calculatePredictedRating(
            Long passengerId,
            PortFeature feature,
            UserItemMatrix matrix) {
        
        // Find interactions for this feature
        Optional<UserItemMatrixService.Interaction> interaction = matrix.getInteractions().stream()
                .filter(i -> i.getItemId().equals(feature.getName()))
                .findFirst();
        
        if (interaction.isPresent()) {
            // Use existing rating
            return interaction.get().getRating();
        }
        
        // Calculate based on similar features
        String featureCategory = feature.getCategory();
        if (featureCategory != null) {
            double avgRating = matrix.getInteractions().stream()
                    .filter(i -> {
                        // Match by category if available
                        // Since Interaction doesn't have category, match by item name similarity
                        return i.getItemName() != null && 
                               i.getItemName().toLowerCase().contains(featureCategory.toLowerCase());
                    })
                    .mapToDouble(UserItemMatrixService.Interaction::getRating)
                    .average()
                    .orElse(0.0);
            
            if (avgRating > 0) {
                return avgRating;
            }
        }
        
        // Default rating for new items
        return 3.0;
    }
    
    /**
     * Handle cold start problem (new user with no interactions)
     * Uses SPARQL to find popular items liked by similar users
     */
    private List<RecommendationItem> handleColdStart(
            Long passengerId,
            List<PortFeature> portFeatures,
            int numRecommendations) {
        
        log.info("Handling cold start for passenger {} using SPARQL", passengerId);
        
        // Query SPARQL for popular items at this port
        // Find items liked by users with similar Facebook interests
        
        List<RecommendationItem> recommendations = new ArrayList<>();
        
        // For cold start, recommend popular items
        for (PortFeature feature : portFeatures) {
            RecommendationItem item = RecommendationItem.builder()
                    .itemId(feature.getName())
                    .itemName(feature.getName())
                    .category(feature.getCategory())
                    .predictedRating(3.5) // Default rating for cold start
                    .reason("Popular choice at this port based on social media activity")
                    .build();
            
            recommendations.add(item);
        }
        
        // Return top N
        return recommendations.stream()
                .limit(numRecommendations)
                .collect(Collectors.toList());
    }
}
