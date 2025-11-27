package com.cruise.recommender.service;

import com.cruise.recommender.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.exec.http.QueryExecutionHTTP;
import org.apache.jena.sparql.exec.http.UpdateExecutionHTTP;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service for creating and managing RDF datasets from social media posts
 * Converts social media ingestion data (Facebook, Twitter, Instagram) to RDF triples
 * and stores them in Fuseki for SPARQL queries in the recommendation system
 * 
 * Based on PortRdfService pattern and Apache Jena RDF API
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SocialMediaRdfService {
    
    // Fuseki SPARQL endpoints
    @Value("${knowledge.graph.endpoint:http://localhost:3030/cruise_kg/sparql}")
    private String sparqlEndpoint;
    
    @Value("${knowledge.graph.update-endpoint:http://localhost:3030/cruise_kg/update}")
    private String updateEndpoint;
    
    @Value("${knowledge.graph.namespace:http://cruise.recommender.org/kg/}")
    private String namespace;
    
    // Fuseki credentials
    @Value("${knowledge.graph.username:}")
    private String fusekiUsername;
    
    @Value("${knowledge.graph.password:}")
    private String fusekiPassword;
    
    // Namespaces
    private static final String SKOS_NS = "http://www.w3.org/2004/02/skos/core#";
    private static final String RDF_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    private static final String RDFS_NS = "http://www.w3.org/2000/01/rdf-schema#";
    private static final String GEO_NS = "http://www.w3.org/2003/01/geo/wgs84_pos#";
    private static final String DCTERMS_NS = "http://purl.org/dc/terms/";
    private static final String SIOC_NS = "http://rdfs.org/sioc/ns#"; // SIOC: Social Media Ontology
    private static final String SCHEMA_NS = "http://schema.org/"; // Schema.org for social media
    
    /**
     * RabbitMQ listener for social media posts
     * Converts incoming posts to RDF and stores in Fuseki
     */
    @RabbitListener(queues = RabbitMQConfig.SOCIAL_MEDIA_QUEUE)
    public void processSocialMediaPost(Object message) {
        try {
            log.debug("Received social media post message for RDF conversion");
            
            // Convert message to SocialMediaPost DTO
            SocialMediaPost post = convertToSocialMediaPost(message);
            
            if (post == null) {
                log.warn("Could not convert message to SocialMediaPost, skipping RDF conversion");
                return;
            }
            
            // Convert to RDF and upload to Fuseki
            convertAndStorePost(post);
            
            log.debug("Successfully converted social media post {} to RDF", post.getPostId());
            
        } catch (Exception e) {
            log.error("Error processing social media post for RDF conversion", e);
        }
    }
    
    /**
     * Convert social media post to RDF and store in Fuseki
     */
    public void convertAndStorePost(SocialMediaPost post) {
        try {
            // Create Jena Model
            Model model = ModelFactory.createDefaultModel();
            
            // Set namespaces
            model.setNsPrefix("cruise", namespace);
            model.setNsPrefix("skos", SKOS_NS);
            model.setNsPrefix("rdf", RDF_NS);
            model.setNsPrefix("rdfs", RDFS_NS);
            model.setNsPrefix("geo", GEO_NS);
            model.setNsPrefix("dcterms", DCTERMS_NS);
            model.setNsPrefix("sioc", SIOC_NS);
            model.setNsPrefix("schema", SCHEMA_NS);
            
            // Create RDF resource for the post
            createPostRdfResource(model, post);
            
            // Upload to Fuseki
            uploadModelToFuseki(model);
            
            log.info("Successfully stored social media post {} in RDF dataset", post.getPostId());
            
        } catch (Exception e) {
            log.error("Error converting social media post to RDF", e);
            throw new RuntimeException("Failed to convert social media post to RDF", e);
        }
    }
    
    /**
     * Create RDF dataset from all social media posts in database
     * Useful for initial dataset creation or full refresh
     */
    public void createSocialMediaRdfDataset(List<SocialMediaPost> posts) {
        log.info("Creating RDF dataset for {} social media posts", posts.size());
        
        try {
            testFusekiConnectivity();
            
            if (posts.isEmpty()) {
                log.warn("No social media posts provided. Cannot create RDF dataset.");
                return;
            }
            
            // Create Jena Model
            Model model = ModelFactory.createDefaultModel();
            
            // Set namespaces
            model.setNsPrefix("cruise", namespace);
            model.setNsPrefix("skos", SKOS_NS);
            model.setNsPrefix("rdf", RDF_NS);
            model.setNsPrefix("rdfs", RDFS_NS);
            model.setNsPrefix("geo", GEO_NS);
            model.setNsPrefix("dcterms", DCTERMS_NS);
            model.setNsPrefix("sioc", SIOC_NS);
            model.setNsPrefix("schema", SCHEMA_NS);
            
            // Load SKOS vocabulary (optional)
            loadSkosVocabulary(model);
            
            // Create RDF resources for each post
            for (SocialMediaPost post : posts) {
                createPostRdfResource(model, post);
            }
            
            // Upload to Fuseki
            uploadModelToFuseki(model);
            
            log.info("Successfully created RDF dataset with {} social media posts", posts.size());
            
        } catch (Exception e) {
            log.error("Error creating social media RDF dataset", e);
            throw new RuntimeException("Failed to create social media RDF dataset", e);
        }
    }
    
    /**
     * Create RDF resource for a social media post
     */
    private void createPostRdfResource(Model model, SocialMediaPost post) {
        // Sanitize post ID for URI
        String sanitizedPostId = sanitizeForUri(post.getPostId());
        
        // Create resource URI for the post
        Resource postResource = model.createResource(namespace + "post/" + sanitizedPostId);
        
        // Add RDF type (using SIOC Post and Schema.org SocialMediaPosting)
        postResource.addProperty(model.createProperty(RDF_NS + "type"), 
                                model.createResource(SIOC_NS + "Post"));
        postResource.addProperty(model.createProperty(RDF_NS + "type"), 
                                model.createResource(SCHEMA_NS + "SocialMediaPosting"));
        
        // Platform-specific type
        String platformType = namespace + "Post/" + post.getPlatform().toUpperCase();
        postResource.addProperty(model.createProperty(RDF_NS + "type"), 
                                model.createResource(platformType));
        
        // Basic properties
        postResource.addProperty(model.createProperty(SIOC_NS + "id"), 
                                post.getPostId());
        postResource.addProperty(model.createProperty(DCTERMS_NS + "identifier"), 
                                post.getPostId());
        
        // Content
        if (post.getContent() != null && !post.getContent().isEmpty()) {
            postResource.addProperty(model.createProperty(SIOC_NS + "content"), 
                                    post.getContent());
            postResource.addProperty(model.createProperty(SCHEMA_NS + "text"), 
                                    post.getContent());
        }
        
        // Author
        if (post.getAuthorId() != null) {
            Resource authorResource = model.createResource(namespace + "author/" + sanitizeForUri(post.getAuthorId()));
            authorResource.addProperty(model.createProperty(RDF_NS + "type"), 
                                     model.createResource(SIOC_NS + "UserAccount"));
            authorResource.addProperty(model.createProperty(SIOC_NS + "id"), 
                                     post.getAuthorId());
            postResource.addProperty(model.createProperty(SIOC_NS + "has_creator"), 
                                    authorResource);
            postResource.addProperty(model.createProperty(SCHEMA_NS + "author"), 
                                    authorResource);
        }
        
        // Platform
        if (post.getPlatform() != null) {
            postResource.addProperty(model.createProperty(namespace + "hasPlatform"), 
                                    post.getPlatform().toUpperCase());
            Resource platformResource = model.createResource(namespace + "platform/" + post.getPlatform().toLowerCase());
            platformResource.addProperty(model.createProperty(RDF_NS + "type"), 
                                        model.createResource(namespace + "SocialMediaPlatform"));
            platformResource.addProperty(model.createProperty(SKOS_NS + "prefLabel"), 
                                        post.getPlatform(), "en");
            postResource.addProperty(model.createProperty(namespace + "publishedOn"), 
                                    platformResource);
        }
        
        // Timestamp
        if (post.getTimestamp() != null) {
            String timestampStr = post.getTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            String dateTimeTypeUri = "http://www.w3.org/2001/XMLSchema#dateTime";
            postResource.addProperty(model.createProperty(DCTERMS_NS + "created"), 
                                    model.createTypedLiteral(timestampStr, dateTimeTypeUri));
            postResource.addProperty(model.createProperty(SCHEMA_NS + "datePublished"), 
                                    model.createTypedLiteral(timestampStr, dateTimeTypeUri));
        }
        
        // Location
        if (post.getLocation() != null && !post.getLocation().isEmpty()) {
            Resource locationResource = model.createResource(namespace + "location/" + sanitizeForUri(post.getLocation()));
            locationResource.addProperty(model.createProperty(RDF_NS + "type"), 
                                       model.createResource(SCHEMA_NS + "Place"));
            locationResource.addProperty(model.createProperty(SCHEMA_NS + "name"), 
                                       post.getLocation());
            postResource.addProperty(model.createProperty(SCHEMA_NS + "contentLocation"), 
                                    locationResource);
            postResource.addProperty(model.createProperty(SIOC_NS + "has_location"), 
                                    locationResource);
            
            // Try to link to port if location matches
            linkToPortIfMatch(model, postResource, post.getLocation());
        }
        
        // Engagement metrics
        if (post.getLikes() != null) {
            postResource.addProperty(model.createProperty(namespace + "likesCount"), 
                                    model.createTypedLiteral(post.getLikes()));
        }
        if (post.getShares() != null) {
            postResource.addProperty(model.createProperty(namespace + "sharesCount"), 
                                    model.createTypedLiteral(post.getShares()));
        }
        if (post.getComments() != null) {
            postResource.addProperty(model.createProperty(namespace + "commentsCount"), 
                                    model.createTypedLiteral(post.getComments()));
        }
        if (post.getRetweets() != null) {
            postResource.addProperty(model.createProperty(namespace + "retweetsCount"), 
                                    model.createTypedLiteral(post.getRetweets()));
        }
        
        // Hashtags as SKOS concepts
        if (post.getHashtags() != null && !post.getHashtags().isEmpty()) {
            for (String hashtag : post.getHashtags()) {
                Resource hashtagConcept = model.createResource(namespace + "hashtag/" + sanitizeForUri(hashtag.toLowerCase()));
                hashtagConcept.addProperty(model.createProperty(RDF_NS + "type"), 
                                         model.createResource(SKOS_NS + "Concept"));
                hashtagConcept.addProperty(model.createProperty(SKOS_NS + "prefLabel"), 
                                         hashtag, "en");
                hashtagConcept.addProperty(model.createProperty(SKOS_NS + "broader"), 
                                         model.createResource(namespace + "concept/hashtag"));
                postResource.addProperty(model.createProperty(SKOS_NS + "related"), 
                                       hashtagConcept);
                postResource.addProperty(model.createProperty(namespace + "hasHashtag"), 
                                       hashtagConcept);
            }
        }
        
        // Keywords as SKOS concepts
        if (post.getKeywords() != null && !post.getKeywords().isEmpty()) {
            for (String keyword : post.getKeywords()) {
                Resource keywordConcept = model.createResource(namespace + "keyword/" + sanitizeForUri(keyword.toLowerCase()));
                keywordConcept.addProperty(model.createProperty(RDF_NS + "type"), 
                                        model.createResource(SKOS_NS + "Concept"));
                keywordConcept.addProperty(model.createProperty(SKOS_NS + "prefLabel"), 
                                         keyword, "en");
                keywordConcept.addProperty(model.createProperty(SKOS_NS + "broader"), 
                                         model.createResource(namespace + "concept/keyword"));
                postResource.addProperty(model.createProperty(SKOS_NS + "related"), 
                                       keywordConcept);
                postResource.addProperty(model.createProperty(namespace + "hasKeyword"), 
                                       keywordConcept);
                
                // Link keywords to port activities/interests if they match
                linkKeywordToPortFeatures(model, postResource, keyword);
            }
        }
        
        // Media URL (for Instagram, etc.)
        if (post.getMediaUrl() != null && !post.getMediaUrl().isEmpty()) {
            Resource mediaResource = model.createResource(post.getMediaUrl());
            mediaResource.addProperty(model.createProperty(RDF_NS + "type"), 
                                    model.createResource(SCHEMA_NS + "MediaObject"));
            if (post.getMediaType() != null) {
                mediaResource.addProperty(model.createProperty(SCHEMA_NS + "encodingFormat"), 
                                        post.getMediaType());
            }
            postResource.addProperty(model.createProperty(SCHEMA_NS + "image"), 
                                    mediaResource);
        }
    }
    
    /**
     * Link post to port if location matches
     */
    private void linkToPortIfMatch(Model model, Resource postResource, String location) {
        // Query for ports matching this location
        // This is a simplified matching - in production, use geocoding or fuzzy matching
        String locationLower = location.toLowerCase();
        
        // Common port city patterns
        if (locationLower.contains("barcelona")) {
            postResource.addProperty(model.createProperty(namespace + "mentionsPort"), 
                                   model.createResource(namespace + "port/BARCELONA"));
        } else if (locationLower.contains("miami")) {
            postResource.addProperty(model.createProperty(namespace + "mentionsPort"), 
                                   model.createResource(namespace + "port/MIAMI"));
        } else if (locationLower.contains("venice")) {
            postResource.addProperty(model.createProperty(namespace + "mentionsPort"), 
                                   model.createResource(namespace + "port/VENICE"));
        }
        // Add more location matching logic as needed
    }
    
    /**
     * Link keyword to port features (activities, interests, etc.)
     */
    private void linkKeywordToPortFeatures(Model model, Resource postResource, String keyword) {
        String keywordLower = keyword.toLowerCase();
        
        // Map keywords to port activities/interests
        // This creates semantic links between social media content and port features
        Map<String, String> keywordToConcept = new HashMap<>();
        keywordToConcept.put("snorkeling", "activity/snorkeling");
        keywordToConcept.put("diving", "activity/diving");
        keywordToConcept.put("art", "interest/art");
        keywordToConcept.put("food", "cuisine/food");
        keywordToConcept.put("restaurant", "cuisine/restaurant");
        keywordToConcept.put("shopping", "activity/shopping");
        keywordToConcept.put("beach", "activity/beach");
        keywordToConcept.put("culture", "interest/culture");
        keywordToConcept.put("excursion", "excursion/shore_excursion");
        keywordToConcept.put("cruise", "interest/cruise");
        keywordToConcept.put("port", "interest/port");
        
        String conceptPath = keywordToConcept.get(keywordLower);
        if (conceptPath != null) {
            Resource conceptResource = model.createResource(namespace + "concept/" + conceptPath);
            conceptResource.addProperty(model.createProperty(RDF_NS + "type"), 
                                      model.createResource(SKOS_NS + "Concept"));
            conceptResource.addProperty(model.createProperty(SKOS_NS + "prefLabel"), 
                                      keyword, "en");
            postResource.addProperty(model.createProperty(namespace + "relatesToPortFeature"), 
                                   conceptResource);
        }
    }
    
    /**
     * Load SKOS vocabulary from W3.org
     */
    private void loadSkosVocabulary(Model model) {
        log.debug("Loading SKOS vocabulary from W3.org");
        try {
            System.setProperty("javax.xml.accessExternalDTD", "all");
            String skosUrl = "https://www.w3.org/2004/02/skos/core";
            RDFDataMgr.read(model, skosUrl, Lang.RDFXML);
            log.debug("Successfully loaded SKOS vocabulary");
        } catch (Exception e) {
            log.debug("Could not load SKOS vocabulary (optional): {}", e.getMessage());
        }
    }
    
    /**
     * Upload RDF model to Fuseki
     */
    private void uploadModelToFuseki(Model model) {
        try {
            // Convert model to Turtle format
            StringWriter writer = new StringWriter();
            RDFDataMgr.write(writer, model, Lang.TURTLE);
            String turtleData = writer.toString();
            
            // Remove prefix declarations (INSERT DATA doesn't support them)
            turtleData = removePrefixesAndBase(turtleData);
            
            // Build SPARQL UPDATE query
            String updateQuery = "INSERT DATA {\n" + turtleData + "\n}";
            
            // Execute update
            UpdateRequest updateRequest = UpdateFactory.create(updateQuery);
            String authUrl = buildAuthenticatedEndpointUrl(updateEndpoint);
            
            UpdateExecutionHTTP.service(authUrl)
                .update(updateRequest)
                .execute();
            
            log.debug("Successfully uploaded RDF model to Fuseki");
            
        } catch (Exception e) {
            log.error("Error uploading RDF model to Fuseki", e);
            throw new RuntimeException("Failed to upload RDF model to Fuseki", e);
        }
    }
    
    /**
     * Test Fuseki connectivity
     */
    private void testFusekiConnectivity() {
        try {
            String testQuery = "ASK { ?s ?p ?o }";
            Query query = QueryFactory.create(testQuery);
            String authUrl = buildAuthenticatedEndpointUrl(sparqlEndpoint);
            
            try (QueryExecution qexec = QueryExecutionHTTP.service(authUrl)
                    .query(query)
                    .build()) {
                boolean result = qexec.execAsk();
                
                if (!result) {
                    log.warn("Fuseki connectivity test returned false - dataset may be empty");
                } else {
                    log.debug("Fuseki connectivity test successful");
                }
            }
        } catch (Exception e) {
            log.error("Fuseki connectivity test failed", e);
            throw new RuntimeException("Cannot connect to Fuseki SPARQL endpoint", e);
        }
    }
    
    /**
     * Convert RabbitMQ message to SocialMediaPost DTO
     */
    private SocialMediaPost convertToSocialMediaPost(Object message) {
        try {
            if (message instanceof SocialMediaPost) {
                return (SocialMediaPost) message;
            }
            
            // Handle JSON deserialization if needed
            if (message instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) message;
                
                return SocialMediaPost.builder()
                    .platform((String) map.get("platform"))
                    .postId((String) map.get("postId"))
                    .authorId((String) map.get("authorId"))
                    .content((String) map.get("content"))
                    .location((String) map.get("location"))
                    .timestamp(parseTimestamp(map.get("timestamp")))
                    .likes(getInteger(map.get("likes")))
                    .shares(getInteger(map.get("shares")))
                    .comments(getInteger(map.get("comments")))
                    .retweets(getInteger(map.get("retweets")))
                    .hashtags(getStringList(map.get("hashtags")))
                    .keywords(getStringList(map.get("keywords")))
                    .mediaUrl((String) map.get("mediaUrl"))
                    .mediaType((String) map.get("mediaType"))
                    .build();
            }
            
            log.warn("Unknown message type: {}", message.getClass().getName());
            return null;
            
        } catch (Exception e) {
            log.error("Error converting message to SocialMediaPost", e);
            return null;
        }
    }
    
    private LocalDateTime parseTimestamp(Object timestamp) {
        if (timestamp == null) return null;
        if (timestamp instanceof LocalDateTime) return (LocalDateTime) timestamp;
        if (timestamp instanceof String) {
            try {
                return LocalDateTime.parse((String) timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception e) {
                log.debug("Could not parse timestamp: {}", timestamp);
            }
        }
        return null;
    }
    
    private Integer getInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        return null;
    }
    
    @SuppressWarnings("unchecked")
    private List<String> getStringList(Object value) {
        if (value == null) return new ArrayList<>();
        if (value instanceof List) return (List<String>) value;
        return new ArrayList<>();
    }
    
    /**
     * Remove @prefix and @base declarations from Turtle data
     */
    private String removePrefixesAndBase(String turtleData) {
        String[] lines = turtleData.split("\n");
        StringBuilder cleaned = new StringBuilder();
        
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("@prefix") && 
                !trimmed.startsWith("PREFIX") && 
                !trimmed.startsWith("@base") &&
                !trimmed.startsWith("BASE") &&
                !trimmed.isEmpty()) {
                cleaned.append(line).append("\n");
            }
        }
        
        return cleaned.toString();
    }
    
    /**
     * Build authenticated endpoint URL
     */
    private String buildAuthenticatedEndpointUrl(String endpoint) {
        String username = (fusekiUsername == null || fusekiUsername.trim().isEmpty()) ? "admin" : fusekiUsername;
        String password = (fusekiPassword == null || fusekiPassword.trim().isEmpty()) ? "admin" : fusekiPassword;
        
        if ((fusekiUsername == null || fusekiUsername.trim().isEmpty()) || 
            (fusekiPassword == null || fusekiPassword.trim().isEmpty())) {
            log.warn("Fuseki credentials not configured, using default admin:admin for development.");
        }
        
        try {
            java.net.URL url = new java.net.URL(endpoint);
            String userInfo = username + ":" + password;
            int port = url.getPort() == -1 ? url.getDefaultPort() : url.getPort();
            
            String authUrl = url.getProtocol() + "://" + userInfo + "@" + url.getHost();
            if (port != url.getDefaultPort()) {
                authUrl += ":" + port;
            }
            authUrl += url.getPath();
            if (url.getQuery() != null) {
                authUrl += "?" + url.getQuery();
            }
            
            return authUrl;
        } catch (Exception e) {
            log.error("Error building authenticated endpoint URL", e);
            return endpoint;
        }
    }
    
    /**
     * Sanitize string for use in URI
     */
    private String sanitizeForUri(String str) {
        if (str == null) {
            return "";
        }
        
        String sanitized = str.trim().replaceAll("\\s+", "_");
        
        try {
            sanitized = java.net.URLEncoder.encode(sanitized, "UTF-8")
                .replace("+", "_")
                .replace("%20", "_");
        } catch (Exception e) {
            sanitized = sanitized.replaceAll("[^a-zA-Z0-9._-]", "_");
        }
        
        return sanitized;
    }
    
    /**
     * DTO for social media posts (matches SocialMediaIngestionService.SocialMediaPost)
     */
    @lombok.Data
    @lombok.Builder
    public static class SocialMediaPost {
        private String platform; // FACEBOOK, TWITTER, INSTAGRAM
        private String postId;
        private String authorId;
        private String content;
        private String location;
        private LocalDateTime timestamp;
        private Integer likes;
        private Integer shares;
        private Integer comments;
        private Integer retweets;
        private List<String> hashtags;
        private List<String> keywords;
        private String mediaUrl;
        private String mediaType;
    }
}

