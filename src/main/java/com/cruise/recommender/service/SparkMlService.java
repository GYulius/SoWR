package com.cruise.recommender.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Spark-based service for machine learning and big data processing
 * Handles recommendation algorithms, long tail recommendations, and data analysis
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SparkMlService {
    
    // Use Object to prevent eager class loading
    private Object sparkSession;
    private Object javaSparkContext;
    private boolean sparkAvailable = false;
    
    /**
     * Initialize Spark session using reflection to prevent class loading errors
     */
    public void initializeSpark() {
        if (sparkSession == null && !sparkAvailable) {
            try {
                // Use reflection to avoid eager class loading
                Class<?> sparkSessionClass = Class.forName("org.apache.spark.sql.SparkSession");
                Class<?> sparkSessionBuilderClass = Class.forName("org.apache.spark.sql.SparkSession$Builder");
                
                Object builder = sparkSessionClass.getMethod("builder").invoke(null);
                builder = sparkSessionBuilderClass.getMethod("appName", String.class).invoke(builder, "CruiseRecommenderML");
                builder = sparkSessionBuilderClass.getMethod("master", String.class).invoke(builder, "local[*]");
                builder = sparkSessionBuilderClass.getMethod("config", String.class, String.class).invoke(builder, "spark.sql.warehouse.dir", "file:///tmp/spark-warehouse");
                builder = sparkSessionBuilderClass.getMethod("config", String.class, String.class).invoke(builder, "spark.driver.memory", "2g");
                builder = sparkSessionBuilderClass.getMethod("config", String.class, String.class).invoke(builder, "spark.executor.memory", "2g");
                
                sparkSession = sparkSessionBuilderClass.getMethod("getOrCreate").invoke(builder);
                
                // Initialize JavaSparkContext
                Class<?> javaSparkContextClass = Class.forName("org.apache.spark.api.java.JavaSparkContext");
                Object sparkContext = sparkSessionClass.getMethod("sparkContext").invoke(sparkSession);
                javaSparkContext = javaSparkContextClass.getMethod("fromSparkContext", 
                    Class.forName("org.apache.spark.SparkContext")).invoke(null, sparkContext);
                
                sparkAvailable = true;
                log.info("Spark session initialized successfully");
            } catch (ClassNotFoundException e) {
                log.warn("Spark classes not found. Spark ML features will be disabled.");
                sparkAvailable = false;
            } catch (NoClassDefFoundError | ExceptionInInitializerError e) {
                log.warn("Spark classes cannot be initialized due to Java module restrictions. " +
                        "Spark ML features will be disabled. Add JVM arguments: " +
                        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED");
                sparkAvailable = false;
            } catch (Throwable e) {
                log.warn("Failed to initialize Spark session. Spark ML features will be disabled.", e);
                sparkAvailable = false;
            }
        }
    }
    
    /**
     * Train collaborative filtering model using ALS (Alternating Least Squares)
     * For long tail recommendations
     */
    public Object trainCollaborativeFilteringModel(Object userItemRatings) {
        log.info("Training collaborative filtering model for long tail recommendations");
        
        initializeSpark();
        
        if (!sparkAvailable) {
            log.warn("Spark not available. Cannot train collaborative filtering model.");
            return null;
        }
        
        try {
            // Use reflection to work with Spark types
            Class<?> datasetClass = Class.forName("org.apache.spark.sql.Dataset");
            Class<?> rowClass = Class.forName("org.apache.spark.sql.Row");
            Class<?> alsClass = Class.forName("org.apache.spark.ml.recommendation.ALS");
            
            // Split data into training and test sets
            Object[] splits = (Object[]) datasetClass.getMethod("randomSplit", double[].class)
                    .invoke(userItemRatings, (Object) new double[]{0.8, 0.2});
            Object training = splits[0];
            
            // Configure ALS for long tail recommendations
            Object als = alsClass.getConstructor().newInstance();
            alsClass.getMethod("setMaxIter", int.class).invoke(als, 10);
            alsClass.getMethod("setRegParam", double.class).invoke(als, 0.01);
            alsClass.getMethod("setUserCol", String.class).invoke(als, "user_id");
            alsClass.getMethod("setItemCol", String.class).invoke(als, "item_id");
            alsClass.getMethod("setRatingCol", String.class).invoke(als, "rating");
            alsClass.getMethod("setColdStartStrategy", String.class).invoke(als, "drop");
            alsClass.getMethod("setImplicitPrefs", boolean.class).invoke(als, false);
            
            // Train the model
            Object model = alsClass.getMethod("fit", datasetClass).invoke(als, training);
            
            log.info("Collaborative filtering model trained successfully");
            return model;
        } catch (Exception e) {
            log.error("Error training collaborative filtering model", e);
            return null;
        }
    }
    
    /**
     * Generate long tail recommendations
     * Long tail items are less popular but highly relevant to specific users
     */
    public Object generateLongTailRecommendations(
            Object model, 
            Object userItemRatings,
            int numRecommendations) {
        
        log.info("Generating long tail recommendations");
        
        initializeSpark();
        
        if (!sparkAvailable) {
            log.warn("Spark not available. Cannot generate long tail recommendations.");
            return null;
        }
        
        try {
            // Use reflection to work with Spark types
            Class<?> datasetClass = Class.forName("org.apache.spark.sql.Dataset");
            
            // Get all unique items
            Object allItems = datasetClass.getMethod("select", String.class).invoke(userItemRatings, "item_id");
            allItems = datasetClass.getMethod("distinct").invoke(allItems);
            
            // Calculate item popularity
            Object itemPopularity = datasetClass.getMethod("groupBy", String.class).invoke(userItemRatings, "item_id");
            itemPopularity = datasetClass.getMethod("count").invoke(itemPopularity);
            itemPopularity = datasetClass.getMethod("withColumnRenamed", String.class, String.class)
                    .invoke(itemPopularity, "count", "popularity");
            
            // Get long tail items
            long totalItems = (Long) datasetClass.getMethod("count").invoke(allItems);
            long tailThreshold = (long) (totalItems * 0.8);
            
            Object longTailItems = datasetClass.getMethod("filter", String.class)
                    .invoke(itemPopularity, "popularity < " + tailThreshold);
            longTailItems = datasetClass.getMethod("select", String.class).invoke(longTailItems, "item_id");
            
            // Generate recommendations
            Object recommendations = model.getClass().getMethod("recommendForAllUsers", int.class)
                    .invoke(model, numRecommendations);
            
            // Filter to long tail items
            Object longTailRecommendations = datasetClass.getMethod("join", datasetClass, String.class)
                    .invoke(recommendations, longTailItems, "item_id");
            
            long count = (Long) datasetClass.getMethod("count").invoke(longTailRecommendations);
            log.info("Generated {} long tail recommendations", count);
            
            return longTailRecommendations;
        } catch (Exception e) {
            log.error("Error generating long tail recommendations", e);
            return null;
        }
    }
    
    /**
     * Analyze user behavior patterns using Spark SQL
     */
    public Map<String, Object> analyzeUserBehavior(Object userInteractions) {
        log.info("Analyzing user behavior patterns");
        
        initializeSpark();
        
        if (!sparkAvailable) {
            log.warn("Spark not available. Cannot analyze user behavior.");
            return Map.of();
        }
        
        try {
            Class<?> datasetClass = Class.forName("org.apache.spark.sql.Dataset");
            
            datasetClass.getMethod("createOrReplaceTempView", String.class).invoke(userInteractions, "user_interactions");
            
            // Calculate various metrics using reflection
            Object avgRatingsByCategory = sparkSession.getClass().getMethod("sql", String.class).invoke(sparkSession,
                    "SELECT item_type, AVG(rating) as avg_rating, COUNT(*) as interaction_count " +
                    "FROM user_interactions " +
                    "WHERE rating IS NOT NULL " +
                    "GROUP BY item_type");
            
            Object popularTimeSlots = sparkSession.getClass().getMethod("sql", String.class).invoke(sparkSession,
                    "SELECT HOUR(created_at) as hour, COUNT(*) as interaction_count " +
                    "FROM user_interactions " +
                    "GROUP BY HOUR(created_at) " +
                    "ORDER BY interaction_count DESC");
            
            Object userEngagement = sparkSession.getClass().getMethod("sql", String.class).invoke(sparkSession,
                    "SELECT user_id, COUNT(*) as total_interactions, " +
                    "AVG(rating) as avg_rating, " +
                    "COUNT(DISTINCT item_type) as categories_explored " +
                    "FROM user_interactions " +
                    "GROUP BY user_id");
            
            return Map.of(
                    "avgRatingsByCategory", datasetClass.getMethod("collectAsList").invoke(avgRatingsByCategory),
                    "popularTimeSlots", datasetClass.getMethod("collectAsList").invoke(popularTimeSlots),
                    "userEngagement", datasetClass.getMethod("collectAsList").invoke(userEngagement)
            );
        } catch (Exception e) {
            log.error("Error analyzing user behavior", e);
            return Map.of();
        }
    }
    
    /**
     * Process AIS data using Spark for real-time analytics
     */
    public Object processAisDataAnalytics(Object aisData) {
        log.info("Processing AIS data analytics with Spark");
        
        initializeSpark();
        
        if (!sparkAvailable) {
            log.warn("Spark not available. Cannot process AIS data analytics.");
            return null;
        }
        
        try {
            Class<?> datasetClass = Class.forName("org.apache.spark.sql.Dataset");
            
            datasetClass.getMethod("createOrReplaceTempView", String.class).invoke(aisData, "ais_data");
            
            // Calculate ship movement patterns
            Object shipMovements = sparkSession.getClass().getMethod("sql", String.class).invoke(sparkSession,
                    "SELECT mmsi, ship_name, " +
                    "COUNT(*) as position_updates, " +
                    "AVG(speed) as avg_speed, " +
                    "MAX(speed) as max_speed, " +
                    "MIN(latitude) as min_lat, MAX(latitude) as max_lat, " +
                    "MIN(longitude) as min_lng, MAX(longitude) as max_lng " +
                    "FROM ais_data " +
                    "WHERE timestamp >= CURRENT_TIMESTAMP - INTERVAL 24 HOUR " +
                    "GROUP BY mmsi, ship_name");
            
            return shipMovements;
        } catch (Exception e) {
            log.error("Error processing AIS data analytics", e);
            return null;
        }
    }
    
    /**
     * Calculate recommendation diversity metrics
     */
    public Map<String, Double> calculateRecommendationDiversity(Object recommendations) {
        log.info("Calculating recommendation diversity metrics");
        
        initializeSpark();
        
        if (!sparkAvailable) {
            log.warn("Spark not available. Cannot calculate recommendation diversity.");
            return Map.of();
        }
        
        try {
            Class<?> datasetClass = Class.forName("org.apache.spark.sql.Dataset");
            Class<?> rowClass = Class.forName("org.apache.spark.sql.Row");
            
            datasetClass.getMethod("createOrReplaceTempView", String.class).invoke(recommendations, "recommendations");
            
            // Calculate catalog coverage
            Object totalItemsResult = sparkSession.getClass().getMethod("sql", String.class)
                    .invoke(sparkSession, "SELECT COUNT(DISTINCT item_id) FROM recommendations");
            Object firstRow = datasetClass.getMethod("first").invoke(totalItemsResult);
            long totalItems = (Long) rowClass.getMethod("getLong", int.class).invoke(firstRow, 0);
            
            Object totalCatalogResult = sparkSession.getClass().getMethod("sql", String.class)
                    .invoke(sparkSession, "SELECT COUNT(DISTINCT item_id) FROM items");
            Object catalogRow = datasetClass.getMethod("first").invoke(totalCatalogResult);
            long totalCatalog = (Long) rowClass.getMethod("getLong", int.class).invoke(catalogRow, 0);
            double catalogCoverage = totalCatalog > 0 ? (double) totalItems / totalCatalog * 100 : 0.0;
            
            // Calculate average recommendation list length
            Object avgLengthResult = sparkSession.getClass().getMethod("sql", String.class).invoke(sparkSession,
                    "SELECT AVG(list_length) FROM " +
                    "(SELECT user_id, COUNT(*) as list_length FROM recommendations GROUP BY user_id)");
            Object avgRow = datasetClass.getMethod("first").invoke(avgLengthResult);
            double avgListLength = (Double) rowClass.getMethod("getDouble", int.class).invoke(avgRow, 0);
            
            // Calculate long tail percentage
            Object itemPopularity = sparkSession.getClass().getMethod("sql", String.class).invoke(sparkSession,
                    "SELECT item_id, COUNT(*) as popularity FROM recommendations GROUP BY item_id");
            long totalRecommendations = (Long) datasetClass.getMethod("count").invoke(recommendations);
            Object longTailItems = datasetClass.getMethod("filter", String.class)
                    .invoke(itemPopularity, "popularity < " + (totalRecommendations * 0.1));
            long longTailCount = (Long) datasetClass.getMethod("count").invoke(longTailItems);
            double longTailPercentage = totalRecommendations > 0 ? 
                    (double) longTailCount / totalRecommendations * 100 : 0.0;
            
            return Map.of(
                    "catalogCoverage", catalogCoverage,
                    "avgListLength", avgListLength,
                    "longTailPercentage", longTailPercentage
            );
        } catch (Exception e) {
            log.error("Error calculating recommendation diversity", e);
            return Map.of();
        }
    }
    
    /**
     * Close Spark session
     */
    public void closeSpark() {
        if (sparkSession != null && sparkAvailable) {
            try {
                sparkSession.getClass().getMethod("stop").invoke(sparkSession);
                sparkSession = null;
                sparkAvailable = false;
                log.info("Spark session closed");
            } catch (Exception e) {
                log.error("Error closing Spark session", e);
            }
        }
    }
}
