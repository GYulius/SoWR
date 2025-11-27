# C4 Level 3 - Component Diagram
## Social Web Recommender for Cruising Ports

### Overview
This component diagram zooms into the **API Application** container to show the major components (services, controllers, repositories) and how they interact. This represents the internal structure of the Spring Boot application.

### API Application Container Components

#### **Controllers (Spring MVC)**

##### **1. PassengerRecommendationController**
- **Technology**: Spring MVC, REST
- **Purpose**: Handle passenger-focused recommendation requests
- **Responsibilities**:
  - Receive passenger recommendation requests
  - Coordinate with recommendation services
  - Return personalized recommendations
- **Key Methods**:
  - `getPassengerRecommendations()` - Comprehensive recommendations
  - `getShoreExcursionRecommendations()` - Excursion recommendations
  - `getMustSeeHighlights()` - Must-see highlights
  - `getBreakfastVenues()` - Breakfast venue recommendations
  - `getLunchVenues()` - Lunch venue recommendations
  - `analyzeSocialMedia()` - Trigger social media analysis
- **Dependencies**:
  - ShoreExcursionRecommendationService
  - MealVenueRecommendationService
  - SocialMediaAnalysisService

##### **2. DashboardController**
- **Technology**: Spring MVC, REST
- **Purpose**: Provide ship tracking and analytics endpoints
- **Responsibilities**:
  - Serve current ship positions
  - Provide ships near port queries
  - Return tracking statistics
- **Key Methods**:
  - `getCurrentShipPositions()` - All tracked ships
  - `getShipsNearPort()` - Ships within radius
  - `getShipTracking()` - Ship tracking history
  - `getShipStatistics()` - Overall statistics
- **Dependencies**:
  - AisDataService

##### **3. RecommendationController**
- **Technology**: Spring MVC, REST
- **Purpose**: Handle general recommendation operations
- **Responsibilities**:
  - Generate recommendations
  - Handle feedback
  - Provide recommendation history
- **Key Methods**:
  - `getRecommendations()` - Generate recommendations
  - `submitFeedback()` - User feedback
  - `getRecommendationHistory()` - Historical data
  - `refreshRecommendations()` - Regenerate recommendations
- **Dependencies**:
  - RecommendationService

##### **4. AdminController**
- **Technology**: Spring MVC, REST
- **Purpose**: Provide admin CRUD operations
- **Responsibilities**:
  - Manage ports (CRUD)
  - Manage cruise ships (CRUD)
  - Manage meal venues (CRUD)
  - Manage restaurants (CRUD)
- **Key Methods**:
  - Port operations: `getAllPorts()`, `getPort()`, `createPort()`, `updatePort()`, `deletePort()`
  - Ship operations: `getAllShips()`, `getShip()`, `createShip()`, `updateShip()`, `deleteShip()`
  - Meal venue operations: `getAllMealVenues()`, `createMealVenue()`, `updateMealVenue()`, `deleteMealVenue()`
  - Restaurant operations: `getAllRestaurants()`, `createRestaurant()`, `updateRestaurant()`, `deleteRestaurant()`
- **Dependencies**:
  - PortRepository
  - CruiseShipRepository
  - MealVenueRepository
  - RestaurantRepository
  - CategoryRepository

##### **5. AdminWebController**
- **Technology**: Spring MVC, Thymeleaf
- **Purpose**: Serve admin web interface
- **Responsibilities**:
  - Serve maintenance.html page
  - Handle admin web requests
- **Dependencies**:
  - Various repositories for data loading

##### **6. StatisticsController**
- **Technology**: Spring MVC, REST
- **Purpose**: Provide statistics and monitoring endpoints
- **Responsibilities**:
  - Serve API performance statistics
  - Serve resource utilization statistics
  - Serve SPARQL query statistics
  - Serve RabbitMQ message statistics
  - Provide combined dashboard statistics
- **Key Methods**:
  - `getApiPerformanceStats()` - API performance metrics
  - `getResourceUtilizationStats()` - Resource utilization metrics
  - `getDashboardStats()` - Combined dashboard statistics
  - `getSparqlStats()` - SPARQL query statistics
  - `getMessageStats()` - Message tracking statistics
- **Dependencies**:
  - SystemPerformanceService
  - StatisticsService

##### **7. PortRdfController**
- **Technology**: Spring MVC, REST
- **Purpose**: Handle port RDF/SPARQL operations
- **Responsibilities**:
  - Create port RDF datasets
  - Serve port RDF data
  - Execute SPARQL queries on ports
  - Provide SPARQL statistics
- **Key Methods**:
  - `createPortsDataset()` - Create RDF dataset from ports
  - `getPortsByCountry()` - Query ports by country
  - `getPortsByActivity()` - Query ports by activity
  - `getPortDetails()` - Get port RDF details
  - `executeCustomQuery()` - Execute custom SPARQL query
- **Dependencies**:
  - PortRdfService
  - StatisticsService

##### **7a. SocialMediaRdfController**
- **Technology**: Spring MVC, REST
- **Purpose**: Handle social media RDF queries
- **Responsibilities**:
  - Query social media posts by port, keyword, hashtag
  - Find popular interests and ports from social media
  - Provide recommendations based on social media RDF
- **Key Methods**:
  - `getPostsByPort()` - Posts mentioning a port
  - `getPostsByKeyword()` - Posts by keyword/interest
  - `getPostsByHashtag()` - Posts by hashtag
  - `getPopularInterests()` - Trending interests
  - `getPopularPorts()` - Popular ports from social mentions
  - `getPostsMatchingInterests()` - Posts matching user interests
  - `getRecommendedPorts()` - Port recommendations from social media
- **Dependencies**:
  - SocialMediaRdfQueryService
  - StatisticsService

##### **8. PublisherController**
- **Technology**: Spring MVC, REST
- **Purpose**: Manage publisher (local business) operations
- **Responsibilities**:
  - Publisher registration
  - Content publishing
  - Subscriber management
- **Key Methods**:
  - `createPublisher()` - Register publisher
  - `publishContent()` - Publish offers/events
  - `getSubscriberAnalytics()` - Analytics
  - `sendCruiseAlert()` - Send alerts
- **Dependencies**:
  - PublisherService

##### **9. AuthController**
- **Technology**: Spring MVC, REST
- **Purpose**: Handle authentication operations
- **Responsibilities**:
  - User registration
  - User login
  - Secure Facebook login with token validation
  - Token refresh
  - Frontend configuration
- **Key Methods**:
  - `register()` - User registration
  - `login()` - User login
  - `loginWithFacebook()` - Secure Facebook authentication
  - `refreshToken()` - Token refresh
  - `getFrontendConfig()` - Frontend configuration
- **Security Features**:
  - Input validation and sanitization
  - CSRF protection via Origin header validation
  - Secure HTTP-only cookie creation
  - Token expiration handling
- **Dependencies**:
  - UserRepository
  - JwtTokenProvider
  - FacebookTokenValidationService
  - PasswordEncoder

##### **10. WebController**
- **Technology**: Spring MVC, Thymeleaf
- **Purpose**: Serve public web pages
- **Responsibilities**:
  - Serve index.html
  - Serve port maps
  - Handle web requests
- **Dependencies**:
  - PortRepository

##### **11. MapController**
- **Technology**: Spring MVC, REST
- **Purpose**: Provide map-related endpoints
- **Responsibilities**:
  - Serve map data
  - Provide geospatial queries
- **Dependencies**:
  - PortRepository

#### **Services (Business Logic)**

##### **12. SocialMediaAnalysisService**
- **Technology**: Spring Service, Apache Spark
- **Purpose**: Analyze passenger social media presence
- **Responsibilities**:
  - Extract interests from social media profiles
  - Perform sentiment analysis
  - Identify activity patterns
  - Aggregate interests for cruise groups
- **Key Methods**:
  - `analyzePassengerSocialMedia()` - Analyze individual profile
  - `analyzeCruisePassengersSocialMedia()` - Batch analysis
  - `extractInterestsFromPosts()` - Interest extraction
  - `performSentimentAnalysis()` - Sentiment analysis
  - `getAggregatedInterests()` - Group interests
- **Dependencies**:
  - SocialMediaProfileRepository
  - PassengerRepository
  - SparkMlService (for NLP processing)
  - RabbitTemplate (for async processing)

##### **13. ShoreExcursionRecommendationService**
- **Technology**: Spring Service
- **Purpose**: Generate personalized shore excursion recommendations
- **Responsibilities**:
  - Match passenger interests with excursions
  - Score excursions using multi-factor algorithm
  - Identify must-see highlights
  - Filter by budget and group size
- **Key Methods**:
  - `recommendShoreExcursions()` - Generate recommendations
  - `getMustSeeHighlights()` - Get highlights
  - `getPersonalizedMustSeeHighlights()` - Personalized highlights
  - `scoreExcursion()` - Calculate recommendation score
  - `calculateInterestMatch()` - Interest matching
- **Dependencies**:
  - ShoreExcursionRepository
  - PassengerRepository
  - PassengerInterestRepository

##### **14. MealVenueRecommendationService**
- **Technology**: Spring Service
- **Purpose**: Recommend breakfast and lunch venues
- **Responsibilities**:
  - Filter locally active venues
  - Match dietary restrictions
  - Consider walking distance
  - Score based on local recommendations
- **Key Methods**:
  - `recommendBreakfastVenues()` - Breakfast recommendations
  - `recommendLunchVenues()` - Lunch recommendations
  - `getLocallyRecommendedVenues()` - Local favorites
  - `isActiveDuringPortCall()` - Time-based filtering
  - `scoreMealVenue()` - Calculate score
- **Dependencies**:
  - MealVenueRepository
  - PassengerRepository
  - CruiseScheduleRepository

##### **15. RecommendationService**
- **Technology**: Spring Service
- **Purpose**: Core recommendation engine
- **Responsibilities**:
  - Generate ML-based recommendations
  - Store recommendation history
  - Handle feedback
  - Integrate with knowledge graph
- **Key Methods**:
  - `generateRecommendations()` - Main recommendation logic
  - `submitFeedback()` - Store user feedback
  - `getRecommendationHistory()` - Historical data
  - `refreshRecommendations()` - Regenerate
- **Dependencies**:
  - RecommendationRepository
  - UserRepository
  - PortRepository
  - KnowledgeGraphService
  - SparkMlService (for ML algorithms)

##### **16. AisDataService**
- **Technology**: Spring Service, RabbitMQ Consumer
- **Purpose**: Process AIS ship tracking data
- **Responsibilities**:
  - Consume AIS messages from RabbitMQ
  - Process and validate AIS data
  - Update ship positions
  - Index in Elasticsearch
  - Broadcast real-time updates
- **Key Methods**:
  - `processAisData()` - Process incoming AIS message
  - `findOrCreateShip()` - Ship management
  - `updateShipPosition()` - Update current position
  - `indexInElasticsearch()` - Search indexing
  - `publishPositionUpdate()` - Real-time broadcast
  - `getCurrentShipPositions()` - Query positions
  - `getShipsNearPort()` - Geographic queries
- **Dependencies**:
  - AisDataRepository
  - CruiseShipRepository
  - RabbitTemplate
  - ElasticsearchOperations

##### **17. AisDataIngestionService**
- **Technology**: Spring Service, Scheduled Task
- **Purpose**: Ingest AIS data from external providers
- **Responsibilities**:
  - Fetch AIS data from VesselFinder API
  - Send data to RabbitMQ
  - Handle API errors and retries
- **Key Methods**:
  - `ingestAisData()` - Fetch and queue AIS data
  - `fetchFromVesselFinder()` - API integration
- **Dependencies**:
  - RabbitTemplate
  - OpenAisApiClient (for VesselFinder)

##### **18. SparkMlService**
- **Technology**: Spring Service, Apache Spark
- **Purpose**: Big data processing and machine learning
- **Responsibilities**:
  - Train collaborative filtering models
  - Generate long tail recommendations
  - Analyze user behavior
  - Process AIS data analytics
  - Calculate recommendation diversity
- **Key Methods**:
  - `trainCollaborativeFilteringModel()` - Train ALS model
  - `generateLongTailRecommendations()` - Long tail items
  - `analyzeUserBehavior()` - Behavior patterns
  - `processAisDataAnalytics()` - AIS analytics
  - `calculateRecommendationDiversity()` - Diversity metrics
- **Dependencies**:
  - SparkSession (Apache Spark)
  - MySQL Database (read data)
  - Elasticsearch (read/write)

##### **19. PageRankService**
- **Technology**: Spring Service, JGraphT
- **Purpose**: Social network analysis using PageRank
- **Responsibilities**:
  - Calculate PageRank for user networks
  - Analyze publisher influence
  - Detect communities
  - Identify influential users
- **Key Methods**:
  - `calculateUserPageRank()` - User influence
  - `calculatePublisherPageRank()` - Publisher influence
  - `calculateRecommendationInfluencePageRank()` - Item influence
  - `findInfluentialUsers()` - Top influencers
  - `detectCommunities()` - Community detection
- **Dependencies**:
  - UserRepository
  - PublisherRepository
  - SubscriptionRepository

##### **20. KnowledgeGraphService**
- **Technology**: Spring Service, Apache Jena
- **Purpose**: RDF/SPARQL knowledge graph operations
- **Responsibilities**:
  - Process SPARQL queries
  - Calculate semantic scores
  - Update knowledge graph
  - Find related items
- **Key Methods**:
  - `calculateSemanticScore()` - Semantic matching
  - `buildSemanticQuery()` - SPARQL query building
  - `executeSparqlQuery()` - Query execution
  - `updateKnowledgeGraph()` - Graph updates
  - `findRelatedItems()` - Semantic relationships
- **Dependencies**:
  - Apache Jena API
  - SPARQL Endpoint

##### **21. PortRdfService**
- **Technology**: Spring Service, Apache Jena
- **Purpose**: Port RDF data management
- **Responsibilities**:
  - Convert port data to RDF
  - Store RDF in Fuseki
  - Execute SPARQL queries on ports
  - Track query statistics
- **Key Methods**:
  - `createPortsRdfDataset()` - Create RDF dataset from all ports
  - `createPortRdfResource()` - Convert single port to RDF
  - `findPortsByCountry()` - SPARQL query by country
  - `findPortsByActivity()` - SPARQL query by activity
  - `getPortDetails()` - Get port RDF details
  - `uploadModelToFuseki()` - Upload RDF to Fuseki
- **Dependencies**:
  - PortRepository
  - SparqlQueryStatRepository
  - Apache Jena Fuseki

##### **21a. SocialMediaRdfService**
- **Technology**: Spring Service, Apache Jena, RabbitMQ Consumer
- **Purpose**: Convert social media posts to RDF triples
- **Responsibilities**:
  - Listen to RabbitMQ `social.media.queue` for incoming posts
  - Convert social media posts to RDF using SIOC and Schema.org vocabularies
  - Link posts to ports, keywords, hashtags, interests via SKOS concepts
  - Upload RDF triples to Fuseki
  - Create RDF datasets from social media posts
- **Key Methods**:
  - `processSocialMediaPost()` - RabbitMQ listener for posts
  - `convertAndStorePost()` - Convert single post to RDF and store
  - `createSocialMediaRdfDataset()` - Create RDF dataset from multiple posts
  - `createPostRdfResource()` - Create RDF resource for a post
  - `linkToPortIfMatch()` - Link post to port if location matches
  - `linkKeywordToPortFeatures()` - Link keywords to port activities/interests
- **Dependencies**:
  - RabbitMQ (social.media.queue)
  - Apache Jena Fuseki
  - Apache Jena RDF API

##### **21b. SocialMediaRdfQueryService**
- **Technology**: Spring Service, Apache Jena SPARQL
- **Purpose**: Query social media RDF dataset with SPARQL
- **Responsibilities**:
  - Execute SPARQL queries on social media RDF data
  - Find posts by port, keyword, hashtag, location
  - Discover popular interests and ports from social media
  - Match user interests with social media posts
  - Provide port recommendations based on social media activity
- **Key Methods**:
  - `findPostsByPort()` - Find posts mentioning a port
  - `findPostsByKeyword()` - Find posts by keyword/interest
  - `findPostsByHashtag()` - Find posts by hashtag
  - `findPopularInterests()` - Find trending interests
  - `findPopularPorts()` - Find popular ports from social mentions
  - `findPostsMatchingInterests()` - Match user interests to posts
  - `findRecommendedPortsBySocialMedia()` - Port recommendations from social activity
  - `findPostsByLocation()` - Find posts by location
  - `findPostsByPlatform()` - Find posts by platform
- **Dependencies**:
  - Apache Jena Fuseki (SPARQL endpoint)
  - SPARQL Query Engine

##### **21c. FacebookTokenValidationService**
- **Technology**: Spring Service, RestTemplate
- **Purpose**: Securely validate Facebook access tokens
- **Responsibilities**:
  - Validate Facebook access tokens using Authorization header
  - Handle token expiration and errors
  - Retrieve user information from Facebook Graph API
  - Provide custom exception types for different error scenarios
- **Key Methods**:
  - `validateTokenAndGetUser()` - Validate token and get user info
  - `isTokenExpired()` - Check token expiration (placeholder for caching)
- **Security Features**:
  - Uses Authorization header instead of URL parameters
  - Comprehensive error handling (expired, invalid tokens)
  - Custom exception types with error codes
- **Dependencies**:
  - RestTemplate
  - Facebook Graph API

##### **22. PublisherService**
- **Technology**: Spring Service
- **Purpose**: Manage publisher (local business) operations
- **Responsibilities**:
  - Publisher registration and verification
  - Content publishing
  - Subscriber management
  - Analytics and reporting
- **Key Methods**:
  - `createPublisher()` - Registration
  - `publishContent()` - Publish content
  - `getSubscriberAnalytics()` - Analytics
  - `sendCruiseAlert()` - Send alerts
- **Dependencies**:
  - PublisherRepository
  - SubscriptionRepository
  - NotificationService
  - PortMessageProducer

##### **23. ApiPerformanceService**
- **Technology**: Spring Service, Async
- **Purpose**: Record API performance metrics
- **Responsibilities**:
  - Index API performance metrics to Elasticsearch
  - Track response times, error rates
  - Record user context
- **Key Methods**:
  - `recordApiCall()` - Record API call metrics
- **Dependencies**:
  - Elasticsearch (via HTTP client)
  - ObjectMapper

##### **24. ResourceUtilizationService**
- **Technology**: Spring Service, Scheduled Task
- **Purpose**: Collect system resource metrics
- **Responsibilities**:
  - Collect CPU, memory, disk metrics
  - Collect thread and GC metrics
  - Index to Elasticsearch
- **Key Methods**:
  - `collectAndIndexResourceUtilization()` - Collect and index metrics
- **Dependencies**:
  - Elasticsearch (via HTTP client)
  - ObjectMapper
  - ManagementFactory (JVM metrics)

##### **25. SystemPerformanceService**
- **Technology**: Spring Service
- **Purpose**: Query system performance metrics
- **Responsibilities**:
  - Query API performance stats from Elasticsearch
  - Query resource utilization stats
  - Aggregate statistics
- **Key Methods**:
  - `getApiPerformanceStats()` - Get API performance statistics
  - `getResourceUtilizationStats()` - Get resource statistics
- **Dependencies**:
  - Elasticsearch (via HTTP client)
  - ObjectMapper

##### **26. StatisticsService**
- **Technology**: Spring Service
- **Purpose**: Aggregate and provide statistics
- **Responsibilities**:
  - Aggregate SPARQL query statistics
  - Aggregate RabbitMQ message statistics
  - Provide combined statistics
- **Key Methods**:
  - `getSparqlStats()` - SPARQL statistics
  - `getMessageStats()` - Message statistics
- **Dependencies**:
  - SparqlQueryStatRepository
  - MessageTrackingRepository

##### **27. PublisherService**
- **Technology**: Spring Service
- **Purpose**: Manage publisher operations
- **Dependencies**:
  - PublisherRepository
  - SubscriptionRepository

##### **28. PortMessageProducer**
- **Technology**: Spring Service, RabbitMQ Producer
- **Purpose**: Publish messages to RabbitMQ
- **Responsibilities**:
  - Publish port-related messages
  - Route messages to queues
- **Dependencies**:
  - RabbitTemplate

##### **29. UserMessageConsumer**
- **Technology**: Spring Service, RabbitMQ Consumer
- **Purpose**: Consume user-related messages
- **Responsibilities**:
  - Consume messages from RabbitMQ
  - Process user notifications
  - Track message delivery
- **Dependencies**:
  - MessageTrackingRepository
  - RabbitTemplate

##### **30. EmailService**
- **Technology**: Spring Service
- **Purpose**: Send email notifications
- **Responsibilities**:
  - Send verification emails
  - Send notification emails
- **Dependencies**:
  - JavaMailSender

##### **31. DataInitializationService**
- **Technology**: Spring Service
- **Purpose**: Initialize application data
- **Responsibilities**:
  - Load initial data
  - Seed database
- **Dependencies**:
  - Various repositories

##### **32. DataPersistenceService**
- **Technology**: Spring Service
- **Purpose**: Persist data operations
- **Responsibilities**:
  - Handle data persistence
  - Manage transactions
- **Dependencies**:
  - Various repositories

##### **33. ElasticsearchStatsService**
- **Technology**: Spring Service
- **Purpose**: Elasticsearch statistics operations
- **Responsibilities**:
  - Query Elasticsearch statistics
  - Aggregate Elasticsearch metrics
- **Dependencies**:
  - ElasticsearchOperations

##### **34. KnowledgeGraphSparkService**
- **Technology**: Spring Service, Apache Spark
- **Purpose**: Spark-based knowledge graph processing
- **Responsibilities**:
  - Process knowledge graph with Spark
  - Analyze semantic relationships
- **Dependencies**:
  - SparkSession
  - KnowledgeGraphService

##### **35. SocialMediaIngestionService**
- **Technology**: Spring Service, Scheduled Task, RabbitMQ Producer
- **Purpose**: Ingest social media data from APIs
- **Responsibilities**:
  - Fetch posts from Facebook, Twitter, Instagram APIs
  - Send posts to RabbitMQ for RDF conversion
  - Generate simulated posts for testing
  - Extract hashtags and keywords from posts
- **Key Methods**:
  - `ingestSocialMediaData()` - Scheduled ingestion task
  - `fetchFacebookPosts()` - Fetch from Facebook Graph API
  - `fetchTwitterPosts()` - Fetch from Twitter API
  - `fetchInstagramPosts()` - Fetch from Instagram API
  - `generateSimulatedFacebookPosts()` - Generate test data
- **Dependencies**:
  - RabbitTemplate (send to social.media.queue)
  - RestTemplate (API calls)
  - Facebook/Twitter/Instagram API credentials

##### **36. OpenAisApiClient**
- **Technology**: Spring Service, HTTP Client
- **Purpose**: Client for AIS data providers
- **Responsibilities**:
  - Fetch AIS data from VesselFinder
  - Handle API authentication
  - Parse API responses
- **Dependencies**:
  - HttpClient

#### **Security Components**

##### **37. JwtAuthenticationFilter**
- **Technology**: Spring Security Filter
- **Purpose**: Validate JWT tokens
- **Order**: 2 (after ApiPerformanceFilter)
- **Dependencies**:
  - JwtTokenProvider
  - CustomUserDetailsService

##### **38. ApiPerformanceFilter**
- **Technology**: Spring Security Filter
- **Purpose**: Track API performance
- **Order**: 1 (first filter)
- **Dependencies**:
  - ApiPerformanceService

##### **39. JwtTokenProvider**
- **Technology**: Spring Component
- **Purpose**: Generate and validate JWT tokens
- **Dependencies**:
  - JWT secret configuration

##### **40. CustomUserDetailsService**
- **Technology**: Spring Security Service
- **Purpose**: Load user details for authentication
- **Dependencies**:
  - UserRepository

#### **Configuration Components**

##### **41. SecurityConfig**
- **Technology**: Spring Security Configuration
- **Purpose**: Configure security settings
- **Responsibilities**:
  - Configure JWT authentication
  - Configure filter chain
  - Configure CORS
  - Configure authorization rules

##### **42. ElasticsearchConfig**
- **Technology**: Spring Configuration
- **Purpose**: Configure Elasticsearch connection
- **Dependencies**:
  - Elasticsearch properties

##### **43. RabbitMQConfig**
- **Technology**: Spring AMQP Configuration
- **Purpose**: Configure RabbitMQ connection
- **Dependencies**:
  - RabbitMQ properties

##### **44. PrometheusMetricsConfig**
- **Technology**: Spring Configuration
- **Purpose**: Configure Prometheus metrics
- **Dependencies**:
  - Micrometer

### Data Access Layer

#### **Repositories (Spring Data JPA)**
- **UserRepository** - User data access
- **PassengerRepository** - Passenger data access
- **PassengerInterestRepository** - Interest data access
- **SocialMediaProfileRepository** - Social media data access
- **ShoreExcursionRepository** - Excursion data access
- **MealVenueRepository** - Meal venue data access
- **RestaurantRepository** - Restaurant data access
- **PortRepository** - Port data access
- **CruiseShipRepository** - Ship data access
- **AisDataRepository** - AIS data access
- **RecommendationRepository** - Recommendation data access
- **PublisherRepository** - Publisher data access
- **SubscriptionRepository** - Subscription data access
- **NotificationRepository** - Notification data access
- **MessageTrackingRepository** - Message tracking data access
- **SparqlQueryStatRepository** - SPARQL statistics data access
- **CategoryRepository** - Category data access
- **CruiseScheduleRepository** - Schedule data access
- **EmailVerificationTokenRepository** - Email verification data access

#### **Elasticsearch Repositories**
- **AisDataElasticsearchRepository** - AIS data Elasticsearch access
- **ApiPerformanceDocument** - API performance document model
- **ResourceUtilizationDocument** - Resource utilization document model

### Component Interactions

#### **Passenger Recommendation Request**
1. **PassengerRecommendationController** receives HTTP request
2. Calls **ShoreExcursionRecommendationService.recommendShoreExcursions()**
3. Service queries **PassengerInterestRepository** for interests
4. Service queries **ShoreExcursionRepository** for available excursions
5. Service calculates scores using interest matching
6. Service returns recommendations to controller
7. Controller returns JSON response

#### **AIS Data Processing**
1. **AisDataIngestionService** fetches data from VesselFinder API
2. Service sends data to **RabbitMQ** queue
3. **AisDataService** consumes message from RabbitMQ
4. Service calls **CruiseShipRepository** to find/create ship
5. Service calls **AisDataRepository** to store data
6. Service calls **ElasticsearchOperations** to index data
7. Service updates ship position via **CruiseShipRepository**
8. Service publishes update via **RabbitTemplate** to WebSocket

#### **Social Media Analysis**
1. **SocialMediaAnalysisService** receives analysis request
2. Service queries **SocialMediaProfileRepository** for profiles
3. Service calls **SparkMlService** to process posts
4. **SparkMlService** uses Spark NLP to extract interests
5. Service stores results via **PassengerInterestRepository**
6. Service updates **SocialMediaProfileRepository** with analysis status

#### **Social Media to RDF Conversion**
1. **SocialMediaIngestionService** fetches posts from Facebook/Twitter/Instagram APIs
2. Service sends posts to **RabbitMQ** (`social.media.queue`)
3. **SocialMediaRdfService** consumes messages from RabbitMQ
4. Service converts posts to RDF triples using Apache Jena
5. Service links posts to ports, keywords, hashtags via SKOS concepts
6. Service uploads RDF triples to **Apache Jena Fuseki** via SPARQL UPDATE
7. Posts are now queryable via SPARQL for recommendation enhancement

#### **Social Media RDF Query Flow**
1. **RecommendationService** or **SocialMediaRdfController** needs social media insights
2. Calls **SocialMediaRdfQueryService.findPostsMatchingInterests()**
3. Service executes SPARQL query against **Apache Jena Fuseki**
4. Query finds posts matching user interests, keywords, hashtags
5. Service returns structured results (posts, ports, engagement metrics)
6. Results used to enhance recommendation scores with social proof
7. Query statistics tracked via **SparqlQueryStatRepository**

#### **Facebook Authentication Flow** (Secure)
1. **User** initiates Facebook login via Facebook SDK
2. **Facebook SDK** requests access token from Facebook
3. Token sent to **AuthController.loginWithFacebook()** via POST request
4. Controller validates input using `@Valid` annotation
5. Controller calls **FacebookTokenValidationService.validateTokenAndGetUser()**
6. Service sends token to **Facebook Graph API** via Authorization header (not URL)
7. Service handles token expiration and errors (error codes 190, 102)
8. Service returns user information or throws **FacebookTokenException**
9. Controller sanitizes user input (names, email) to prevent XSS
10. Controller validates Origin header for CSRF protection
11. Controller calls **UserRepository** to find or create user
12. Controller generates JWT token via **JwtTokenProvider**
13. Controller sets secure HTTP-only cookies with SameSite attribute
14. Controller returns authentication response to user

#### **ML Recommendation Generation**
1. **RecommendationService** receives request
2. Service queries **UserRepository** and **PortRepository**
3. Service calls **SparkMlService** to generate recommendations
4. **SparkMlService** loads data from MySQL
5. **SparkMlService** trains/uses ML model
6. **SparkMlService** returns scored recommendations
7. Service stores via **RecommendationRepository**
8. Service caches results in Redis

#### **System Performance Monitoring**
1. **ApiPerformanceFilter** intercepts HTTP request
2. Filter calls **ApiPerformanceService.recordApiCall()**
3. Service indexes metrics to Elasticsearch asynchronously
4. **ResourceUtilizationService** collects metrics on schedule
5. Service indexes to Elasticsearch
6. **StatisticsController** queries **SystemPerformanceService**
7. Service queries Elasticsearch for aggregated statistics
8. Controller returns statistics to admin dashboard

#### **Admin CRUD Operations**
1. **AdminController** receives HTTP request (with ADMIN role)
2. Controller validates JWT token and role
3. Controller calls appropriate repository (e.g., **PortRepository**)
4. Repository performs database operation
5. Controller returns response

### Technology Patterns

#### **Service Layer Pattern**
- Business logic encapsulated in services
- Controllers are thin, delegate to services
- Services coordinate between repositories and external systems

#### **Repository Pattern**
- Spring Data JPA repositories for data access
- Abstraction over database operations
- Custom queries for complex operations

#### **Message-Driven Architecture**
- RabbitMQ for asynchronous processing
- Decouples producers and consumers
- Enables scalable processing

#### **Caching Strategy**
- Redis for frequently accessed data
- Recommendation results cached
- Session data cached
- Real-time positions cached

#### **Filter Chain Pattern**
- ApiPerformanceFilter (order 1) tracks API performance
- JwtAuthenticationFilter (order 2) validates authentication
- Filters execute in order before controllers

#### **RDF/Semantic Web Pattern**
- PortRdfService converts port data to RDF triples
- SocialMediaRdfService converts social media posts to RDF triples
- Both use SKOS concepts for semantic relationships
- SPARQL queries enable semantic search across datasets
- Results enhance recommendations with semantic understanding

#### **Async Processing**
- ApiPerformanceService uses @Async for non-blocking indexing
- Scheduled tasks for periodic operations
- RabbitMQ for message-driven processing

---

*This component diagram shows the internal structure of the API Application container and how components collaborate to deliver functionality.*
