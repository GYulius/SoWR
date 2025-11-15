package com.cruise.recommender.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.ml.recommendation.ALS;
import org.apache.spark.ml.recommendation.ALSModel;
import org.apache.spark.mllib.recommendation.MatrixFactorizationModel;
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
    
    private SparkSession sparkSession;
    private JavaSparkContext javaSparkContext;
    
    /**
     * Initialize Spark session
     */
    public void initializeSpark() {
        if (sparkSession == null) {
            sparkSession = SparkSession.builder()
                    .appName("CruiseRecommenderML")
                    .master("local[*]")
                    .config("spark.sql.warehouse.dir", "file:///tmp/spark-warehouse")
                    .config("spark.driver.memory", "2g")
                    .config("spark.executor.memory", "2g")
                    .getOrCreate();
            
            javaSparkContext = JavaSparkContext.fromSparkContext(sparkSession.sparkContext());
            
            log.info("Spark session initialized successfully");
        }
    }
    
    /**
     * Train collaborative filtering model using ALS (Alternating Least Squares)
     * For long tail recommendations
     */
    public ALSModel trainCollaborativeFilteringModel(Dataset<Row> userItemRatings) {
        log.info("Training collaborative filtering model for long tail recommendations");
        
        initializeSpark();
        
        // Split data into training and test sets
        Dataset<Row>[] splits = userItemRatings.randomSplit(new double[]{0.8, 0.2});
        Dataset<Row> training = splits[0];
        Dataset<Row> test = splits[1];
        
        // Configure ALS for long tail recommendations
        ALS als = new ALS()
                .setMaxIter(10)
                .setRegParam(0.01)
                .setUserCol("user_id")
                .setItemCol("item_id")
                .setRatingCol("rating")
                .setColdStartStrategy("drop") // Handle cold start problem
                .setImplicitPrefs(false); // Explicit ratings
        
        // Train the model
        ALSModel model = als.fit(training);
        
        log.info("Collaborative filtering model trained successfully");
        
        return model;
    }
    
    /**
     * Generate long tail recommendations
     * Long tail items are less popular but highly relevant to specific users
     */
    public Dataset<Row> generateLongTailRecommendations(
            ALSModel model, 
            Dataset<Row> userItemRatings,
            int numRecommendations) {
        
        log.info("Generating long tail recommendations");
        
        initializeSpark();
        
        // Get all unique items
        Dataset<Row> allItems = userItemRatings.select("item_id").distinct();
        
        // Calculate item popularity (for filtering out popular items)
        Dataset<Row> itemPopularity = userItemRatings
                .groupBy("item_id")
                .count()
                .withColumnRenamed("count", "popularity");
        
        // Get long tail items (less popular items)
        long totalItems = allItems.count();
        long tailThreshold = (long) (totalItems * 0.8); // Bottom 80% are long tail
        
        Dataset<Row> longTailItems = itemPopularity
                .filter("popularity < " + tailThreshold)
                .select("item_id");
        
        // Generate recommendations for all users
        Dataset<Row> recommendations = model.recommendForAllUsers(numRecommendations);
        
        // Filter to only include long tail items
        Dataset<Row> longTailRecommendations = recommendations
                .join(longTailItems, "item_id");
        
        log.info("Generated {} long tail recommendations", longTailRecommendations.count());
        
        return longTailRecommendations;
    }
    
    /**
     * Analyze user behavior patterns using Spark SQL
     */
    public Map<String, Object> analyzeUserBehavior(Dataset<Row> userInteractions) {
        log.info("Analyzing user behavior patterns");
        
        initializeSpark();
        
        userInteractions.createOrReplaceTempView("user_interactions");
        
        // Calculate various metrics
        Dataset<Row> avgRatingsByCategory = sparkSession.sql(
                "SELECT item_type, AVG(rating) as avg_rating, COUNT(*) as interaction_count " +
                "FROM user_interactions " +
                "WHERE rating IS NOT NULL " +
                "GROUP BY item_type"
        );
        
        Dataset<Row> popularTimeSlots = sparkSession.sql(
                "SELECT HOUR(created_at) as hour, COUNT(*) as interaction_count " +
                "FROM user_interactions " +
                "GROUP BY HOUR(created_at) " +
                "ORDER BY interaction_count DESC"
        );
        
        Dataset<Row> userEngagement = sparkSession.sql(
                "SELECT user_id, COUNT(*) as total_interactions, " +
                "AVG(rating) as avg_rating, " +
                "COUNT(DISTINCT item_type) as categories_explored " +
                "FROM user_interactions " +
                "GROUP BY user_id"
        );
        
        return Map.of(
                "avgRatingsByCategory", avgRatingsByCategory.collectAsList(),
                "popularTimeSlots", popularTimeSlots.collectAsList(),
                "userEngagement", userEngagement.collectAsList()
        );
    }
    
    /**
     * Process AIS data using Spark for real-time analytics
     */
    public Dataset<Row> processAisDataAnalytics(Dataset<Row> aisData) {
        log.info("Processing AIS data analytics with Spark");
        
        initializeSpark();
        
        aisData.createOrReplaceTempView("ais_data");
        
        // Calculate ship movement patterns
        Dataset<Row> shipMovements = sparkSession.sql(
                "SELECT mmsi, ship_name, " +
                "COUNT(*) as position_updates, " +
                "AVG(speed) as avg_speed, " +
                "MAX(speed) as max_speed, " +
                "MIN(latitude) as min_lat, MAX(latitude) as max_lat, " +
                "MIN(longitude) as min_lng, MAX(longitude) as max_lng " +
                "FROM ais_data " +
                "WHERE timestamp >= CURRENT_TIMESTAMP - INTERVAL 24 HOUR " +
                "GROUP BY mmsi, ship_name"
        );
        
        // Identify ships approaching ports
        Dataset<Row> approachingPorts = sparkSession.sql(
                "SELECT mmsi, ship_name, destination, " +
                "latitude, longitude, speed, course, " +
                "TIMESTAMPDIFF(HOUR, CURRENT_TIMESTAMP, eta) as hours_to_arrival " +
                "FROM ais_data " +
                "WHERE destination IS NOT NULL " +
                "AND TIMESTAMPDIFF(HOUR, CURRENT_TIMESTAMP, eta) BETWEEN 0 AND 24 " +
                "ORDER BY hours_to_arrival ASC"
        );
        
        return shipMovements;
    }
    
    /**
     * Calculate recommendation diversity metrics
     */
    public Map<String, Double> calculateRecommendationDiversity(Dataset<Row> recommendations) {
        log.info("Calculating recommendation diversity metrics");
        
        initializeSpark();
        
        recommendations.createOrReplaceTempView("recommendations");
        
        // Calculate catalog coverage (percentage of items recommended)
        long totalItems = sparkSession.sql("SELECT COUNT(DISTINCT item_id) FROM recommendations").first().getLong(0);
        long totalCatalog = sparkSession.sql("SELECT COUNT(DISTINCT item_id) FROM items").first().getLong(0);
        double catalogCoverage = (double) totalItems / totalCatalog * 100;
        
        // Calculate average recommendation list length
        double avgListLength = sparkSession.sql(
                "SELECT AVG(list_length) FROM " +
                "(SELECT user_id, COUNT(*) as list_length FROM recommendations GROUP BY user_id)"
        ).first().getDouble(0);
        
        // Calculate long tail percentage
        Dataset<Row> itemPopularity = sparkSession.sql(
                "SELECT item_id, COUNT(*) as popularity FROM recommendations GROUP BY item_id"
        );
        long totalRecommendations = recommendations.count();
        long longTailCount = itemPopularity.filter("popularity < " + (totalRecommendations * 0.1)).count();
        double longTailPercentage = (double) longTailCount / totalRecommendations * 100;
        
        return Map.of(
                "catalogCoverage", catalogCoverage,
                "avgListLength", avgListLength,
                "longTailPercentage", longTailPercentage
        );
    }
    
    /**
     * Close Spark session
     */
    public void closeSpark() {
        if (sparkSession != null) {
            sparkSession.stop();
            sparkSession = null;
            log.info("Spark session closed");
        }
    }
}
