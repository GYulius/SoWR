package com.cruise.recommender.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.sparql.core.DatasetImpl;
import org.apache.jena.tdb.TDBFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for processing Knowledge Graphs using Apache Spark and Apache Jena
 * Converts AIS and social media data into RDF triples and processes with SPARQL
 * 
 * Note: Spark features are optional and will be disabled if Spark classes cannot be loaded
 * due to Java module restrictions. RDF processing with Jena will still work.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeGraphSparkService {
    
    // Use Object to prevent eager class loading of Spark classes
    private volatile Object sparkSession;
    private volatile Object javaSparkContext;
    private volatile Model rdfModel;
    private volatile org.apache.jena.query.Dataset jenaDataset; // Jena Dataset, not Spark Dataset
    private volatile boolean sparkAvailable = false;
    private final Object initializationLock = new Object();
    
    @Value("${knowledge.graph.rdf.storage.path:./data/rdf-storage}")
    private String rdfStoragePath;
    
    @Value("${knowledge.graph.namespace:http://cruise.recommender.org/kg/}")
    private String namespace;
    
    /**
     * Initialize Spark session and Jena RDF model
     * Note: Spark initialization is lazy and may fail on Java 9+ due to module restrictions
     * Consider using Spark only when needed or configuring JVM arguments
     */
    public void initialize() {
        // Initialize Jena RDF model first (doesn't require Spark)
        if (rdfModel == null) {
            synchronized (initializationLock) {
                if (rdfModel == null) {
                    try {
                        // Initialize Jena TDB dataset for persistent RDF storage
                        org.apache.jena.query.Dataset tdbDataset = TDBFactory.createDataset(rdfStoragePath);
                        rdfModel = tdbDataset.getDefaultModel();
                        
                        // Set up namespace prefixes
                        rdfModel.setNsPrefix("cruise", namespace);
                        rdfModel.setNsPrefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
                        rdfModel.setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
                        rdfModel.setNsPrefix("owl", "http://www.w3.org/2002/07/owl#");
                        rdfModel.setNsPrefix("geo", "http://www.w3.org/2003/01/geo/wgs84_pos#");
                        
                        jenaDataset = new DatasetImpl(rdfModel);
                        
                        log.info("Jena RDF model initialized with namespace: {}", namespace);
                    } catch (Exception e) {
                        log.error("Error initializing Jena RDF model", e);
                    }
                }
            }
        }
        
        // Initialize Spark session lazily using reflection to prevent class loading errors
        // Spark may fail on Java 9+ due to module system restrictions
        // Use double-check locking to prevent multiple SparkContext creation
        if (sparkSession == null && !sparkAvailable) {
            synchronized (initializationLock) {
                // Double-check after acquiring lock
                if (sparkSession == null && !sparkAvailable) {
                    try {
                        // Check if SparkContext already exists (from previous failed initialization)
                        try {
                            Class<?> sparkContextClass = Class.forName("org.apache.spark.SparkContext");
                            Object existingContext = sparkContextClass.getMethod("getActive").invoke(null);
                            if (existingContext != null) {
                                log.warn("Another SparkContext is already active. Reusing existing context.");
                                // Try to get SparkSession from existing context
                                Class<?> sparkSessionClass = Class.forName("org.apache.spark.sql.SparkSession");
                                sparkSession = sparkSessionClass.getMethod("active").invoke(null);
                                if (sparkSession != null) {
                                    sparkAvailable = true;
                                    log.info("Reusing existing Spark session");
                                    return;
                                }
                            }
                        } catch (Exception e) {
                            // No active context, proceed with creation
                        }
                        
                        // Use reflection to avoid eager class loading
                        // Catch all possible initialization errors
                        Class<?> sparkSessionClass = Class.forName("org.apache.spark.sql.SparkSession");
                        Class<?> sparkSessionBuilderClass = Class.forName("org.apache.spark.sql.SparkSession$Builder");
                        
                        Object builder = sparkSessionClass.getMethod("builder").invoke(null);
                        builder = sparkSessionBuilderClass.getMethod("appName", String.class).invoke(builder, "KnowledgeGraphProcessor");
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
                        log.info("Spark session initialized for Knowledge Graph processing");
                    } catch (ClassNotFoundException e) {
                        log.warn("Spark classes not found on classpath. Spark features will be disabled.");
                        sparkAvailable = false;
                    } catch (NoClassDefFoundError | ExceptionInInitializerError e) {
                        log.warn("Spark classes cannot be initialized due to Java module restrictions. " +
                                "Spark features will be disabled. To enable Spark, add JVM arguments: " +
                                "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED " +
                                "--add-opens=java.base/java.lang=ALL-UNNAMED " +
                                "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED " +
                                "--add-opens=java.base/java.io=ALL-UNNAMED");
                        sparkAvailable = false;
                    } catch (Throwable e) {
                        // Catch any other errors including LinkageError, etc.
                        // Log the full exception to help diagnose the issue
                        Throwable cause = e.getCause();
                        String causeInfo = cause != null ? " (Caused by: " + cause.getClass().getSimpleName() + ": " + cause.getMessage() + ")" : "";
                        
                        log.warn("Failed to initialize Spark session. Spark features will be disabled. " +
                                "Error: " + e.getClass().getSimpleName() + " - " + e.getMessage() + causeInfo +
                                ". To enable Spark, add JVM arguments: " +
                                "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED " +
                                "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED");
                        
                        // Log full stack trace at debug level for troubleshooting
                        if (log.isDebugEnabled()) {
                            log.debug("Full Spark initialization exception:", e);
                        }
                        
                        sparkAvailable = false;
                    }
                }
            }
        }
        
    }
    
    /**
     * Process AIS data and convert to RDF triples
     * Called from AisDataService after processing
     */
    public void processAisDataToRDF(com.cruise.recommender.service.AisDataService.AisDataMessage message) {
        log.debug("Processing AIS data for Knowledge Graph: MMSI {}", message.getMmsi());
        
        initialize();
        
        // Skip if RDF model is not initialized
        if (rdfModel == null) {
            log.warn("RDF model not initialized, skipping AIS data processing");
            return;
        }
        
        try {
            // Convert AIS data message to RDF triples
            String shipUri = namespace + "ship/" + message.getMmsi();
            Resource ship = rdfModel.createResource(shipUri);
            
            // Add ship properties
            ship.addProperty(rdfModel.createProperty(namespace + "hasMMSI"), 
                rdfModel.createLiteral(message.getMmsi() != null ? message.getMmsi() : ""));
            ship.addProperty(rdfModel.createProperty(namespace + "hasName"), 
                rdfModel.createLiteral(message.getShipName() != null ? message.getShipName() : "Unknown"));
            
            // Add location as geo:Point
            if (message.getLatitude() != null && message.getLongitude() != null) {
                Resource location = rdfModel.createResource();
                location.addProperty(rdfModel.createProperty("geo:lat"), 
                    rdfModel.createLiteral(String.valueOf(message.getLatitude())));
                location.addProperty(rdfModel.createProperty("geo:long"), 
                    rdfModel.createLiteral(String.valueOf(message.getLongitude())));
                ship.addProperty(rdfModel.createProperty(namespace + "hasLocation"), location);
            }
            
            // Add timestamp
            if (message.getTimestamp() != null) {
                ship.addProperty(rdfModel.createProperty(namespace + "hasTimestamp"), 
                    rdfModel.createLiteral(message.getTimestamp().toString()));
            }
            
            log.debug("Added AIS data to RDF model for ship: {}", message.getMmsi());
            
        } catch (Exception e) {
            log.error("Error processing AIS data to RDF", e);
        }
    }
    
    /**
     * Process social media data and convert to RDF triples
     */
    @RabbitListener(queues = "social.media.queue")
    public void processSocialMediaToRDF(Object socialMediaPost) {
        log.info("Processing social media data for Knowledge Graph");
        
        initialize();
        
        // Skip if RDF model is not initialized
        if (rdfModel == null) {
            log.warn("RDF model not initialized, skipping social media processing");
            return;
        }
        
        try {
            // Convert social media post to RDF triples
            if (socialMediaPost instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> post = (Map<String, Object>) socialMediaPost;
                
                String postUri = namespace + "post/" + post.get("postId");
                Resource postResource = rdfModel.createResource(postUri);
                
                // Add post properties
                postResource.addProperty(rdfModel.createProperty(namespace + "hasPlatform"), 
                    rdfModel.createLiteral(String.valueOf(post.get("platform"))));
                postResource.addProperty(rdfModel.createProperty(namespace + "hasAuthor"), 
                    rdfModel.createLiteral(String.valueOf(post.get("authorId"))));
                postResource.addProperty(rdfModel.createProperty(namespace + "hasContent"), 
                    rdfModel.createLiteral(String.valueOf(post.get("content"))));
                
                // Add location if available
                if (post.get("location") != null) {
                    postResource.addProperty(rdfModel.createProperty(namespace + "hasLocation"), 
                        rdfModel.createLiteral(String.valueOf(post.get("location"))));
                }
                
                // Add keywords as RDF properties
                if (post.get("keywords") instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> keywords = (List<String>) post.get("keywords");
                    for (String keyword : keywords) {
                        postResource.addProperty(rdfModel.createProperty(namespace + "hasKeyword"), 
                            rdfModel.createLiteral(keyword));
                    }
                }
                
                // Link to passenger interest
                String passengerUri = namespace + "passenger/" + post.get("authorId");
                Resource passenger = rdfModel.createResource(passengerUri);
                passenger.addProperty(rdfModel.createProperty(namespace + "hasInterest"), postResource);
                
                log.debug("Added social media post to RDF model: {}", post.get("postId"));
            }
            
        } catch (Exception e) {
            log.error("Error processing social media data to RDF", e);
        }
    }
    
    /**
     * Execute SPARQL query on the RDF model
     */
    public List<Map<String, String>> executeSparqlQuery(String sparqlQuery) {
        initialize();
        
        List<Map<String, String>> results = new ArrayList<>();
        
        if (jenaDataset == null || rdfModel == null) {
            log.warn("RDF model not initialized, cannot execute SPARQL query");
            return results;
        }
        
        try {
            Query query = QueryFactory.create(sparqlQuery);
            QueryExecution qexec = QueryExecutionFactory.create(query, jenaDataset);
            
            ResultSet resultSet = qexec.execSelect();
            
            while (resultSet.hasNext()) {
                QuerySolution solution = resultSet.nextSolution();
                Map<String, String> resultMap = new java.util.HashMap<>();
                
                for (String varName : resultSet.getResultVars()) {
                    RDFNode node = solution.get(varName);
                    if (node != null) {
                        resultMap.put(varName, node.toString());
                    }
                }
                
                results.add(resultMap);
            }
            
            qexec.close();
            
            log.info("Executed SPARQL query, returned {} results", results.size());
            
        } catch (Exception e) {
            log.error("Error executing SPARQL query", e);
        }
        
        return results;
    }
    
    /**
     * Find passenger interests from social media posts
     */
    public List<Map<String, String>> findPassengerInterests(String passengerId) {
        String sparqlQuery = String.format(
            "PREFIX cruise: <%s> " +
            "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " +
            "SELECT ?keyword ?location ?platform " +
            "WHERE { " +
            "  ?passenger cruise:hasInterest ?post . " +
            "  ?post cruise:hasKeyword ?keyword . " +
            "  ?post cruise:hasLocation ?location . " +
            "  ?post cruise:hasPlatform ?platform . " +
            "  FILTER (?passenger = <%spassenger/%s>) " +
            "}",
            namespace, namespace, passengerId
        );
        
        return executeSparqlQuery(sparqlQuery);
    }
    
    /**
     * Find ships near a location
     */
    public List<Map<String, String>> findShipsNearLocation(double latitude, double longitude, double radiusKm) {
        String sparqlQuery = String.format(
            "PREFIX cruise: <%s> " +
            "PREFIX geo: <http://www.w3.org/2003/01/geo/wgs84_pos#> " +
            "SELECT ?ship ?name ?lat ?long " +
            "WHERE { " +
            "  ?ship cruise:hasLocation ?location . " +
            "  ?location geo:lat ?lat . " +
            "  ?location geo:long ?long . " +
            "  ?ship cruise:hasName ?name . " +
            "  FILTER (abs(?lat - %f) < %f AND abs(?long - %f) < %f) " +
            "}",
            namespace, latitude, radiusKm / 111.0, longitude, radiusKm / (111.0 * Math.cos(Math.toRadians(latitude)))
        );
        
        return executeSparqlQuery(sparqlQuery);
    }
    
    /**
     * Find popular interests by location
     */
    public List<Map<String, String>> findPopularInterestsByLocation(String location) {
        String sparqlQuery = String.format(
            "PREFIX cruise: <%s> " +
            "SELECT ?keyword (COUNT(?post) as ?count) " +
            "WHERE { " +
            "  ?post cruise:hasLocation \"%s\" . " +
            "  ?post cruise:hasKeyword ?keyword . " +
            "} " +
            "GROUP BY ?keyword " +
            "ORDER BY DESC(?count) " +
            "LIMIT 10",
            namespace, location
        );
        
        return executeSparqlQuery(sparqlQuery);
    }
    
    /**
     * Export RDF model to file
     */
    public void exportRdfModel(String filePath) {
        initialize();
        
        try {
            RDFDataMgr.write(new java.io.FileOutputStream(filePath), rdfModel, RDFFormat.TURTLE);
            log.info("Exported RDF model to: {}", filePath);
        } catch (Exception e) {
            log.error("Error exporting RDF model", e);
        }
    }
    
    /**
     * Get RDF model as Turtle string
     */
    public String getRdfModelAsTurtle() {
        initialize();
        
        StringWriter writer = new StringWriter();
        RDFDataMgr.write(writer, rdfModel, RDFFormat.TURTLE);
        return writer.toString();
    }
    
    /**
     * Process RDF data with Spark for analytics
     * Note: Requires Spark to be initialized. Returns null if Spark is not available.
     */
    public Object processRdfWithSpark() {
        initialize();
        
        if (!sparkAvailable || sparkSession == null) {
            log.warn("Spark session not available. Cannot process RDF with Spark.");
            return null;
        }
        
        try {
            // Convert RDF triples to Spark DataFrame using reflection
            String sparqlQuery = String.format(
                "PREFIX cruise: <%s> " +
                "SELECT ?subject ?predicate ?object " +
                "WHERE { ?subject ?predicate ?object }",
                namespace
            );
            
            List<Map<String, String>> results = executeSparqlQuery(sparqlQuery);
            
            // Use reflection to create Spark DataFrame
            Class<?> dataTypesClass = Class.forName("org.apache.spark.sql.types.DataTypes");
            Class<?> structTypeClass = Class.forName("org.apache.spark.sql.types.StructType");
            Class<?> structFieldClass = Class.forName("org.apache.spark.sql.types.StructField");
            Class<?> stringTypeClass = Class.forName("org.apache.spark.sql.types.StringType");
            Class<?> rowFactoryClass = Class.forName("org.apache.spark.sql.RowFactory");
            Class<?> rowClass = Class.forName("org.apache.spark.sql.Row");
            
            Object stringType = stringTypeClass.getField("INSTANCE").get(null);
            Object[] fields = new Object[3];
            fields[0] = structFieldClass.getMethod("create", String.class, 
                Class.forName("org.apache.spark.sql.types.DataType"), boolean.class)
                .invoke(null, "subject", stringType, false);
            fields[1] = structFieldClass.getMethod("create", String.class, 
                Class.forName("org.apache.spark.sql.types.DataType"), boolean.class)
                .invoke(null, "predicate", stringType, false);
            fields[2] = structFieldClass.getMethod("create", String.class, 
                Class.forName("org.apache.spark.sql.types.DataType"), boolean.class)
                .invoke(null, "object", stringType, false);
            
            Object schema = structTypeClass.getMethod("create", structFieldClass.arrayType())
                .invoke(null, (Object) fields);
            
            // Convert results to Spark Rows
            List<Object> rows = new ArrayList<>();
            for (Map<String, String> result : results) {
                Object row = rowFactoryClass.getMethod("create", Object[].class)
                    .invoke(null, (Object) new Object[]{
                        result.get("subject"),
                        result.get("predicate"),
                        result.get("object")
                    });
                rows.add(row);
            }
            
            // Create DataFrame from rows
            if (rows.isEmpty()) {
                return sparkSession.getClass().getMethod("createDataFrame", 
                    java.util.List.class, structTypeClass)
                    .invoke(sparkSession, new ArrayList<>(), schema);
            }
            
            return sparkSession.getClass().getMethod("createDataFrame", 
                java.util.List.class, structTypeClass)
                .invoke(sparkSession, rows, schema);
                
        } catch (Exception e) {
            log.error("Error processing RDF with Spark", e);
            return null;
        }
    }
    
    /**
     * Close resources
     */
    public void close() {
        if (rdfModel != null) {
            rdfModel.close();
        }
        if (sparkSession != null && sparkAvailable) {
            try {
                // Use reflection to stop Spark session
                sparkSession.getClass().getMethod("stop").invoke(sparkSession);
            } catch (Exception e) {
                log.error("Error stopping Spark session", e);
            }
        }
        
        if (javaSparkContext != null && sparkAvailable) {
            try {
                // Use reflection to close JavaSparkContext
                javaSparkContext.getClass().getMethod("close").invoke(javaSparkContext);
            } catch (Exception e) {
                log.error("Error closing JavaSparkContext", e);
            }
        }
    }
}

