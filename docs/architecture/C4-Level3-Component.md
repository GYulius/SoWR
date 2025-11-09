# C4 Level 3 - Component Diagram
## Social Web Recommender for Cruising Ports

### Overview
This component diagram zooms into the **API Application** container to show the major components (services, controllers, repositories) and how they interact. This represents the internal structure of the Spring Boot application.

### API Application Container Components

#### **1. Passenger Recommendation Controller**
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

#### **2. Dashboard Controller**
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

#### **3. Recommendation Controller**
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

#### **4. Publisher Controller**
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

#### **5. Social Media Analysis Service**
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

#### **6. Shore Excursion Recommendation Service**
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

#### **7. Meal Venue Recommendation Service**
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

#### **8. Recommendation Service**
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

#### **9. AIS Data Service**
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

#### **10. Spark ML Service**
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

#### **11. PageRank Service**
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

#### **12. Knowledge Graph Service**
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

#### **13. Publisher Service**
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

#### **14. Notification Service**
- **Technology**: Spring Service, RabbitMQ, WebSocket
- **Purpose**: Real-time notification distribution
- **Responsibilities**:
  - Send notifications to subscribers
  - Manage notification preferences
  - Broadcast real-time updates
  - Handle WebSocket connections
- **Key Methods**:
  - `notifySubscribers()` - Send to subscribers
  - `sendNotification()` - Individual notification
  - `broadcastUpdate()` - Real-time broadcast
- **Dependencies**:
  - NotificationRepository
  - RabbitTemplate
  - WebSocketHandler

### Data Access Layer

#### **Repositories (Spring Data JPA)**
- **PassengerRepository** - Passenger data access
- **PassengerInterestRepository** - Interest data access
- **SocialMediaProfileRepository** - Social media data access
- **ShoreExcursionRepository** - Excursion data access
- **MealVenueRepository** - Meal venue data access
- **AisDataRepository** - AIS data access
- **CruiseShipRepository** - Ship data access
- **RecommendationRepository** - Recommendation data access
- **UserRepository** - User data access
- **PortRepository** - Port data access
- **PublisherRepository** - Publisher data access
- **SubscriptionRepository** - Subscription data access

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
1. **AisDataService** consumes message from RabbitMQ
2. Service calls **CruiseShipRepository** to find/create ship
3. Service calls **AisDataRepository** to store data
4. Service calls **ElasticsearchOperations** to index data
5. Service updates ship position via **CruiseShipRepository**
6. Service publishes update via **RabbitTemplate** to WebSocket

#### **Social Media Analysis**
1. **SocialMediaAnalysisService** receives analysis request
2. Service queries **SocialMediaProfileRepository** for profiles
3. Service calls **SparkMlService** to process posts
4. **SparkMlService** uses Spark NLP to extract interests
5. Service stores results via **PassengerInterestRepository**
6. Service updates **SocialMediaProfileRepository** with analysis status

#### **ML Recommendation Generation**
1. **RecommendationService** receives request
2. Service queries **UserRepository** and **PortRepository**
3. Service calls **SparkMlService** to generate recommendations
4. **SparkMlService** loads data from MySQL
5. **SparkMlService** trains/uses ML model
6. **SparkMlService** returns scored recommendations
7. Service stores via **RecommendationRepository**
8. Service caches results in Redis

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

---

*This component diagram shows the internal structure of the API Application container and how components collaborate to deliver functionality.*
