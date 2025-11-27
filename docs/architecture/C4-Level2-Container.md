# C4 Level 2 - Container Diagram
## Social Web Recommender for Cruising Ports

### Overview
This container diagram shows the high-level technical building blocks (containers) that make up the Social Web Recommender system. Each container is a separately deployable unit with its own technology stack.

### Containers

#### **1. Web Application**
- **Technology**: Spring Boot 3.2.0+, Thymeleaf, Bootstrap 5, Leaflet.js
- **Purpose**: Serves the main web interface and handles user interactions
- **Responsibilities**:
  - Render Thymeleaf templates for passenger-facing UI
  - Display ship tracking dashboards
  - Provide interactive forms for interest expression
  - Show personalized recommendations
  - Display interactive port maps
  - Serve admin maintenance portal
- **Interfaces**:
  - HTTP/HTTPS for web requests
  - WebSocket for real-time updates
- **Dependencies**:
  - API Application (REST calls)
  - Redis (session management)

#### **2. API Application**
- **Technology**: Spring Boot 3.2.0+, REST, OpenAPI 3, Spring MVC
- **Purpose**: Provides RESTful API endpoints for all system functionality
- **Responsibilities**:
  - Handle passenger recommendation requests
  - Process AIS data ingestion
  - Manage publisher-subscriber operations
  - Provide admin CRUD operations
  - Serve statistics and analytics endpoints
  - Handle RDF/SPARQL operations
  - Process social media data and convert to RDF
  - Secure Facebook authentication with token validation
  - Serve API documentation via Swagger
- **Key Endpoints**:
  - `/api/v1/passengers/*` - Passenger-focused recommendations
  - `/api/v1/dashboard/*` - Ship tracking and analytics
  - `/api/v1/admin/*` - Admin operations (ports, ships, venues, restaurants)
  - `/api/v1/admin/stats/*` - Statistics and monitoring
  - `/api/v1/recommendations/*` - General recommendations
  - `/api/v1/publishers/*` - Publisher management
  - `/api/v1/rdf/ports/*` - Port RDF operations
  - `/api/v1/rdf/social-media/*` - Social media RDF queries
  - `/api/v1/auth/*` - Authentication (including secure Facebook login)
- **Interfaces**:
  - REST API (JSON)
  - WebSocket for real-time notifications
- **Dependencies**:
  - MySQL Database
  - Redis Cache
  - RabbitMQ
  - Elasticsearch
  - Spark Cluster (for ML jobs)
  - Apache Jena Fuseki

#### **3. Spark Cluster**
- **Technology**: Apache Spark 3.5.0 (Spark SQL, MLlib, GraphX)
- **Purpose**: Big data processing and machine learning
- **Responsibilities**:
  - Process social media content for interest extraction
  - Train collaborative filtering models
  - Calculate PageRank for social networks
  - Generate long tail recommendations
  - Analyze AIS data at scale
  - Process user behavior patterns
  - Perform NLP analysis on social media posts
- **Interfaces**:
  - Spark API (Java/Scala)
  - REST API for job submission (future)
- **Dependencies**:
  - MySQL Database (read data)
  - Elasticsearch (read/write AIS data and analytics)
- **Deployment**: Can run as standalone cluster or integrated with Spring Boot

#### **4. MySQL Database**
- **Technology**: MySQL 8.0
- **Purpose**: Primary relational database for structured data
- **Responsibilities**:
  - Store passenger profiles and interests
  - Store port, attraction, restaurant, and activity data
  - Store recommendations and user interactions
  - Store AIS data and cruise ship information
  - Store publisher and subscription data
  - Store user accounts and authentication data
  - Store booking and reservation data
  - Store message tracking data
  - Store SPARQL query statistics
- **Data Stored**:
  - Users, passengers, interests, social media profiles
  - Ports, attractions, restaurants, activities, shore excursions, meal venues
  - Recommendations, bookings, interactions
  - AIS data, cruise ships, schedules
  - Publishers, subscriptions, notifications
  - Message tracking, SPARQL query stats
- **Interfaces**:
  - JDBC (via Spring Data JPA)
  - SQL queries
- **Dependencies**: None (data store)

#### **5. Elasticsearch**
- **Technology**: Elasticsearch 8.11.0
- **Purpose**: Search engine and analytics database for AIS data, system monitoring, and analytics
- **Responsibilities**:
  - Index AIS position data for fast geographic queries
  - Store API performance metrics
  - Store resource utilization metrics
  - Store recommendation analytics
  - Store user interaction analytics
  - Enable full-text search on social media content
  - Support Kibana visualizations
  - Store SPARQL query statistics
  - Store RabbitMQ message tracking data
- **Data Stored**:
  - AIS position data (time-series) - `ais-data-*` indices
  - API performance metrics - `api-performance-*` indices
  - Resource utilization metrics - `resource-utilization-*` indices
  - Recommendation metrics - `recommendations-*` indices (future)
  - User behavior analytics - `user-interactions-*` indices (future)
  - Social media analysis results - `social-media-*` indices (future)
  - SPARQL query statistics - `sparql-query-stats-*` indices
  - Message tracking - `message-tracking-*` indices
- **Interfaces**:
  - REST API (JSON)
  - Elasticsearch Java Client
- **Dependencies**: None (data store)

#### **6. Redis Cache**
- **Technology**: Redis 7.0+
- **Purpose**: In-memory caching and real-time data management
- **Responsibilities**:
  - Cache recommendation results
  - Store session data
  - Manage publisher-subscriber state
  - Cache frequently accessed passenger interests
  - Store real-time ship positions (temporary)
  - Cache port data and venue information
- **Data Stored**:
  - Recommendation cache
  - Session data
  - Subscription state
  - Real-time position updates
  - Frequently accessed entities
- **Interfaces**:
  - Redis Protocol
  - Spring Data Redis
- **Dependencies**: None (cache store)

#### **7. RabbitMQ Message Broker**
- **Technology**: RabbitMQ 3.12+ (Management UI)
- **Purpose**: Asynchronous message queuing and event distribution
- **Responsibilities**:
  - Queue AIS data messages
  - Queue social media posts for RDF conversion
  - Distribute notification events
  - Handle recommendation update events
  - Manage analytics processing queues
  - Route publisher-subscriber messages
- **Queues**:
  - `ais.data.queue` - AIS position updates
  - `social.media.queue` - Social media posts for RDF conversion
  - `notification.queue` - User notifications
  - `recommendation.queue` - Recommendation updates
  - `analytics.queue` - Analytics processing
  - `port.messages.queue` - Port publisher messages
  - `user.messages.queue` - User consumer messages
  - `knowledge.graph.queue` - Knowledge graph processing
- **Interfaces**:
  - AMQP Protocol
  - Spring AMQP
  - Management UI (HTTP)
- **Dependencies**: None (message broker)

#### **8. Apache Jena Fuseki (RDF Store)**
- **Technology**: Apache Jena Fuseki 5.6.0
- **Purpose**: Knowledge graph storage and SPARQL query processing
- **Responsibilities**:
  - Store RDF triples for semantic relationships
  - Store social media posts as RDF triples
  - Process SPARQL queries on ports and social media data
  - Enhance recommendations with semantic data
  - Integrate with external RDF datasets
  - Provide SPARQL endpoint for queries
- **Data Stored**:
  - RDF triples (port relationships, attraction semantics, etc.)
  - Social media posts as RDF triples (SIOC, Schema.org vocabularies)
  - Knowledge graph relationships
  - Port semantic data
  - Social media semantic data (keywords, hashtags, interests linked to ports)
- **Interfaces**:
  - SPARQL Protocol (HTTP)
  - SPARQL Query Endpoint
  - SPARQL Update Endpoint
  - Fuseki Admin API
- **Dependencies**: None (knowledge graph store)

#### **9. Prometheus**
- **Technology**: Prometheus
- **Purpose**: Metrics collection and monitoring
- **Responsibilities**:
  - Collect application metrics from Spring Boot Actuator
  - Store time-series data
  - Provide metrics endpoint for scraping
  - Support PromQL queries
- **Metrics Collected**:
  - Ship tracking statistics
  - Recommendation processing times
  - AIS message processing rates
  - Social media analysis performance
  - System resource usage
  - HTTP request metrics
  - JVM metrics (memory, GC, threads)
- **Interfaces**:
  - HTTP endpoint for metrics scraping
  - PromQL query language
  - Prometheus API
- **Dependencies**: None (monitoring system)

#### **10. Grafana**
- **Technology**: Grafana
- **Purpose**: Metrics visualization and dashboards
- **Responsibilities**:
  - Visualize ship tracking data
  - Display recommendation performance metrics
  - Show AIS signal quality distributions
  - Monitor system health
  - Display Prometheus metrics
- **Dashboards**:
  - Ship Tracking Dashboard
  - AIS Signal Quality Dashboard
  - Recommendation Performance Dashboard
  - System Health Dashboard
  - JVM Metrics Dashboard
- **Interfaces**:
  - HTTP/HTTPS web interface
  - Prometheus data source
- **Dependencies**: Prometheus (data source)

#### **11. Kibana**
- **Technology**: Kibana 8.11.0
- **Purpose**: Data visualization and analytics dashboards
- **Responsibilities**:
  - Visualize AIS data on maps
  - Display passenger behavior analytics
  - Show recommendation diversity metrics
  - Analyze social media engagement
  - Display system performance metrics
  - Visualize API performance
  - Display resource utilization
  - Show SPARQL query statistics
  - Visualize RabbitMQ message flow
- **Visualizations**:
  - Real-time Ship Positions Map
  - API Performance Dashboard
  - Resource Utilization Dashboard
  - Passenger Interest Distribution
  - Recommendation Performance Trends
  - Long Tail Item Discovery
  - SPARQL Query Statistics
  - Message Tracking Analytics
- **Interfaces**:
  - HTTP/HTTPS web interface
  - Elasticsearch data source
- **Dependencies**: Elasticsearch (data source)

### Container Interactions

#### **Passenger Recommendation Flow**
1. **Web Application** → **API Application** (HTTP request)
2. **API Application** → **MySQL Database** (query passenger interests)
3. **API Application** → **Redis Cache** (check cached recommendations)
4. **API Application** → **Spark Cluster** (trigger ML recommendation job)
5. **Spark Cluster** → **MySQL Database** (read user interaction data)
6. **Spark Cluster** → **Elasticsearch** (read analytics data)
7. **Spark Cluster** → **API Application** (return recommendations)
8. **API Application** → **Redis Cache** (cache results)
9. **API Application** → **Web Application** (return JSON response)

#### **AIS Data Processing Flow**
1. **External AIS Provider (VesselFinder)** → **API Application** (HTTP API call)
2. **API Application** → **RabbitMQ** (publish AIS messages)
3. **RabbitMQ** → **API Application** (consume AIS messages)
4. **API Application** → **MySQL Database** (store AIS data)
5. **API Application** → **Elasticsearch** (index for search)
6. **API Application** → **Redis Cache** (cache current positions)
7. **API Application** → **WebSocket** (broadcast to dashboard)
8. **Grafana** → **Prometheus** (query metrics)
9. **Kibana** → **Elasticsearch** (query AIS data)

#### **Social Media Analysis Flow**
1. **API Application** → **Social Media APIs** (fetch profiles, with consent)
2. **API Application** → **Spark Cluster** (submit analysis job)
3. **Spark Cluster** → **MySQL Database** (read social media profiles)
4. **Spark Cluster** → **MySQL Database** (store extracted interests)
5. **API Application** → **Redis Cache** (cache analysis results)

#### **Social Media to RDF Flow**
1. **Social Media APIs** (Facebook/Twitter/Instagram) → **API Application** (fetch posts)
2. **API Application** → **RabbitMQ** (publish social media posts to `social.media.queue`)
3. **RabbitMQ** → **SocialMediaRdfService** (consume posts)
4. **SocialMediaRdfService** → **Apache Jena Fuseki** (convert posts to RDF, store triples)
5. **API Application** → **Apache Jena Fuseki** (execute SPARQL queries on social media RDF)
6. **SocialMediaRdfQueryService** → **Recommendation System** (use query results for enhancement)
7. **API Application** → **Elasticsearch** (index SPARQL query statistics)

#### **Facebook Authentication Flow** (Secure)
1. **User** → **Facebook SDK** (initiate login, get access token)
2. **Facebook SDK** → **API Application** (POST `/auth/facebook/login` with token)
3. **API Application** → **FacebookTokenValidationService** (validate token via Authorization header)
4. **FacebookTokenValidationService** → **Facebook Graph API** (verify token, get user info)
5. **API Application** → **Input Sanitization** (sanitize user data, validate email)
6. **API Application** → **CSRF Protection** (validate Origin header)
7. **API Application** → **MySQL Database** (find or create user)
8. **API Application** → **JWT Token Provider** (generate JWT token)
9. **API Application** → **User** (set secure HTTP-only cookies, return JWT)

#### **Publisher-Subscriber Flow**
1. **Publisher** → **API Application** (publish content)
2. **API Application** → **RabbitMQ** (publish notification event)
3. **RabbitMQ** → **API Application** (distribute to subscribers)
4. **API Application** → **Redis Cache** (update subscription state)
5. **API Application** → **WebSocket** (real-time notification)
6. **API Application** → **Elasticsearch** (index message tracking)

#### **System Performance Monitoring Flow**
1. **API Application** → **ApiPerformanceFilter** (intercept HTTP requests)
2. **ApiPerformanceFilter** → **ApiPerformanceService** (async indexing)
3. **ApiPerformanceService** → **Elasticsearch** (index API performance metrics)
4. **ResourceUtilizationService** → **Elasticsearch** (index resource metrics)
5. **StatisticsService** → **Elasticsearch** (aggregate statistics)
6. **Kibana** → **Elasticsearch** (query and visualize metrics)
7. **Grafana** → **Prometheus** (query and visualize metrics)

#### **RDF/Knowledge Graph Flow**
1. **API Application** → **PortRdfService** (process port data)
2. **PortRdfService** → **Apache Jena Fuseki** (store RDF triples)
3. **API Application** → **Apache Jena Fuseki** (execute SPARQL queries on ports)
4. **PortRdfService** → **Elasticsearch** (index query statistics)
5. **Kibana** → **Elasticsearch** (visualize SPARQL statistics)

#### **Social Media RDF Query Flow**
1. **Recommendation System** → **SocialMediaRdfQueryService** (request social media insights)
2. **SocialMediaRdfQueryService** → **Apache Jena Fuseki** (execute SPARQL queries)
3. **Apache Jena Fuseki** → **SocialMediaRdfQueryService** (return query results)
4. **SocialMediaRdfQueryService** → **Recommendation System** (enhance recommendations with social proof)
5. **API Application** → **Elasticsearch** (index query statistics)

#### **Admin Operations Flow**
1. **Web Application** → **API Application** (HTTP request with JWT)
2. **API Application** → **Spring Security** (validate JWT and role)
3. **API Application** → **MySQL Database** (CRUD operations)
4. **API Application** → **Elasticsearch** (update indices if needed)
5. **API Application** → **Web Application** (return response)

### Technology Decisions

#### **Why Spring Boot?**
- Rapid development and deployment
- Comprehensive ecosystem
- Strong integration with all required technologies
- Production-ready features (actuator, security)
- Excellent documentation and community support

#### **Why Apache Spark?**
- Handles large-scale social media data processing
- Built-in ML algorithms (collaborative filtering, etc.)
- Graph processing capabilities (PageRank)
- Scalable distributed processing
- Industry-standard big data framework

#### **Why RabbitMQ?**
- Reliable message delivery
- Handles high-volume AIS data streams
- Decouples producers and consumers
- Supports complex routing patterns
- Management UI for monitoring
- Excellent Spring integration

#### **Why Elasticsearch?**
- Fast geographic queries for ship positions
- Full-text search on social media content
- Time-series data support
- Integration with Kibana
- Excellent for analytics and monitoring
- Horizontal scalability

#### **Why Redis?**
- Sub-millisecond latency for caching
- Pub-sub capabilities
- Session management
- Real-time data storage
- Simple deployment and operation

#### **Why Apache Jena Fuseki?**
- Industry-standard RDF/SPARQL implementation
- Docker support for easy deployment
- TDB2 for persistent storage
- SPARQL 1.1 compliance
- Excellent performance for knowledge graphs

#### **Why Prometheus & Grafana?**
- Industry-standard monitoring stack
- Excellent Spring Boot integration
- Rich visualization capabilities
- PromQL for powerful queries
- Active community and ecosystem

#### **Why Kibana?**
- Native Elasticsearch integration
- Powerful visualization capabilities
- Real-time dashboards
- Easy to use for non-technical users
- Extensive plugin ecosystem

### Deployment Considerations

#### **Scalability**
- **API Application**: Horizontal scaling with load balancer
- **Spark Cluster**: Distributed processing across multiple nodes
- **MySQL Database**: Read replicas for query scaling
- **Elasticsearch**: Cluster mode for high availability
- **Redis**: Cluster mode for distributed caching
- **RabbitMQ**: Cluster mode for high availability

#### **High Availability**
- All containers can be deployed in redundant configurations
- Database replication for data durability
- Message queue clustering for reliability
- Health checks and auto-recovery
- Load balancing for API and web applications

#### **Security**
- **API Application**: 
  - JWT authentication, HTTPS, CORS configuration
  - Secure Facebook authentication with token validation
  - CSRF protection via Origin header validation
  - Input sanitization and XSS prevention
  - Secure HTTP-only cookies with SameSite attribute
- **Database**: Encrypted connections, access control, strong passwords
- **Redis**: Password protection, network isolation
- **Elasticsearch**: Security plugin, authentication (when enabled)
- **RabbitMQ**: TLS, user authentication, virtual host isolation
- **Prometheus/Grafana**: Authentication, network isolation
- **Kibana**: Authentication, network isolation
- **Facebook Integration**: Token validation via Authorization header, expiration handling

#### **Monitoring**
- **Health Checks**: All containers have health check endpoints
- **Metrics**: Prometheus collects metrics from all services
- **Logging**: Centralized logging (can be enhanced with ELK stack)
- **Alerting**: Configurable alerts for critical metrics
- **Dashboards**: Grafana and Kibana for visualization

#### **Data Persistence**
- **MySQL**: Persistent volumes for database data
- **Elasticsearch**: Persistent volumes for indices
- **Redis**: Persistent volumes for AOF (Append Only File)
- **RabbitMQ**: Persistent volumes for message queues
- **Fuseki**: Persistent volumes for TDB2 databases

---

*This container diagram shows the high-level technical architecture and how containers interact to deliver the complete system functionality.*
