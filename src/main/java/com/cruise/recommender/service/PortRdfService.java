package com.cruise.recommender.service;

import com.cruise.recommender.entity.Port;
import com.cruise.recommender.repository.PortRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.exec.http.UpdateExecutionHTTP;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.util.List;

/**
 * Service for creating and managing RDF datasets for ports using Apache Jena
 * Integrates with Fuseki SPARQL server and uses SKOS vocabulary
 * 
 * Based on: https://jena.apache.org/tutorials/rdf_api.html
 * SKOS: https://www.w3.org/2004/02/skos/
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortRdfService {
    
    private final PortRepository portRepository;
    
    // Fuseki SPARQL endpoints
    @Value("${knowledge.graph.endpoint:http://localhost:3030/cruise_kg/sparql}")
    private String sparqlEndpoint;
    
    @Value("${knowledge.graph.update-endpoint:http://localhost:3030/cruise_kg/update}")
    private String updateEndpoint;
    
    @Value("${knowledge.graph.namespace:http://cruise.recommender.org/kg/}")
    private String namespace;
    
    @Value("${knowledge.graph.username:admin}")
    private String fusekiUsername;
    
    @Value("${knowledge.graph.password:admin}")
    private String fusekiPassword;
    
    // SKOS namespace
    private static final String SKOS_NS = "http://www.w3.org/2004/02/skos/core#";
    private static final String RDF_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    private static final String RDFS_NS = "http://www.w3.org/2000/01/rdf-schema#";
    private static final String GEO_NS = "http://www.w3.org/2003/01/geo/wgs84_pos#";
    private static final String DCTERMS_NS = "http://purl.org/dc/terms/";
    
    /**
     * Load SKOS vocabulary from W3.org and add to model
     * This enriches the RDF dataset with standard SKOS concepts
     */
    public void loadSkosVocabulary(Model model) {
        log.info("Loading SKOS vocabulary from W3.org");
        
        try {
            // Suppress StAX property warnings by setting system property before loading
            // These warnings occur in newer Java versions but don't affect functionality
            System.setProperty("javax.xml.accessExternalDTD", "all");
            
            // Load SKOS core vocabulary
            String skosUrl = "https://www.w3.org/2004/02/skos/core";
            RDFDataMgr.read(model, skosUrl, Lang.RDFXML);
            
            log.info("Successfully loaded SKOS vocabulary");
        } catch (Exception e) {
            log.debug("Could not load SKOS vocabulary from W3.org (this is optional): {}", e.getMessage());
            // Continue without SKOS vocabulary - it's optional
            // The SKOS namespace is already defined, so we can still use SKOS properties
        }
    }
    
    /**
     * Create RDF dataset for all ports and upload to Fuseki
     */
    public void createPortsRdfDataset() {
        log.info("Creating RDF dataset for ports from database");
        
        try {
            // Test Fuseki connectivity first
            testFusekiConnectivity();
            
            // Get all ports from database
            List<Port> ports = portRepository.findAll();
            log.info("Found {} ports to convert to RDF", ports.size());
            
            if (ports.isEmpty()) {
                log.warn("No ports found in database. Cannot create RDF dataset.");
                throw new RuntimeException("No ports found in database");
            }
            
            // Create Jena Model (in-memory RDF graph)
            Model model = ModelFactory.createDefaultModel();
            
            // Set namespaces for cleaner output
            model.setNsPrefix("cruise", namespace);
            model.setNsPrefix("skos", SKOS_NS);
            model.setNsPrefix("rdf", RDF_NS);
            model.setNsPrefix("rdfs", RDFS_NS);
            model.setNsPrefix("geo", GEO_NS);
            model.setNsPrefix("dcterms", DCTERMS_NS);
            
            // Load SKOS vocabulary from W3.org (optional - enriches the dataset)
            loadSkosVocabulary(model);
            
            // Create RDF resources for each port
            for (Port port : ports) {
                createPortRdfResource(model, port);
            }
            
            // Upload to Fuseki
            uploadModelToFuseki(model);
            
            log.info("Successfully created RDF dataset with {} ports", ports.size());
            
        } catch (Exception e) {
            log.error("Error creating ports RDF dataset", e);
            throw new RuntimeException("Failed to create ports RDF dataset", e);
        }
    }
    
    /**
     * Create RDF resource for a single port
     * Following Jena RDF API patterns: https://jena.apache.org/tutorials/rdf_api.html
     */
    private void createPortRdfResource(Model model, Port port) {
        // Sanitize port code for URI (remove spaces, encode special characters)
        String sanitizedPortCode = sanitizeForUri(port.getPortCode());
        
        // Create resource URI for the port
        Resource portResource = model.createResource(namespace + "port/" + sanitizedPortCode);
        
        // Add RDF type
        portResource.addProperty(model.createProperty(RDF_NS + "type"), 
                                model.createResource(namespace + "Port"));
        
        // Basic properties using SKOS and Dublin Core
        portResource.addProperty(model.createProperty(SKOS_NS + "prefLabel"), 
                                port.getName(), "en");
        portResource.addProperty(model.createProperty(SKOS_NS + "altLabel"), 
                                port.getPortCode());
        portResource.addProperty(model.createProperty(DCTERMS_NS + "identifier"), 
                                port.getPortCode());
        
        // Location properties
        if (port.getCity() != null) {
            portResource.addProperty(model.createProperty(DCTERMS_NS + "spatial"), 
                                    port.getCity());
        }
        if (port.getCountry() != null) {
            portResource.addProperty(model.createProperty(DCTERMS_NS + "coverage"), 
                                    port.getCountry());
        }
        if (port.getRegion() != null) {
            portResource.addProperty(model.createProperty(namespace + "region"), 
                                    port.getRegion());
        }
        
        // Geographic coordinates using WGS84 Geo vocabulary
        if (port.getLatitude() != null && port.getLongitude() != null) {
            Resource location = model.createResource();
            location.addProperty(model.createProperty(GEO_NS + "lat"), 
                               model.createTypedLiteral(port.getLatitude()));
            location.addProperty(model.createProperty(GEO_NS + "long"), 
                               model.createTypedLiteral(port.getLongitude()));
            portResource.addProperty(model.createProperty(GEO_NS + "location"), location);
        }
        
        // Port-specific properties
        if (port.getBerthsCapacity() != null) {
            portResource.addProperty(model.createProperty(namespace + "berthsCapacity"), 
                                   model.createTypedLiteral(port.getBerthsCapacity()));
        }
        
        if (port.getDockingFees() != null) {
            Resource fee = model.createResource();
            fee.addProperty(model.createProperty(DCTERMS_NS + "price"), 
                          model.createTypedLiteral(port.getDockingFees().doubleValue()));
            if (port.getCurrency() != null) {
                fee.addProperty(model.createProperty(DCTERMS_NS + "currency"), 
                              port.getCurrency());
            }
            portResource.addProperty(model.createProperty(namespace + "dockingFee"), fee);
        }
        
        // Tourism and interests using SKOS concepts
        if (port.getTourism1() != null) {
            Resource tourismConcept = model.createResource(namespace + "concept/tourism/" + 
                                                          sanitizeForUri(port.getPortCode()).toLowerCase());
            tourismConcept.addProperty(model.createProperty(RDF_NS + "type"), 
                                      model.createResource(SKOS_NS + "Concept"));
            tourismConcept.addProperty(model.createProperty(SKOS_NS + "prefLabel"), 
                                      port.getTourism1(), "en");
            tourismConcept.addProperty(model.createProperty(SKOS_NS + "broader"), 
                                      model.createResource(namespace + "concept/tourism"));
            portResource.addProperty(model.createProperty(SKOS_NS + "related"), tourismConcept);
        }
        
        // Activities as SKOS concepts
        if (port.getActivityKeywords() != null) {
            addJsonArrayAsSkosConcepts(model, portResource, port.getActivityKeywords(), 
                                      "activity", namespace + "concept/activity");
        }
        
        // Restaurants/Cuisines as SKOS concepts
        if (port.getRestaurantKeywords() != null) {
            addJsonArrayAsSkosConcepts(model, portResource, port.getRestaurantKeywords(), 
                                      "cuisine", namespace + "concept/cuisine");
        }
        
        // Excursions as SKOS concepts
        if (port.getExcursions() != null) {
            addJsonArrayAsSkosConcepts(model, portResource, port.getExcursions(), 
                                      "excursion", namespace + "concept/excursion");
        }
        
        // General interests as SKOS concepts
        if (port.getGeneralInterests() != null) {
            addJsonArrayAsSkosConcepts(model, portResource, port.getGeneralInterests(), 
                                      "interest", namespace + "concept/interest");
        }
        
        // Foodie information
        if (port.getFoodieMain() != null) {
            Resource foodieConcept = model.createResource(namespace + "concept/foodie/" + 
                                                         sanitizeForUri(port.getPortCode()).toLowerCase());
            foodieConcept.addProperty(model.createProperty(RDF_NS + "type"), 
                                    model.createResource(SKOS_NS + "Concept"));
            foodieConcept.addProperty(model.createProperty(SKOS_NS + "prefLabel"), 
                                    "Foodie Destination", "en");
            foodieConcept.addProperty(model.createProperty(SKOS_NS + "note"), 
                                    port.getFoodieMain(), "en");
            portResource.addProperty(model.createProperty(SKOS_NS + "related"), foodieConcept);
        }
        
        // Timestamps
        if (port.getCreatedAt() != null) {
            portResource.addProperty(model.createProperty(DCTERMS_NS + "created"), 
                                    model.createTypedLiteral(port.getCreatedAt().toString()));
        }
        if (port.getUpdatedAt() != null) {
            portResource.addProperty(model.createProperty(DCTERMS_NS + "modified"), 
                                    model.createTypedLiteral(port.getUpdatedAt().toString()));
        }
    }
    
    /**
     * Add JSON array values as SKOS concepts
     */
    private void addJsonArrayAsSkosConcepts(Model model, Resource portResource, 
                                           String jsonArray, String type, String conceptNamespace) {
        try {
            // Simple JSON array parsing (assuming format: ["item1", "item2", ...])
            String cleaned = jsonArray.trim();
            if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
                cleaned = cleaned.substring(1, cleaned.length() - 1);
                String[] items = cleaned.split(",");
                
                for (String item : items) {
                    String cleanedItem = item.trim().replaceAll("^\"|\"$", "");
                    if (!cleanedItem.isEmpty()) {
                        Resource concept = model.createResource(conceptNamespace + "/" + 
                                                               cleanedItem.toLowerCase().replaceAll("\\s+", "_"));
                        concept.addProperty(model.createProperty(RDF_NS + "type"), 
                                          model.createResource(SKOS_NS + "Concept"));
                        concept.addProperty(model.createProperty(SKOS_NS + "prefLabel"), 
                                          cleanedItem, "en");
                        concept.addProperty(model.createProperty(SKOS_NS + "broader"), 
                                          model.createResource(conceptNamespace));
                        portResource.addProperty(model.createProperty(SKOS_NS + "related"), concept);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse JSON array for SKOS concepts: {}", jsonArray, e);
        }
    }
    
    /**
     * Upload RDF model to Fuseki using SPARQL Update
     * Following Jena Graph API patterns: https://jena.apache.org/tutorials/rdf_api.html
     */
    private void uploadModelToFuseki(Model model) {
        log.info("Uploading RDF model to Fuseki at {}", updateEndpoint);
        
        try {
            // Clear existing data first (optional - uncomment if you want to replace all data)
            // String clearQuery = "DELETE WHERE { ?s ?p ?o }";
            // UpdateRequest clearRequest = UpdateFactory.create(clearQuery);
            // UpdateExecutionHTTPBuilder.newBuilder()
            //     .endpoint(updateEndpoint)
            //     .httpClient(createHttpClient())
            //     .build()
            //     .execute(clearRequest);
            
            // Upload model using Jena's built-in methods
            // Use N-Triples format which doesn't use prefixes (better for INSERT DATA)
            int totalTriples = (int) model.size();
            log.info("Uploading {} triples to Fuseki", totalTriples);
            
            // Process in batches to avoid large queries
            int batchSize = 500;
            StmtIterator statements = model.listStatements();
            Model batchModel = ModelFactory.createDefaultModel();
            int count = 0;
            
            while (statements.hasNext()) {
                Statement stmt = statements.nextStatement();
                batchModel.add(stmt);
                count++;
                
                if (batchModel.size() >= batchSize) {
                    insertModelBatch(batchModel);
                    batchModel = ModelFactory.createDefaultModel();
                }
            }
            
            // Insert remaining triples
            if (!batchModel.isEmpty()) {
                insertModelBatch(batchModel);
            }
            
            log.info("Successfully uploaded {} RDF triples to Fuseki", count);
            
        } catch (Exception e) {
            log.error("Error uploading model to Fuseki. Endpoint: {}", updateEndpoint, e);
            String errorDetails = e.getMessage();
            if (e.getCause() != null) {
                errorDetails += " - Cause: " + e.getCause().getMessage();
            }
            throw new RuntimeException("Failed to upload RDF model to Fuseki: " + errorDetails, e);
        }
    }
    
    /**
     * Format a statement as a Turtle triple
     */
    private String formatTriple(Statement stmt) {
        Resource subject = stmt.getSubject();
        Property predicate = stmt.getPredicate();
        RDFNode object = stmt.getObject();
        
        StringBuilder sb = new StringBuilder();
        sb.append(formatResource(subject));
        sb.append(" ");
        sb.append(formatResource(predicate));
        sb.append(" ");
        sb.append(formatNode(object));
        
        return sb.toString();
    }
    
    /**
     * Format a resource for Turtle output
     */
    private String formatResource(Resource resource) {
        if (resource.isURIResource()) {
            return "<" + resource.getURI() + ">";
        } else {
            return "_:" + resource.getId().getLabelString();
        }
    }
    
    /**
     * Format an RDF node for Turtle output
     */
    private String formatNode(RDFNode node) {
        if (node.isURIResource()) {
            return "<" + node.asResource().getURI() + ">";
        } else if (node.isLiteral()) {
            Literal literal = node.asLiteral();
            String value = literal.getLexicalForm();
            String datatype = literal.getDatatypeURI();
            String lang = literal.getLanguage();
            
            if (lang != null && !lang.isEmpty()) {
                return "\"" + escapeString(value) + "\"@" + lang;
            } else if (datatype != null && !datatype.equals("http://www.w3.org/2001/XMLSchema#string")) {
                return "\"" + escapeString(value) + "\"^^<" + datatype + ">";
            } else {
                return "\"" + escapeString(value) + "\"";
            }
        } else {
            return "_:" + node.asResource().getId().getLabelString();
        }
    }
    
    /**
     * Escape special characters in strings
     */
    private String escapeString(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    /**
     * Sanitize string for use in URI
     * Removes leading/trailing whitespace and URL-encodes special characters
     */
    private String sanitizeForUri(String str) {
        if (str == null) {
            return "";
        }
        
        // Trim whitespace
        String sanitized = str.trim();
        
        // Replace spaces with underscores (or URL encode them)
        sanitized = sanitized.replaceAll("\\s+", "_");
        
        // URL encode special characters that aren't safe in URIs
        try {
            sanitized = java.net.URLEncoder.encode(sanitized, "UTF-8")
                .replace("+", "_")  // Replace + with _ for readability
                .replace("%20", "_"); // Replace encoded spaces with _
        } catch (Exception e) {
            log.warn("Could not URL encode string: {}", str, e);
            // Fallback: just remove/replace problematic characters
            sanitized = sanitized.replaceAll("[^a-zA-Z0-9._-]", "_");
        }
        
        return sanitized;
    }
    
    /**
     * Remove @prefix and @base declarations from Turtle data
     * INSERT DATA doesn't support these declarations
     */
    private String removePrefixesAndBase(String turtleData) {
        String[] lines = turtleData.split("\n");
        StringBuilder cleaned = new StringBuilder();
        
        for (String line : lines) {
            String trimmed = line.trim();
            // Skip prefix declarations, base declarations, and empty lines
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
     * Build authenticated endpoint URL with embedded credentials
     * Format: http://username:password@host:port/path
     */
    private String buildAuthenticatedEndpointUrl(String endpoint) {
        try {
            java.net.URL url = new java.net.URL(endpoint);
            String userInfo = fusekiUsername + ":" + fusekiPassword;
            int port = url.getPort() == -1 ? url.getDefaultPort() : url.getPort();
            
            // Build URL with embedded credentials
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
            log.warn("Could not build authenticated URL, using original endpoint: {}", e.getMessage());
            return endpoint;
        }
    }
    
    /**
     * Insert a batch of triples from a Model using N-Triples format
     * N-Triples doesn't use prefixes, making it perfect for INSERT DATA
     */
    private void insertModelBatch(Model batchModel) {
        if (batchModel == null || batchModel.isEmpty()) {
            log.warn("Skipping empty batch");
            return;
        }
        
        try {
            // Use N-Triples format - always uses full URIs, no prefixes
            // This is the safest format for INSERT DATA
            StringWriter writer = new StringWriter();
            RDFDataMgr.write(writer, batchModel, Lang.NTRIPLES);
            String ntriplesData = writer.toString();
            
            // N-Triples format: <subject> <predicate> <object> .\n
            // For INSERT DATA, we need to replace newlines with spaces
            // Each triple ends with " ." followed by newline
            String[] lines = ntriplesData.split("\r?\n");
            StringBuilder cleaned = new StringBuilder();
            
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (!line.isEmpty()) {
                    if (i > 0) {
                        cleaned.append(" ");
                    }
                    cleaned.append(line);
                }
            }
            
            String cleanedData = cleaned.toString().trim();
            
            // Build SPARQL INSERT DATA query
            String insertQuery = "INSERT DATA { " + cleanedData + " }";
            
            // Log a sample for debugging (first 200 and last 100 chars)
            if (log.isDebugEnabled()) {
                String sample = insertQuery.length() > 300 
                    ? insertQuery.substring(0, 200) + " ... " + insertQuery.substring(Math.max(0, insertQuery.length() - 100))
                    : insertQuery;
                log.debug("SPARQL query sample: {}", sample);
            }
            
            // Also log the actual error if it fails
            log.info("Prepared SPARQL INSERT DATA query with {} triples, {} characters", 
                batchModel.size(), insertQuery.length());
            
            log.debug("Executing SPARQL update with {} triples ({} characters)", batchModel.size(), insertQuery.length());
            
            UpdateRequest request = UpdateFactory.create(insertQuery);
            
            // Use Jena's HTTP update execution - Fuseki requires authentication
            log.debug("Executing SPARQL update with {} characters to {}", insertQuery.length(), updateEndpoint);
            
            // Build endpoint URL with embedded credentials for authentication
            // Format: http://username:password@host:port/path
            String authenticatedEndpoint = buildAuthenticatedEndpointUrl(updateEndpoint);
            
            // Use authenticated endpoint URL
            UpdateExecutionHTTP.service(authenticatedEndpoint)
                .update(request)
                .execute();
            
            log.debug("Successfully executed SPARQL update batch");
        } catch (Exception e) {
            log.error("Error executing SPARQL update. Endpoint: {}", updateEndpoint, e);
            
            // Log the actual query that failed (first 500 chars)
            try {
                StringWriter writer = new StringWriter();
                RDFDataMgr.write(writer, batchModel, Lang.NTRIPLES);
                String sampleQuery = "INSERT DATA { " + writer.toString().substring(0, Math.min(500, writer.toString().length())) + " }";
                log.error("Failed query sample (first 500 chars): {}", sampleQuery);
            } catch (Exception logError) {
                log.error("Could not log query sample", logError);
            }
            
            String errorMsg = e.getMessage();
            if (e.getCause() != null) {
                errorMsg += " - Root cause: " + e.getCause().getMessage();
            }
            throw new RuntimeException("Failed to execute SPARQL update: " + errorMsg, e);
        }
    }
    
    /**
     * Test Fuseki connectivity
     */
    private void testFusekiConnectivity() {
        try {
            HttpClient client = createAuthenticatedHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(sparqlEndpoint.replace("/sparql", "/$/ping")))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                log.info("Fuseki connectivity test passed");
            } else {
                log.warn("Fuseki connectivity test returned status: {}", response.statusCode());
            }
        } catch (Exception e) {
            log.warn("Fuseki connectivity test failed (will continue anyway): {}", e.getMessage());
        }
    }
    
    /**
     * Create HTTP client with authentication
     * Uses Java's built-in HttpClient (required by Jena)
     */
    private HttpClient createHttpClient() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }
    
    /**
     * Create authenticated HTTP client for Fuseki
     * Uses Java's HttpClient with Basic Authentication via Authenticator
     * Note: This may not work with all HTTP libraries, so we also try without auth as fallback
     */
    private HttpClient createAuthenticatedHttpClient() {
        // Create an authenticator for Basic Auth
        // The Authenticator will be called when the server requests authentication
        java.net.Authenticator authenticator = new java.net.Authenticator() {
            @Override
            protected java.net.PasswordAuthentication getPasswordAuthentication() {
                // Only provide credentials for Fuseki host
                String requestingHost = getRequestingHost();
                if (requestingHost != null && requestingHost.contains("localhost")) {
                    return new java.net.PasswordAuthentication(
                        fusekiUsername, 
                        fusekiPassword.toCharArray()
                    );
                }
                return null;
            }
        };
        
        return HttpClient.newBuilder()
            .authenticator(authenticator)
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }
    
    /**
     * Query ports from Fuseki using SPARQL
     */
    public List<QuerySolution> queryPorts(String sparqlQuery) {
        log.debug("Executing SPARQL query: {}", sparqlQuery);
        
        try {
            Query query = QueryFactory.create(sparqlQuery);
            try (QueryExecution qexec = QueryExecution.service(sparqlEndpoint)
                    .query(query)
                    .httpClient(createAuthenticatedHttpClient())
                    .build()) {
                
                ResultSet results = qexec.execSelect();
                return convertResultSetToList(results);
            }
        } catch (org.apache.jena.query.QueryParseException e) {
            log.error("SPARQL query parse error. Query (first 500 chars): {}", 
                     sparqlQuery.length() > 500 ? sparqlQuery.substring(0, 500) : sparqlQuery, e);
            throw new RuntimeException("Failed to parse SPARQL query: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error executing SPARQL query. Query (first 500 chars): {}", 
                     sparqlQuery.length() > 500 ? sparqlQuery.substring(0, 500) : sparqlQuery, e);
            String errorMsg = e.getMessage();
            if (e.getCause() != null) {
                errorMsg += " - Cause: " + e.getCause().getMessage();
            }
            throw new RuntimeException("Failed to execute SPARQL query: " + errorMsg, e);
        }
    }
    
    /**
     * Convert ResultSet to List of QuerySolution
     */
    private List<QuerySolution> convertResultSetToList(ResultSet resultSet) {
        List<QuerySolution> solutions = new java.util.ArrayList<>();
        while (resultSet.hasNext()) {
            solutions.add(resultSet.next());
        }
        return solutions;
    }
    
    /**
     * Find ports by country using SPARQL
     */
    public List<QuerySolution> findPortsByCountry(String country) {
        String query = String.format("""
            PREFIX cruise: <%s>
            PREFIX dcterms: <%s>
            PREFIX skos: <%s>
            
            SELECT ?port ?name ?city WHERE {
                ?port a cruise:Port .
                ?port skos:prefLabel ?name .
                ?port dcterms:spatial ?city .
                ?port dcterms:coverage "%s" .
            }
            ORDER BY ?name
            """, namespace, DCTERMS_NS, SKOS_NS, country);
        
        return queryPorts(query);
    }
    
    /**
     * Find ports by activity/interest using SKOS concepts
     */
    public List<QuerySolution> findPortsByActivity(String activity) {
        String query = String.format("""
            PREFIX cruise: <%s>
            PREFIX skos: <%s>
            PREFIX dcterms: <%s>
            
            SELECT DISTINCT ?port ?name WHERE {
                ?port a cruise:Port .
                ?port skos:prefLabel ?name .
                ?port skos:related ?concept .
                ?concept skos:prefLabel ?label .
                FILTER (CONTAINS(LCASE(?label), LCASE("%s")))
            }
            ORDER BY ?name
            """, namespace, SKOS_NS, DCTERMS_NS, activity);
        
        return queryPorts(query);
    }
    
    /**
     * Get port details with all related concepts
     */
    public QuerySolution getPortDetails(String portCode) {
        String query = String.format("""
            PREFIX cruise: <%s>
            PREFIX skos: <%s>
            PREFIX dcterms: <%s>
            PREFIX geo: <%s>
            
            SELECT ?port ?name ?city ?country ?lat ?long ?capacity WHERE {
                ?port a cruise:Port .
                ?port skos:prefLabel ?name .
                ?port skos:altLabel "%s" .
                OPTIONAL { ?port dcterms:spatial ?city }
                OPTIONAL { ?port dcterms:coverage ?country }
                OPTIONAL { 
                    ?port geo:location ?location .
                    ?location geo:lat ?lat .
                    ?location geo:long ?long .
                }
                OPTIONAL { ?port cruise:berthsCapacity ?capacity }
            }
            """, namespace, SKOS_NS, DCTERMS_NS, GEO_NS, portCode);
        
        List<QuerySolution> results = queryPorts(query);
        return results.isEmpty() ? null : results.get(0);
    }
}

