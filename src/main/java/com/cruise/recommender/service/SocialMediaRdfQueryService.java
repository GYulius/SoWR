package com.cruise.recommender.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.*;
import org.apache.jena.sparql.exec.http.QueryExecutionHTTP;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for querying social media RDF dataset with SPARQL
 * Provides methods for recommendation system queries
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SocialMediaRdfQueryService {
    
    @Value("${knowledge.graph.endpoint:http://localhost:3030/cruise_kg/sparql}")
    private String sparqlEndpoint;
    
    @Value("${knowledge.graph.namespace:http://cruise.recommender.org/kg/}")
    private String namespace;
    
    @Value("${knowledge.graph.username:}")
    private String fusekiUsername;
    
    @Value("${knowledge.graph.password:}")
    private String fusekiPassword;
    
    /**
     * Find posts mentioning a specific port
     */
    public List<Map<String, String>> findPostsByPort(String portCode) {
        String query = String.format("""
            PREFIX cruise: <%s>
            PREFIX sioc: <http://rdfs.org/sioc/ns#>
            PREFIX schema: <http://schema.org/>
            PREFIX dcterms: <http://purl.org/dc/terms/>
            
            SELECT ?post ?content ?author ?platform ?timestamp ?likes
            WHERE {
                ?post cruise:mentionsPort cruise:port/%s .
                ?post sioc:content ?content .
                ?post sioc:has_creator ?author .
                ?post cruise:hasPlatform ?platform .
                ?post dcterms:created ?timestamp .
                OPTIONAL { ?post cruise:likesCount ?likes }
            }
            ORDER BY DESC(?likes)
            LIMIT 50
            """, namespace, portCode.toUpperCase());
        
        return executeQuery(query);
    }
    
    /**
     * Find posts by keyword/interest
     */
    public List<Map<String, String>> findPostsByKeyword(String keyword) {
        String query = String.format("""
            PREFIX cruise: <%s>
            PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
            PREFIX sioc: <http://rdfs.org/sioc/ns#>
            
            SELECT ?post ?content ?author ?platform ?keyword ?likes
            WHERE {
                ?post cruise:hasKeyword ?keywordConcept .
                ?keywordConcept skos:prefLabel ?keyword .
                FILTER(LCASE(?keyword) = LCASE("%s"))
                ?post sioc:content ?content .
                ?post sioc:has_creator ?author .
                ?post cruise:hasPlatform ?platform .
                OPTIONAL { ?post cruise:likesCount ?likes }
            }
            ORDER BY DESC(?likes)
            LIMIT 50
            """, namespace, keyword);
        
        return executeQuery(query);
    }
    
    /**
     * Find posts by hashtag
     */
    public List<Map<String, String>> findPostsByHashtag(String hashtag) {
        String query = String.format("""
            PREFIX cruise: <%s>
            PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
            PREFIX sioc: <http://rdfs.org/sioc/ns#>
            
            SELECT ?post ?content ?author ?platform ?hashtag ?likes
            WHERE {
                ?post cruise:hasHashtag ?hashtagConcept .
                ?hashtagConcept skos:prefLabel ?hashtag .
                FILTER(LCASE(?hashtag) = LCASE("%s"))
                ?post sioc:content ?content .
                ?post sioc:has_creator ?author .
                ?post cruise:hasPlatform ?platform .
                OPTIONAL { ?post cruise:likesCount ?likes }
            }
            ORDER BY DESC(?likes)
            LIMIT 50
            """, namespace, hashtag);
        
        return executeQuery(query);
    }
    
    /**
     * Find popular interests from social media posts
     * Useful for discovering trending interests
     */
    public List<Map<String, String>> findPopularInterests(int limit) {
        String query = String.format("""
            PREFIX cruise: <%s>
            PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
            
            SELECT ?keyword (COUNT(?post) as ?postCount) (AVG(?likes) as ?avgLikes)
            WHERE {
                ?post cruise:hasKeyword ?keywordConcept .
                ?keywordConcept skos:prefLabel ?keyword .
                OPTIONAL { ?post cruise:likesCount ?likes }
            }
            GROUP BY ?keyword
            ORDER BY DESC(?postCount) DESC(?avgLikes)
            LIMIT %d
            """, namespace, limit);
        
        return executeQuery(query);
    }
    
    /**
     * Find ports mentioned in social media posts
     * Useful for discovering popular ports
     */
    public List<Map<String, String>> findPopularPorts(int limit) {
        String query = String.format("""
            PREFIX cruise: <%s>
            
            SELECT ?port (COUNT(?post) as ?mentionCount) (AVG(?likes) as ?avgLikes)
            WHERE {
                ?post cruise:mentionsPort ?port .
                OPTIONAL { ?post cruise:likesCount ?likes }
            }
            GROUP BY ?port
            ORDER BY DESC(?mentionCount) DESC(?avgLikes)
            LIMIT %d
            """, namespace, limit);
        
        return executeQuery(query);
    }
    
    /**
     * Find posts matching user interests
     * Used for recommendation system
     */
    public List<Map<String, String>> findPostsMatchingInterests(List<String> interests) {
        if (interests == null || interests.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Build filter for multiple interests
        StringBuilder filterBuilder = new StringBuilder();
        for (int i = 0; i < interests.size(); i++) {
            if (i > 0) filterBuilder.append(" || ");
            filterBuilder.append(String.format("LCASE(?keyword) = LCASE(\"%s\")", interests.get(i)));
        }
        
        String query = String.format("""
            PREFIX cruise: <%s>
            PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
            PREFIX sioc: <http://rdfs.org/sioc/ns#>
            
            SELECT DISTINCT ?post ?content ?author ?platform ?keyword ?likes ?port
            WHERE {
                ?post cruise:hasKeyword ?keywordConcept .
                ?keywordConcept skos:prefLabel ?keyword .
                FILTER(%s)
                ?post sioc:content ?content .
                ?post sioc:has_creator ?author .
                ?post cruise:hasPlatform ?platform .
                OPTIONAL { ?post cruise:likesCount ?likes }
                OPTIONAL { ?post cruise:mentionsPort ?port }
            }
            ORDER BY DESC(?likes)
            LIMIT 100
            """, namespace, filterBuilder.toString());
        
        return executeQuery(query);
    }
    
    /**
     * Find ports recommended based on social media activity
     * Combines port features with social media mentions
     */
    public List<Map<String, String>> findRecommendedPortsBySocialMedia(List<String> interests) {
        if (interests == null || interests.isEmpty()) {
            return new ArrayList<>();
        }
        
        StringBuilder filterBuilder = new StringBuilder();
        for (int i = 0; i < interests.size(); i++) {
            if (i > 0) filterBuilder.append(" || ");
            filterBuilder.append(String.format("LCASE(?keyword) = LCASE(\"%s\")", interests.get(i)));
        }
        
        String query = String.format("""
            PREFIX cruise: <%s>
            PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
            
            SELECT ?port (COUNT(DISTINCT ?post) as ?mentionCount) 
                   (AVG(?likes) as ?avgLikes)
                   (GROUP_CONCAT(DISTINCT ?keyword; separator=", ") as ?matchedInterests)
            WHERE {
                ?post cruise:mentionsPort ?port .
                ?post cruise:hasKeyword ?keywordConcept .
                ?keywordConcept skos:prefLabel ?keyword .
                FILTER(%s)
                OPTIONAL { ?post cruise:likesCount ?likes }
            }
            GROUP BY ?port
            ORDER BY DESC(?mentionCount) DESC(?avgLikes)
            LIMIT 20
            """, namespace, filterBuilder.toString());
        
        return executeQuery(query);
    }
    
    /**
     * Find posts by location
     */
    public List<Map<String, String>> findPostsByLocation(String location) {
        String query = String.format("""
            PREFIX cruise: <%s>
            PREFIX schema: <http://schema.org/>
            PREFIX sioc: <http://rdfs.org/sioc/ns#>
            
            SELECT ?post ?content ?author ?platform ?location ?likes
            WHERE {
                ?post schema:contentLocation ?locationResource .
                ?locationResource schema:name ?location .
                FILTER(CONTAINS(LCASE(?location), LCASE("%s")))
                ?post sioc:content ?content .
                ?post sioc:has_creator ?author .
                ?post cruise:hasPlatform ?platform .
                OPTIONAL { ?post cruise:likesCount ?likes }
            }
            ORDER BY DESC(?likes)
            LIMIT 50
            """, namespace, location);
        
        return executeQuery(query);
    }
    
    /**
     * Find posts by platform
     */
    public List<Map<String, String>> findPostsByPlatform(String platform) {
        String query = String.format("""
            PREFIX cruise: <%s>
            PREFIX sioc: <http://rdfs.org/sioc/ns#>
            
            SELECT ?post ?content ?author ?timestamp ?likes ?shares
            WHERE {
                ?post cruise:hasPlatform "%s" .
                ?post sioc:content ?content .
                ?post sioc:has_creator ?author .
                ?post dcterms:created ?timestamp .
                OPTIONAL { ?post cruise:likesCount ?likes }
                OPTIONAL { ?post cruise:sharesCount ?shares }
            }
            ORDER BY DESC(?timestamp)
            LIMIT 100
            """, namespace, platform.toUpperCase());
        
        return executeQuery(query);
    }
    
    /**
     * Execute SPARQL query and return results as list of maps
     */
    private List<Map<String, String>> executeQuery(String queryString) {
        List<Map<String, String>> results = new ArrayList<>();
        
        try {
            Query query = QueryFactory.create(queryString);
            String authUrl = buildAuthenticatedEndpointUrl(sparqlEndpoint);
            
            try (QueryExecution qexec = QueryExecutionHTTP.service(authUrl).query(query).build()) {
                ResultSet resultSet = qexec.execSelect();
                
                while (resultSet.hasNext()) {
                    QuerySolution solution = resultSet.nextSolution();
                    Map<String, String> resultMap = new HashMap<>();
                    
                    // Extract all variables from solution
                    Iterator<String> varNames = solution.varNames();
                    while (varNames.hasNext()) {
                        String varName = varNames.next();
                        org.apache.jena.rdf.model.RDFNode node = solution.get(varName);
                        if (node != null) {
                            resultMap.put(varName, node.toString());
                        }
                    }
                    
                    results.add(resultMap);
                }
            }
            
            log.debug("Executed SPARQL query, returned {} results", results.size());
            
        } catch (Exception e) {
            log.error("Error executing SPARQL query", e);
            throw new RuntimeException("Failed to execute SPARQL query", e);
        }
        
        return results;
    }
    
    /**
     * Build authenticated endpoint URL
     */
    private String buildAuthenticatedEndpointUrl(String endpoint) {
        String username = (fusekiUsername == null || fusekiUsername.trim().isEmpty()) ? "admin" : fusekiUsername;
        String password = (fusekiPassword == null || fusekiPassword.trim().isEmpty()) ? "admin" : fusekiPassword;
        
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
}

