# C4 Level 4 - Code Diagram
## Social Web Recommender for Cruising Ports

### Overview
This code diagram zooms into specific components to show how they are implemented at the code level. We focus on key classes and their relationships, including passenger recommendations, AIS tracking, system monitoring, and admin operations.

### Key Code Examples

#### **1. ApiPerformanceFilter - API Performance Tracking**

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiPerformanceFilter extends OncePerRequestFilter implements Ordered {
    
    private static final int FILTER_ORDER = 1; // Execute early in the filter chain
    
    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }
    
    private final ApiPerformanceService apiPerformanceService;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                     HttpServletResponse response, 
                                     FilterChain filterChain)
            throws ServletException, IOException {
        
        long startTime = System.currentTimeMillis();
        LocalDateTime timestamp = LocalDateTime.now();
        
        // Wrap request/response to cache content for size calculation
        ContentCachingRequestWrapper wrappedRequest = 
            new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = 
            new ContentCachingResponseWrapper(response);
        
        String errorType = null;
        String errorMessage = null;
        boolean success = true;
        
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
            
            int statusCode = wrappedResponse.getStatus();
            success = statusCode < 400;
            
        } catch (Exception e) {
            success = false;
            errorType = e.getClass().getSimpleName();
            errorMessage = e.getMessage();
            throw e;
        } finally {
            long responseTime = System.currentTimeMillis() - startTime;
            
            // Extract user information from security context
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userEmail = auth != null ? auth.getName() : null;
            String userRole = extractUserRole(auth);
            
            // Record API call asynchronously
            apiPerformanceService.recordApiCall(
                request.getRequestURI(),
                request.getMethod(),
                wrappedResponse.getStatus(),
                responseTime,
                success,
                errorType,
                errorMessage,
                userEmail,
                userRole,
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                wrappedRequest.getContentLengthLong(),
                wrappedResponse.getContentSizeLong(),
                timestamp
            );
            
            wrappedResponse.copyBodyToResponse();
        }
    }
}
```

**Key Relationships:**
- Extends: `OncePerRequestFilter` (Spring Security)
- Implements: `Ordered` (order 1, before JWT filter)
- Uses: `ApiPerformanceService` for async indexing
- Wraps: Request/Response with `ContentCachingRequestWrapper` for size calculation

#### **2. ApiPerformanceService - Async Performance Indexing**

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiPerformanceService {
    
    @Value("${elasticsearch.host:localhost}")
    private String elasticsearchHost;
    
    @Value("${elasticsearch.port:9200}")
    private int elasticsearchPort;
    
    @Value("${elasticsearch.enabled:false}")
    private boolean elasticsearchEnabled;
    
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    
    @Async
    public void recordApiCall(String endpoint, String method, int httpStatus,
                              long responseTimeMs, boolean success,
                              String errorType, String errorMessage,
                              String userEmail, String userRole,
                              String clientIp, String userAgent,
                              long requestSize, long responseSize,
                              LocalDateTime timestamp) {
        
        if (!elasticsearchEnabled) {
            return;
        }
        
        try {
            ApiPerformanceDocument document = ApiPerformanceDocument.builder()
                .id(UUID.randomUUID().toString())
                .endpoint(endpoint)
                .method(method)
                .httpStatus(httpStatus)
                .responseTimeMs(responseTimeMs)
                .success(success)
                .errorType(errorType)
                .errorMessage(errorMessage)
                .userEmail(userEmail)
                .userRole(userRole)
                .clientIp(clientIp)
                .userAgent(userAgent)
                .requestSize(requestSize)
                .responseSize(responseSize)
                .timestamp(timestamp)
                .build();
            
            String indexName = "api-performance-" + 
                timestamp.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
            
            String json = objectMapper.writeValueAsString(document);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + elasticsearchHost + ":" + 
                     elasticsearchPort + "/" + indexName + "/_doc"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
            
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
        } catch (Exception e) {
            log.debug("Error indexing API performance: {}", e.getMessage());
        }
    }
}
```

**Key Relationships:**
- Uses: `@Async` for non-blocking execution
- Uses: `HttpClient` for Elasticsearch HTTP API
- Creates: `ApiPerformanceDocument` for indexing
- Indexes: Daily indices (`api-performance-YYYY.MM.DD`)

#### **3. ResourceUtilizationService - System Metrics Collection**

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceUtilizationService {
    
    @Value("${monitoring.resource-collection-interval:60000}")
    private long collectionInterval;
    
    @Value("${elasticsearch.enabled:false}")
    private boolean elasticsearchEnabled;
    
    private final ElasticsearchStatsService elasticsearchStatsService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    
    @Scheduled(fixedRateString = "${monitoring.resource-collection-interval:60000}")
    public void collectAndIndexResourceUtilization() {
        if (!elasticsearchEnabled) {
            return;
        }
        
        try {
            ResourceUtilizationDocument metrics = collectMetrics();
            indexMetrics(metrics);
        } catch (Exception e) {
            log.debug("Error collecting resource utilization: {}", e.getMessage());
        }
    }
    
    private ResourceUtilizationDocument collectMetrics() {
        // CPU metrics
        Double cpuUsagePercent = getCpuUsage();
        Double systemLoadAverage = getSystemLoadAverage();
        
        // Memory metrics
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        
        // Thread metrics
        int activeThreads = threadBean.getThreadCount();
        int peakThreads = threadBean.getPeakThreadCount();
        int daemonThreads = threadBean.getDaemonThreadCount();
        
        // GC metrics
        long gcCollectionCount = getGcCollectionCount();
        long gcCollectionTimeMs = getGcCollectionTime();
        
        // Disk metrics
        File root = new File("/");
        long diskTotalBytes = root.getTotalSpace();
        long diskFreeBytes = root.getFreeSpace();
        double diskUsagePercent = ((double)(diskTotalBytes - diskFreeBytes) / diskTotalBytes) * 100;
        
        return ResourceUtilizationDocument.builder()
            .id(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .cpuUsagePercent(cpuUsagePercent)
            .systemLoadAverage(systemLoadAverage)
            .heapUsedBytes(heapUsage.getUsed())
            .heapMaxBytes(heapUsage.getMax())
            .heapUsagePercent(((double)heapUsage.getUsed() / heapUsage.getMax()) * 100)
            .nonHeapUsedBytes(nonHeapUsage.getUsed())
            .nonHeapMaxBytes(nonHeapUsage.getMax())
            .nonHeapUsagePercent(nonHeapUsage.getMax() > 0 ? 
                ((double)nonHeapUsage.getUsed() / nonHeapUsage.getMax()) * 100 : 0.0)
            .totalMemoryBytes(Runtime.getRuntime().totalMemory())
            .freeMemoryBytes(Runtime.getRuntime().freeMemory())
            .memoryUsagePercent(((double)(Runtime.getRuntime().totalMemory() - 
                Runtime.getRuntime().freeMemory()) / Runtime.getRuntime().totalMemory()) * 100)
            .activeThreads(activeThreads)
            .peakThreads(peakThreads)
            .daemonThreads(daemonThreads)
            .gcCollectionCount(gcCollectionCount)
            .gcCollectionTimeMs(gcCollectionTimeMs)
            .diskTotalBytes(diskTotalBytes)
            .diskFreeBytes(diskFreeBytes)
            .diskUsagePercent(diskUsagePercent)
            .build();
    }
}
```

**Key Relationships:**
- Uses: `@Scheduled` for periodic execution
- Uses: `ManagementFactory` for JVM metrics
- Creates: `ResourceUtilizationDocument` for indexing
- Indexes: Daily indices (`resource-utilization-YYYY.MM.DD`)

#### **4. AdminController - Port Update with Field Merging**

```java
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    
    private final PortRepository portRepository;
    
    @PutMapping("/ports/{id}")
    public ResponseEntity<Port> updatePort(@PathVariable Long id, 
                                           @RequestBody Port port) {
        return portRepository.findById(id)
                .map(existing -> {
                    // Merge incoming fields with existing port 
                    // (only update fields that are provided)
                    if (port.getPortCode() != null) {
                        existing.setPortCode(port.getPortCode());
                    }
                    if (port.getName() != null) {
                        existing.setName(port.getName());
                    }
                    if (port.getCountry() != null) {
                        existing.setCountry(port.getCountry());
                    }
                    if (port.getCity() != null) {
                        existing.setCity(port.getCity());
                    }
                    if (port.getLatitude() != null) {
                        existing.setLatitude(port.getLatitude());
                    }
                    if (port.getLongitude() != null) {
                        existing.setLongitude(port.getLongitude());
                    }
                    if (port.getBerthsCapacity() != null) {
                        existing.setBerthsCapacity(port.getBerthsCapacity());
                    }
                    // Preserve all other fields (geo, region, facilities, etc.)
                    Port updated = portRepository.save(existing);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
```

**Key Relationships:**
- Uses: `@PreAuthorize("hasRole('ADMIN')")` for authorization
- Uses: `PortRepository` for data access
- Implements: Partial update pattern (merge fields)
- Preserves: Existing fields not in request body

#### **5. StatisticsController - System Performance Statistics**

```java
@RestController
@RequestMapping("/admin/stats")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@PreAuthorize("hasRole('ADMIN')")
public class StatisticsController {
    
    private final StatisticsService statisticsService;
    private final SystemPerformanceService systemPerformanceService;
    
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardStats(
            @RequestParam(defaultValue = "24") int hours) {
        
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        Map<String, Object> dashboard = new HashMap<>();
        
        dashboard.put("sparql", statisticsService.getSparqlStats(since));
        dashboard.put("messages", statisticsService.getMessageStats(since));
        dashboard.put("apiPerformance", 
            systemPerformanceService.getApiPerformanceStats(since));
        dashboard.put("resourceUtilization", 
            systemPerformanceService.getResourceUtilizationStats(since));
        dashboard.put("timeRange", Map.of(
            "since", since.toString(),
            "hours", hours
        ));
        
        return ResponseEntity.ok(dashboard);
    }
    
    @GetMapping("/api-performance")
    public ResponseEntity<Map<String, Object>> getApiPerformanceStats(
            @RequestParam(defaultValue = "24") int hours) {
        
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        Map<String, Object> stats = 
            systemPerformanceService.getApiPerformanceStats(since);
        return ResponseEntity.ok(stats);
    }
    
    @GetMapping("/resource-utilization")
    public ResponseEntity<Map<String, Object>> getResourceUtilizationStats(
            @RequestParam(defaultValue = "24") int hours) {
        
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        Map<String, Object> stats = 
            systemPerformanceService.getResourceUtilizationStats(since);
        return ResponseEntity.ok(stats);
    }
}
```

**Key Relationships:**
- Uses: `StatisticsService` for SPARQL and message stats
- Uses: `SystemPerformanceService` for performance stats
- Aggregates: Multiple statistics sources
- Returns: Combined dashboard data

#### **6. SystemPerformanceService - Elasticsearch Query Service**

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemPerformanceService {
    
    @Value("${elasticsearch.host:localhost}")
    private String elasticsearchHost;
    
    @Value("${elasticsearch.port:9200}")
    private int elasticsearchPort;
    
    @Value("${elasticsearch.enabled:false}")
    private boolean elasticsearchEnabled;
    
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    
    public Map<String, Object> getApiPerformanceStats(LocalDateTime since) {
        if (!elasticsearchEnabled) {
            return createEmptyStats();
        }
        
        try {
            String indexPattern = "api-performance-*";
            String query = buildAggregationQuery(since);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + elasticsearchHost + ":" + 
                     elasticsearchPort + "/" + indexPattern + "/_search"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(query))
                .build();
            
            HttpResponse<String> response = 
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            JsonNode jsonResponse = objectMapper.readTree(response.body());
            return parseAggregationResponse(jsonResponse);
            
        } catch (Exception e) {
            log.error("Error querying API performance stats: {}", e.getMessage());
            return createEmptyStats();
        }
    }
    
    private String buildAggregationQuery(LocalDateTime since) {
        // Build Elasticsearch aggregation query
        // Aggregates: total requests, avg response time, error rates, etc.
        return """
            {
              "size": 0,
              "query": {
                "range": {
                  "timestamp": {
                    "gte": "%s"
                  }
                }
              },
              "aggs": {
                "total_requests": { "value_count": { "field": "_id" } },
                "avg_response_time": { "avg": { "field": "responseTimeMs" } },
                "error_rate": {
                  "filters": {
                    "filters": {
                      "errors": { "term": { "success": false } }
                    }
                  }
                },
                "status_codes": {
                  "terms": { "field": "httpStatus", "size": 20 }
                }
              }
            }
            """.formatted(since.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }
}
```

**Key Relationships:**
- Uses: `HttpClient` for Elasticsearch queries
- Queries: Elasticsearch aggregation API
- Parses: JSON responses with `ObjectMapper`
- Returns: Aggregated statistics

#### **7. JwtAuthenticationFilter - Authentication**

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter implements Ordered {
    
    private static final int FILTER_ORDER = 2; // After ApiPerformanceFilter
    
    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }
    
    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {
        
        try {
            String jwt = getJwtFromRequest(request);
            
            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                String email = tokenProvider.getEmailFromToken(jwt);
                
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
        }
        
        filterChain.doFilter(request, response);
    }
    
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
```

**Key Relationships:**
- Extends: `OncePerRequestFilter`
- Implements: `Ordered` (order 2)
- Uses: `JwtTokenProvider` for token validation
- Uses: `CustomUserDetailsService` for user loading
- Sets: `SecurityContext` for authorization

#### **8. SecurityConfig - Filter Chain Configuration**

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiPerformanceFilter apiPerformanceFilter;
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/api-docs/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(apiPerformanceFilter, 
                UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, 
                UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
```

**Key Relationships:**
- Configures: Filter chain order
- Registers: `ApiPerformanceFilter` (order 1)
- Registers: `JwtAuthenticationFilter` (order 2)
- Configures: Authorization rules with `@PreAuthorize`

#### **9. FacebookTokenValidationService - Secure Token Validation**

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class FacebookTokenValidationService {
    
    private final RestTemplate restTemplate;
    
    private static final String FACEBOOK_GRAPH_API_BASE = "https://graph.facebook.com/v24.0";
    
    public Map<String, Object> validateTokenAndGetUser(String accessToken) 
            throws FacebookTokenException {
        
        // Use Authorization header instead of URL parameter
        String verifyUrl = FACEBOOK_GRAPH_API_BASE + "/me?fields=id,name,email";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken); // ✅ Secure: Token in header
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<?> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                verifyUrl, HttpMethod.GET, entity, Map.class);
            
            Map<String, Object> facebookUser = response.getBody();
            
            // Check for Facebook API errors
            if (facebookUser.containsKey("error")) {
                Map<String, Object> error = (Map<String, Object>) facebookUser.get("error");
                String errorCode = String.valueOf(error.get("code"));
                
                // Handle expired token (error code 190)
                if ("190".equals(errorCode)) {
                    throw new FacebookTokenException(
                        "Facebook token expired. Please login again.", 
                        "TOKEN_EXPIRED");
                }
                
                // Handle invalid token (error code 102)
                if ("102".equals(errorCode)) {
                    throw new FacebookTokenException(
                        "Invalid Facebook access token", 
                        "INVALID_TOKEN");
                }
            }
            
            return facebookUser;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new FacebookTokenException(
                    "Invalid Facebook access token", 
                    "INVALID_TOKEN");
            }
            throw new FacebookTokenException(
                "Failed to verify Facebook access token: " + e.getMessage(), 
                "HTTP_ERROR");
        }
    }
    
    public static class FacebookTokenException extends Exception {
        private final String errorCode;
        public FacebookTokenException(String message, String errorCode) {
            super(message);
            this.errorCode = errorCode;
        }
        public String getErrorCode() { return errorCode; }
    }
}
```

**Key Relationships:**
- Uses: `RestTemplate` for HTTP calls
- Uses: `Authorization` header (not URL parameters)
- Handles: Token expiration and errors
- Returns: User information or custom exceptions

#### **10. AuthController - Secure Facebook Login**

```java
@PostMapping("/facebook/login")
public ResponseEntity<?> loginWithFacebook(
        @Valid @RequestBody FacebookLoginRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse) {
    
    // Input validation
    if (request.getAccessToken() == null || request.getAccessToken().trim().isEmpty()) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Access token is required"));
    }
    
    // CSRF protection - validate Origin header
    String origin = httpRequest.getHeader("Origin");
    if (origin != null && !isValidOrigin(origin)) {
        log.warn("Invalid origin for Facebook login: {}", origin);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Invalid origin"));
    }
    
    // Validate Facebook access token using secure method
    Map<String, Object> facebookUser;
    try {
        facebookUser = facebookTokenValidationService.validateTokenAndGetUser(
            request.getAccessToken());
    } catch (FacebookTokenValidationService.FacebookTokenException e) {
        if ("TOKEN_EXPIRED".equals(e.getErrorCode())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Facebook token expired. Please login again.",
                                "errorCode", e.getErrorCode()));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid Facebook access token",
                            "errorCode", e.getErrorCode()));
    }
    
    // Sanitize input to prevent XSS
    String facebookUserId = sanitizeInput((String) facebookUser.get("id"));
    String email = sanitizeEmail((String) facebookUser.get("email"));
    String name = sanitizeInput((String) facebookUser.get("name"));
    
    // Validate email format
    if (!isValidEmail(email)) {
        email = facebookUserId + "@facebook.com";
    }
    
    // Find or create user
    User user = userRepository.findByEmail(email).orElse(null);
    if (user == null) {
        user = User.builder()
            .email(email)
            .emailVerified(true)
            .firstName(name != null ? name : "")
            .build();
        user = userRepository.save(user);
    }
    
    // Generate JWT token
    String token = tokenProvider.generateToken(
        user.getEmail(), user.getId(), user.getRole().name());
    
    // Set secure HTTP-only cookies
    Cookie cookie = createSecureCookie("token", token, "/api/v1", 7 * 24 * 60 * 60);
    httpResponse.addCookie(cookie);
    
    return ResponseEntity.ok(authResponse);
}

private Cookie createSecureCookie(String name, String value, String path, int maxAge) {
    Cookie cookie = new Cookie(name, value);
    cookie.setHttpOnly(true); // ✅ Prevent JavaScript access
    cookie.setSecure(isProduction()); // HTTPS only in production
    cookie.setPath(path);
    cookie.setMaxAge(maxAge);
    cookie.setAttribute("SameSite", "Lax"); // CSRF protection
    return cookie;
}

private String sanitizeInput(String input) {
    if (input == null) return null;
    return input.replaceAll("[<>\"']", "").trim(); // Remove XSS characters
}
```

**Key Relationships:**
- Uses: `FacebookTokenValidationService` for secure token validation
- Implements: CSRF protection via Origin validation
- Implements: Input sanitization for XSS prevention
- Creates: Secure HTTP-only cookies

#### **11. SocialMediaRdfService - RDF Conversion**

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class SocialMediaRdfService {
    
    private final PortRepository portRepository;
    
    @RabbitListener(queues = RabbitMQConfig.SOCIAL_MEDIA_QUEUE)
    public void processSocialMediaPost(Object message) {
        try {
            SocialMediaPost post = convertToSocialMediaPost(message);
            if (post == null) return;
            
            convertAndStorePost(post);
        } catch (Exception e) {
            log.error("Error processing social media post for RDF", e);
        }
    }
    
    public void convertAndStorePost(SocialMediaPost post) {
        Model model = ModelFactory.createDefaultModel();
        
        // Set namespaces
        model.setNsPrefix("cruise", namespace);
        model.setNsPrefix("sioc", SIOC_NS);
        model.setNsPrefix("schema", SCHEMA_NS);
        model.setNsPrefix("skos", SKOS_NS);
        
        // Create RDF resource for the post
        String sanitizedPostId = sanitizeForUri(post.getPostId());
        Resource postResource = model.createResource(namespace + "post/" + sanitizedPostId);
        
        // Add RDF types
        postResource.addProperty(model.createProperty(RDF_NS + "type"), 
                                model.createResource(SIOC_NS + "Post"));
        postResource.addProperty(model.createProperty(RDF_NS + "type"), 
                                model.createResource(SCHEMA_NS + "SocialMediaPosting"));
        
        // Content
        if (post.getContent() != null) {
            postResource.addProperty(model.createProperty(SIOC_NS + "content"), 
                                    post.getContent());
        }
        
        // Author
        if (post.getAuthorId() != null) {
            Resource authorResource = model.createResource(
                namespace + "author/" + sanitizeForUri(post.getAuthorId()));
            authorResource.addProperty(model.createProperty(RDF_NS + "type"), 
                                     model.createResource(SIOC_NS + "UserAccount"));
            postResource.addProperty(model.createProperty(SIOC_NS + "has_creator"), 
                                    authorResource);
        }
        
        // Platform
        if (post.getPlatform() != null) {
            postResource.addProperty(model.createProperty(namespace + "hasPlatform"), 
                                    post.getPlatform().toUpperCase());
        }
        
        // Hashtags as SKOS concepts
        if (post.getHashtags() != null) {
            for (String hashtag : post.getHashtags()) {
                Resource hashtagConcept = model.createResource(
                    namespace + "hashtag/" + sanitizeForUri(hashtag.toLowerCase()));
                hashtagConcept.addProperty(model.createProperty(RDF_NS + "type"), 
                                         model.createResource(SKOS_NS + "Concept"));
                hashtagConcept.addProperty(model.createProperty(SKOS_NS + "prefLabel"), 
                                         hashtag, "en");
                postResource.addProperty(model.createProperty(namespace + "hasHashtag"), 
                                       hashtagConcept);
            }
        }
        
        // Keywords as SKOS concepts
        if (post.getKeywords() != null) {
            for (String keyword : post.getKeywords()) {
                Resource keywordConcept = model.createResource(
                    namespace + "keyword/" + sanitizeForUri(keyword.toLowerCase()));
                keywordConcept.addProperty(model.createProperty(RDF_NS + "type"), 
                                        model.createResource(SKOS_NS + "Concept"));
                keywordConcept.addProperty(model.createProperty(SKOS_NS + "prefLabel"), 
                                         keyword, "en");
                postResource.addProperty(model.createProperty(namespace + "hasKeyword"), 
                                       keywordConcept);
                
                // Link keywords to port features
                linkKeywordToPortFeatures(model, postResource, keyword);
            }
        }
        
        // Link to port if location matches
        if (post.getLocation() != null) {
            linkToPortIfMatch(model, postResource, post.getLocation());
        }
        
        // Upload to Fuseki
        uploadModelToFuseki(model);
    }
    
    private void linkToPortIfMatch(Model model, Resource postResource, String location) {
        String locationLower = location.toLowerCase();
        if (locationLower.contains("barcelona")) {
            postResource.addProperty(model.createProperty(namespace + "mentionsPort"), 
                                   model.createResource(namespace + "port/BARCELONA"));
        }
        // Add more location matching logic
    }
    
    private void linkKeywordToPortFeatures(Model model, Resource postResource, String keyword) {
        Map<String, String> keywordToConcept = Map.of(
            "snorkeling", "activity/snorkeling",
            "art", "interest/art",
            "food", "cuisine/food"
        );
        
        String conceptPath = keywordToConcept.get(keyword.toLowerCase());
        if (conceptPath != null) {
            Resource conceptResource = model.createResource(namespace + "concept/" + conceptPath);
            conceptResource.addProperty(model.createProperty(RDF_NS + "type"), 
                                      model.createResource(SKOS_NS + "Concept"));
            postResource.addProperty(model.createProperty(namespace + "relatesToPortFeature"), 
                                   conceptResource);
        }
    }
}
```

**Key Relationships:**
- Listens: RabbitMQ `social.media.queue`
- Uses: Apache Jena RDF API
- Creates: RDF triples with SIOC and Schema.org vocabularies
- Links: Posts to ports, keywords, hashtags via SKOS
- Uploads: RDF to Fuseki via SPARQL UPDATE

#### **12. SocialMediaRdfQueryService - SPARQL Queries**

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class SocialMediaRdfQueryService {
    
    @Value("${knowledge.graph.endpoint:http://localhost:3030/cruise_kg/sparql}")
    private String sparqlEndpoint;
    
    public List<Map<String, String>> findPostsMatchingInterests(List<String> interests) {
        // Build filter for multiple interests
        StringBuilder filterBuilder = new StringBuilder();
        for (int i = 0; i < interests.size(); i++) {
            if (i > 0) filterBuilder.append(" || ");
            filterBuilder.append(String.format(
                "LCASE(?keyword) = LCASE(\"%s\")", interests.get(i)));
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
    
    public List<Map<String, String>> findRecommendedPortsBySocialMedia(List<String> interests) {
        // Similar SPARQL query that groups by port and calculates match scores
        // Returns ports with mention counts and matched interests
    }
    
    private List<Map<String, String>> executeQuery(String queryString) {
        Query query = QueryFactory.create(queryString);
        String authUrl = buildAuthenticatedEndpointUrl(sparqlEndpoint);
        
        try (QueryExecution qexec = QueryExecutionHTTP.service(authUrl)
                .query(query).build()) {
            ResultSet resultSet = qexec.execSelect();
            List<Map<String, String>> results = new ArrayList<>();
            
            while (resultSet.hasNext()) {
                QuerySolution solution = resultSet.nextSolution();
                Map<String, String> resultMap = new HashMap<>();
                
                Iterator<String> varNames = solution.varNames();
                while (varNames.hasNext()) {
                    String varName = varNames.next();
                    RDFNode node = solution.get(varName);
                    if (node != null) {
                        resultMap.put(varName, node.toString());
                    }
                }
                results.add(resultMap);
            }
            return results;
        }
    }
}
```

**Key Relationships:**
- Uses: Apache Jena SPARQL Query Engine
- Queries: Social media RDF dataset in Fuseki
- Returns: Structured query results
- Enables: Semantic search for recommendations

### Code Patterns and Principles

#### **Dependency Injection**
- All services use constructor injection via `@RequiredArgsConstructor` (Lombok)
- Controllers inject services, services inject repositories
- Promotes testability and loose coupling

#### **Repository Pattern**
- Spring Data JPA repositories abstract database access
- Custom queries for complex operations
- Type-safe query methods

#### **DTO Pattern**
- Request/Response DTOs separate API contracts from entities
- Prevents entity exposure in APIs
- Enables API versioning

#### **Service Layer Pattern**
- Business logic in services, not controllers
- Services coordinate between repositories
- Transaction management at service level (`@Transactional`)

#### **Message-Driven Processing**
- `@RabbitListener` for async message processing
- Decouples AIS data ingestion from processing
- Enables scalable processing

#### **Caching Strategy**
- Redis for recommendation results
- Cache-aside pattern
- TTL-based expiration

#### **Filter Chain Pattern**
- Ordered filters for cross-cutting concerns
- `ApiPerformanceFilter` (order 1) tracks performance
- `JwtAuthenticationFilter` (order 2) validates authentication
- Filters execute before controllers

#### **Async Processing**
- `@Async` for non-blocking operations
- `ApiPerformanceService` uses async for Elasticsearch indexing
- Scheduled tasks for periodic operations (`@Scheduled`)

#### **Partial Update Pattern**
- Admin controllers merge incoming fields with existing entities
- Preserves fields not in request body
- Prevents accidental data loss

#### **RDF/Semantic Web Pattern**
- SocialMediaRdfService converts posts to RDF triples
- Uses SIOC (Social Media Ontology) and Schema.org vocabularies
- SKOS concepts for semantic relationships
- SPARQL queries enable semantic search
- Links social media content to ports and interests

#### **Security Pattern**
- Token validation via Authorization header (not URL)
- Input sanitization to prevent XSS
- CSRF protection via Origin header validation
- Secure cookie creation (HttpOnly, Secure, SameSite)
- Comprehensive error handling with custom exceptions

### Key Algorithms

#### **Multi-Factor Recommendation Scoring**
```java
score = (interestMatch * 0.4) + 
        (localRecommendation * 0.3) + 
        (popularity * 0.15) + 
        (rating * 0.1) + 
        (accessibility * 0.05) +
        (socialMediaScore * 0.1) // NEW: Social proof from RDF queries
```

#### **Social Media RDF Query for Recommendations**
```java
// Query social media RDF for posts matching user interests
List<Map<String, String>> matchingPosts = 
    socialMediaRdfQueryService.findPostsMatchingInterests(userInterests);

// Extract ports mentioned in matching posts
Set<String> recommendedPorts = matchingPosts.stream()
    .map(post -> post.get("port"))
    .filter(Objects::nonNull)
    .collect(Collectors.toSet());

// Calculate social proof score
double socialProofScore = calculateSocialProofScore(matchingPosts);
// Factors: number of mentions, average likes, recency

// Add to recommendation score
double totalScore = baseScore + (socialProofScore * 0.1);
```

#### **Interest Matching Algorithm**
```java
for (PassengerInterest interest : interests) {
    if (matchesCategory(excursion, interest)) {
        totalScore += interest.getConfidenceScore();
        matches++;
    }
}
return matches > 0 ? totalScore / interests.size() : 0.0;
```

#### **Long Tail Identification**
```java
long tailThreshold = (long) (totalItems * 0.8); // Bottom 80%
longTailItems = items.filter(item -> 
    item.getPopularity() < tailThreshold
);
```

### Error Handling

#### **Exception Hierarchy**
- `RuntimeException` for business logic errors
- `EntityNotFoundException` for missing entities
- Custom exceptions for domain-specific errors
- `GlobalExceptionHandler` for centralized error handling

#### **Validation**
- Bean Validation (`@Valid`, `@NotNull`, `@Email`, `@Size`)
- Custom validators for complex rules
- Validation at controller level

### Testing Considerations

#### **Unit Tests**
- Mock repositories and external services
- Test service logic in isolation
- Test scoring algorithms with various inputs
- Test filter logic with mock requests

#### **Integration Tests**
- Test full request/response cycle
- Use test database
- Test RabbitMQ message processing
- Test Elasticsearch indexing

#### **Component Tests**
- Test controller-service-repository integration
- Verify data flow through layers
- Test transaction boundaries
- Test filter chain execution

---

*This code diagram shows the implementation details of key components, focusing on system monitoring, admin operations, and authentication as representative examples of the system's architecture.*
