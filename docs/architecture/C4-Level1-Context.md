# C4 Level 1 - Context Diagram
## Social Web Recommender for Cruising Ports

### System Overview
The Social Web Recommender for Cruising Ports is a comprehensive web-based system designed to enhance the cruise tourism experience by providing intelligent recommendations and facilitating communication between cruise passengers, local businesses, and port authorities. The system leverages big data analytics, machine learning, real-time AIS ship tracking, and semantic knowledge graphs to deliver personalized experiences.

### External Entities

#### **Cruise Passengers** (Priority Focus)
- **Role**: Primary users seeking personalized recommendations for ports of call
- **Interactions**: 
  - Voluntarily express interests and preferences (highest priority)
  - Opt-in for social media analysis to enhance recommendations
  - Receive personalized must-see highlights based on interests
  - Get tailored shore excursion recommendations
  - Discover locally active breakfast and lunch venues
  - Browse and receive personalized recommendations
  - Subscribe to publishers (local businesses, attractions)
  - Make reservations and bookings
  - Provide feedback and preferences
  - View real-time ship position via AIS tracking
  - Access interactive port maps with attractions and venues

#### **Local Businesses**
- **Role**: Service providers at cruise ports (restaurants, tour operators, shops)
- **Interactions**:
  - Register as publishers via REST API
  - Publish content, offers, and availability
  - Receive notifications about incoming cruise ships
  - Manage reservations and bookings
  - Update availability and special offers
  - Access analytics and subscriber insights
  - Publish messages to subscribers via RabbitMQ

#### **Port Authorities**
- **Role**: Infrastructure and security management
- **Interactions**:
  - Access admin maintenance portal (ADMIN role required)
  - Manage port data (CRUD operations)
  - Manage cruise ship data
  - Manage meal venues and restaurants
  - Receive advance notifications about cruise arrivals
  - Monitor passenger capacity and infrastructure needs
  - Coordinate security and logistics
  - Access analytics and reporting via statistics dashboard
  - Monitor system performance metrics

#### **System Administrators**
- **Role**: System operations and monitoring
- **Interactions**:
  - Access admin maintenance portal
  - Monitor system performance (API response times, error rates, resource utilization)
  - View Kibana dashboards for analytics
  - View Grafana dashboards for metrics
  - Access Prometheus metrics
  - Monitor RabbitMQ message queues
  - Manage Elasticsearch indices
  - Access SPARQL query statistics
  - Monitor AIS data ingestion

#### **Cruise Lines**
- **Role**: Cruise ship operators
- **Interactions**:
  - Provide ship schedules and passenger estimates (future integration)
  - Integrate with booking systems (future integration)
  - Access passenger preference data (anonymized, future integration)

#### **External Data Sources**
- **Role**: Third-party data providers
- **Interactions**:
  - **VesselFinder API**: AIS ship tracking data (real-time positions, ship metadata)
  - **Google Places API**: Commercial locations and venue information (future integration)
  - **Open source RDF datasets**: Via SPARQL endpoints for semantic data
  - **Weather APIs**: Weather and event data (future integration)
  - **Social Media APIs**: Twitter, Instagram, Facebook, LinkedIn, TikTok for passenger interest analysis (with consent)
  - **Facebook Graph API**: Secure authentication and data access with token validation, CSRF protection
  - **Social Media to RDF**: Automatic conversion of social media posts to RDF triples for semantic querying
  - **Local Business Data Sources**: Venue information and reviews

### Core System Components

#### **Social Web Recommender System**
The main Spring Boot application that orchestrates all functionality:

**Key Responsibilities:**

- **Passenger-Focused Analytics** (Priority):
  - Analyze voluntarily expressed passenger interests
  - Extract interests from social media profiles (with consent)
  - Generate personalized must-see highlights
  - Recommend shore excursions based on interests
  - Suggest locally active breakfast/lunch venues
  - Match passenger interests with available options (40% weight)
  - Prioritize local recommendations (30% weight)

- **Big Data Processing**:
  - Process AIS data using Apache Spark
  - Analyze social media content with Spark NLP
  - Calculate PageRank for social network analysis
  - Generate long tail recommendations
  - Process user behavior patterns

- **Real-Time Tracking**:
  - Process AIS messages via RabbitMQ
  - Index ship positions in Elasticsearch
  - Provide real-time ship tracking dashboards
  - Update ship positions in real-time
  - Handle signal quality and data reliability

- **Machine Learning**:
  - Collaborative filtering with Spark MLlib
  - Interest-based recommendation scoring
  - Multi-factor recommendation algorithms
  - Long tail item discovery

- **Knowledge Graph**: 
  - Construction and management via Apache Jena Fuseki
  - SPARQL query processing
  - Semantic relationship discovery
  - RDF data integration
  - Port RDF dataset creation and management
  - Social media RDF dataset creation from ingested posts
  - Semantic queries on social media content for recommendations

- **Publisher-Subscriber**: 
  - System management with RabbitMQ
  - Real-time notifications via WebSocket
  - Message tracking and analytics

- **System Performance Monitoring**:
  - API performance tracking (response times, error rates)
  - Resource utilization monitoring (CPU, memory, disk, threads, GC)
  - Index metrics to Elasticsearch
  - Provide statistics via REST API

- **Admin Operations**:
  - Comprehensive CRUD operations for ports, ships, venues, restaurants
  - User management and role assignment
  - Statistics and analytics dashboard
  - System configuration management

- **Integration**: 
  - External data sources through REST APIs
  - AIS data ingestion from VesselFinder
  - Social media API integration (with consent)
  - Secure Facebook authentication with token validation
  - Social media data ingestion and RDF conversion
  - SPARQL queries on social media RDF for recommendation enhancement

- **Web Interface**: 
  - Serving via Thymeleaf templates
  - Bootstrap 5 UI components
  - Interactive maps with Leaflet.js
  - Admin maintenance portal

**Key Interactions:**

- **Passenger Interest Collection**:
  - Receives voluntarily expressed interests through profile forms
  - Analyzes social media profiles (with consent) to extract interests
  - Stores interests with confidence scores and sources
  - Combines voluntary and social media interests for comprehensive profile

- **Recommendation Generation**:
  - Matches passenger interests with available options (40% weight)
  - Scores recommendations using multi-factor algorithm
  - Prioritizes locally active venues during port calls
  - Generates personalized must-see highlights
  - Caches results in Redis for performance

- **Real-Time Processing**:
  - Receives AIS data via RabbitMQ queues
  - Processes ship positions with AisDataService
  - Indexes data in Elasticsearch for fast search
  - Updates ship positions in MySQL database
  - Publishes real-time updates via WebSocket
  - Tracks message flow via MessageTracking

- **Analytics & ML**:
  - Processes large datasets with Apache Spark
  - Calculates PageRank for social networks
  - Generates long tail recommendations
  - Analyzes user behavior patterns
  - Trains collaborative filtering models

- **System Monitoring**:
  - Tracks API performance via ApiPerformanceFilter
  - Collects resource utilization metrics via ResourceUtilizationService
  - Indexes metrics to Elasticsearch
  - Provides statistics via StatisticsController
  - Visualizes metrics in Kibana dashboards

- **Data Management**:
  - Stores passenger data and interests in MySQL via JPA
  - Caches recommendations in Redis
  - Indexes AIS data in Elasticsearch
  - Stores RDF triples in Apache Jena Fuseki
  - Manages subscription relationships with real-time updates
  - Coordinates with local businesses and authorities

- **Admin Operations**:
  - Provides CRUD operations for all entities
  - Manages user roles and permissions
  - Provides statistics and analytics
  - Monitors system performance

- **Interface**:
  - Serves web interface and API documentation via Spring MVC
  - Provides Grafana dashboards for monitoring
  - Offers Kibana visualizations for analytics
  - Provides Swagger UI for API exploration
  - Serves admin maintenance portal

### Data Flow Overview

1. **Passenger Interest Collection Flow** (Priority):
   - Passengers voluntarily express interests via profile forms → Stored in MySQL with high confidence
   - Passengers opt-in for social media analysis → Social media APIs accessed (with consent) → Spark processes posts and extracts interests → Interests stored with confidence scores → Combined with voluntary interests for comprehensive profile

2. **Personalized Recommendation Flow**:
   - Port call identified → Passenger interests retrieved (voluntary + social media) → Available options fetched (excursions, venues) → Multi-factor scoring applied (interests 40%, local recommendations 30%, popularity 15%, rating 10%) → Top recommendations cached in Redis → Presented via REST API/Thymeleaf templates

3. **AIS Ship Tracking Flow**:
   - VesselFinder API provides ship positions → AisDataIngestionService fetches data → Data sent to RabbitMQ queue → AisDataService processes messages → Data stored in MySQL and indexed in Elasticsearch → Ship positions updated in real-time → WebSocket broadcasts to dashboard subscribers → Grafana displays on map → Kibana provides analytics

4. **Social Media Analysis Flow**:
   - Passenger consents to social media analysis → SocialMediaAnalysisService fetches profiles → Spark processes posts using NLP → Interests extracted with confidence scores → Sentiment analysis performed → Activity patterns identified → Results stored as PassengerInterest entities → Used for recommendation enhancement

4a. **Social Media to RDF Flow**:
   - SocialMediaIngestionService fetches posts from Facebook/Twitter/Instagram APIs → Posts sent to RabbitMQ queue → SocialMediaRdfService receives messages → Converts posts to RDF triples using SIOC and Schema.org vocabularies → Links posts to ports, keywords, hashtags, interests via SKOS concepts → Uploads RDF triples to Fuseki → SocialMediaRdfQueryService enables SPARQL queries → Queries used for recommendation enhancement → Posts queryable by port, keyword, hashtag, interest

5. **Big Data Analytics Flow**:
   - Large datasets loaded into Spark → Spark SQL analyzes user behavior → Spark MLlib trains recommendation models → PageRank calculated for social networks → Long tail items identified → Results stored and cached → Kibana visualizes analytics

6. **Publisher-Subscriber Flow**: 
   - Local businesses publish content via REST APIs → PortMessageProducer sends to RabbitMQ → UserMessageConsumer processes messages → Spring WebSocket broadcasts notifications → Redis manages subscription state → Real-time updates delivered to subscribers → MessageTracking records flow

7. **System Performance Monitoring Flow**:
   - ApiPerformanceFilter intercepts HTTP requests → Captures performance metrics → ApiPerformanceService indexes to Elasticsearch → ResourceUtilizationService collects system metrics → Indexes to Elasticsearch → StatisticsService aggregates metrics → StatisticsController provides REST API → Kibana visualizes dashboards

8. **RDF/Knowledge Graph Flow**:
   - PortRdfService processes port data → Converts to RDF format → Stores in Apache Jena Fuseki → SPARQL queries executed → Results enhance recommendations → Query statistics tracked → Indexed to Elasticsearch
   - SocialMediaRdfService processes social media posts → Converts to RDF triples → Links to ports and interests → Stores in Fuseki → SPARQL queries enable semantic search → Results enhance recommendation scoring

8a. **Facebook Authentication Flow** (Secure):
   - User initiates Facebook login → Facebook SDK requests access token → Token sent to backend via POST request → FacebookTokenValidationService validates token using Authorization header → Token expiration checked → User information retrieved → Input sanitized → CSRF protection via Origin validation → Secure HTTP-only cookies set → JWT token generated → User authenticated

9. **Admin Operations Flow**:
   - Admin accesses maintenance portal → AdminController provides CRUD operations → Updates entities in MySQL → Changes reflected in real-time → Statistics available via StatisticsController

10. **Data Integration Flow**: 
    - External APIs provide data → Apache Jena processes RDF/SPARQL queries → Spring Data JPA synchronizes MySQL database → Recommendations enhanced with semantic data → Results cached in Redis → Elasticsearch provides fast search

11. **Social Media RDF Query Flow**:
    - Recommendation system needs social media insights → SocialMediaRdfQueryService executes SPARQL queries → Queries social media RDF dataset in Fuseki → Finds posts matching user interests → Identifies popular ports from social mentions → Extracts trending interests → Results combined with port RDF data → Enhanced recommendations generated → Social proof added to recommendation scores

### Technology Stack

#### Core Backend
- **Runtime**: Java 17+
- **Framework**: Spring Boot 3.2.0
- **Database**: MySQL 8.0 for structured data
- **ORM**: Spring Data JPA with Hibernate
- **Cache**: Redis 7.0+ for performance optimization
- **Security**: Spring Security with JWT authentication
  - **Facebook Authentication**: Secure token validation, CSRF protection, input sanitization
  - **Token Management**: Authorization header usage, expiration handling
  - **Cookie Security**: HTTP-only cookies with SameSite attribute
- **Build**: Maven for dependency management

#### Big Data & Analytics
- **Big Data Processing**: Apache Spark 3.5.0 (Spark SQL, MLlib, GraphX)
- **Machine Learning**: Collaborative filtering, long tail recommendations
- **Social Network Analysis**: PageRank algorithm with JGraphT
- **Message Queue**: RabbitMQ 3.12+ for asynchronous AIS data processing
- **Search Engine**: Elasticsearch 8.11.0 for AIS data indexing, analytics, and system monitoring
- **Analytics Visualization**: Kibana 8.11.0 for data dashboards

#### Knowledge & Semantics
- **Knowledge Graph**: Apache Jena Fuseki 5.6.0 for RDF/SPARQL processing
- **Semantic Data**: SPARQL 1.1 queries for enhanced recommendations
- **RDF Storage**: TDB2 for persistent RDF data storage
- **Social Media RDF**: SIOC (Social Media Ontology) and Schema.org vocabularies
- **RDF Conversion**: Automatic conversion of social media posts to RDF triples
- **Semantic Linking**: SKOS concepts for keywords, hashtags, interests

#### Monitoring & Observability
- **Metrics**: Prometheus for metrics collection
- **Dashboards**: Grafana for real-time visualizations
- **Health Checks**: Spring Boot Actuator
- **API Performance**: Custom tracking via ApiPerformanceFilter
- **Resource Monitoring**: Custom collection via ResourceUtilizationService
- **Analytics**: Elasticsearch and Kibana for system performance analysis

#### Real-Time & Communication
- **Real-time Updates**: Spring WebSocket for notifications
- **AIS Tracking**: Real-time ship position monitoring
- **Publisher-Subscriber**: RabbitMQ for message distribution
- **Message Tracking**: Custom tracking for message flow analytics

#### Web & API
- **Web Interface**: Thymeleaf templates with Bootstrap 5
- **Maps**: Leaflet.js for interactive maps
- **APIs**: RESTful APIs with OpenAPI 3 documentation
- **API Documentation**: Swagger UI
- **Admin Portal**: Comprehensive maintenance interface

### Success Metrics

#### Passenger-Focused Metrics (Priority)
- **Recommendation Accuracy**: Match rate between passenger interests and recommendations
- **Interest Coverage**: Percentage of passenger interests successfully matched
- **Social Media Analysis Effectiveness**: Quality of interests extracted from social media
- **Must-See Highlight Relevance**: Passenger satisfaction with highlighted attractions
- **Meal Venue Utilization**: Booking rate for recommended breakfast/lunch venues
- **Shore Excursion Engagement**: Participation rate in recommended excursions
- **User Engagement**: Active passenger participation rate

#### System Performance Metrics
- **AIS Data Processing**: Real-time ship position update latency
- **Recommendation Generation Time**: Average time to generate personalized recommendations
- **Social Media Analysis Time**: Processing time for profile analysis
- **Spark Job Performance**: Big data processing throughput
- **System Scalability**: Concurrent passenger recommendation capacity
- **API Response Times**: Average, p95, p99 response times
- **Error Rates**: HTTP error rates by endpoint and type
- **Resource Utilization**: CPU, memory, disk usage trends

#### Business Metrics
- **User Engagement**: Active passenger participation rate
- **Local Business Revenue**: Increase from recommendations
- **Port Authority Efficiency**: Operational improvements
- **Data Quality**: Knowledge graph completeness and accuracy
- **Recommendation Diversity**: Long tail item discovery rate
- **Publisher Engagement**: Active publishers and subscriber counts
- **Message Delivery Rate**: Successful message delivery percentage

#### Operational Metrics
- **System Uptime**: Application availability percentage
- **Database Performance**: Query response times and throughput
- **Cache Hit Rate**: Redis cache effectiveness
- **Elasticsearch Performance**: Index and search performance
- **RabbitMQ Throughput**: Message processing rate
- **SPARQL Query Performance**: Query success rates and response times

---

*This context diagram provides a high-level view of the system's interactions with external entities and establishes the foundation for more detailed architectural documentation.*
