# C4 Level 2 - Container Diagram
## Social Web Recommender for Cruising Ports

### Overview
This container diagram shows the high-level technical building blocks (containers) that make up the Social Web Recommender system. Each container is a separately deployable unit with its own technology stack.

### Containers

#### **1. Web Application**
- **Technology**: Spring Boot 3.2+, Thymeleaf, Bootstrap 5
- **Purpose**: Serves the main web interface and handles user interactions
- **Responsibilities**:
  - Render Thymeleaf templates for passenger-facing UI
  - Display ship tracking dashboards
  - Provide interactive forms for interest expression
  - Show personalized recommendations
- **Interfaces**:
  - HTTP/HTTPS for web requests
  - WebSocket for real-time updates
- **Dependencies**:
  - API Application (REST calls)
  - Redis (session management)

#### **2. API Application**
- **Technology**: Spring Boot 3.2+, REST, OpenAPI 3
- **Purpose**: Provides RESTful API endpoints for all system functionality
- **Responsibilities**:
  - Handle passenger recommendation requests
  - Process AIS data ingestion
  - Manage publisher-subscriber operations
  - Serve API documentation via Swagger
- **Key Endpoints**:
  - `/api/v1/passengers/*` - Passenger-focused recommendations
  - `/api/v1/dashboard/*` - Ship tracking and analytics
  - `/api/v1/recommendations/*` - General recommendations
  - `/api/v1/publishers/*` - Publisher management
- **Interfaces**:
  - REST API (JSON)
  - WebSocket for real-time notifications
- **Dependencies**:
  - MySQL Database
  - Redis Cache
  - RabbitMQ
  - Elasticsearch
  - Spark Cluster (for ML jobs)

#### **3. Spark Cluster**
- **Technology**: Apache Spark 3.5+ (Spark SQL, MLlib, GraphX)
- **Purpose**: Big data processing and machine learning
- **Responsibilities**:
  - Process social media content for interest extraction
  - Train collaborative filtering models
  - Calculate PageRank for social networks
  - Generate long tail recommendations
  - Analyze AIS data at scale
  - Process user behavior patterns
- **Interfaces**:
  - Spark API (Java/Scala)
  - REST API for job submission
- **Dependencies**:
  - MySQL Database (read data)
  - Elasticsearch (read/write AIS data)
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
- **Data Stored**:
  - Passengers, users, interests
  - Ports, attractions, restaurants, activities, shore excursions, meal venues
  - Recommendations, bookings, interactions
  - AIS data, cruise ships, schedules
  - Publishers, subscriptions, notifications
- **Interfaces**:
  - JDBC (via Spring Data JPA)
  - SQL queries
- **Dependencies**: None (data store)

#### **5. Elasticsearch**
- **Technology**: Elasticsearch 8.0+
- **Purpose**: Search engine and analytics database for AIS data
- **Responsibilities**:
  - Index AIS position data for fast geographic queries
  - Store recommendation analytics
  - Store user interaction analytics
  - Enable full-text search on social media content
  - Support Kibana visualizations
- **Data Stored**:
  - AIS position data (time-series)
  - Recommendation metrics
  - User behavior analytics
  - Social media analysis results
- **Interfaces**:
  - REST API (JSON)
  - Elasticsearch Java Client
- **Dependencies**: None (data store)

#### **6. Redis Cache**
- **Technology**: Redis 6.0+
- **Purpose**: In-memory caching and real-time data management
- **Responsibilities**:
  - Cache recommendation results
  - Store session data
  - Manage publisher-subscriber state
  - Cache frequently accessed passenger interests
  - Store real-time ship positions (temporary)
- **Data Stored**:
  - Recommendation cache
  - Session data
  - Subscription state
  - Real-time position updates
- **Interfaces**:
  - Redis Protocol
  - Spring Data Redis
- **Dependencies**: None (cache store)

#### **7. RabbitMQ Message Broker**
- **Technology**: RabbitMQ 3.12+
- **Purpose**: Asynchronous message queuing and event distribution
- **Responsibilities**:
  - Queue AIS data messages
  - Distribute notification events
  - Handle recommendation update events
  - Manage analytics processing queues
- **Queues**:
  - `ais.data.queue` - AIS position updates
  - `notification.queue` - User notifications
  - `recommendation.queue` - Recommendation updates
  - `analytics.queue` - Analytics processing
- **Interfaces**:
  - AMQP Protocol
  - Spring AMQP
- **Dependencies**: None (message broker)

#### **8. Apache Jena (RDF Store)**
- **Technology**: Apache Jena 4.9+
- **Purpose**: Knowledge graph storage and SPARQL query processing
- **Responsibilities**:
  - Store RDF triples for semantic relationships
  - Process SPARQL queries
  - Enhance recommendations with semantic data
  - Integrate with external RDF datasets
- **Data Stored**:
  - RDF triples (port relationships, attraction semantics, etc.)
  - Knowledge graph relationships
- **Interfaces**:
  - SPARQL Protocol
  - Jena API
- **Dependencies**: None (knowledge graph store)

#### **9. Prometheus**
- **Technology**: Prometheus
- **Purpose**: Metrics collection and monitoring
- **Responsibilities**:
  - Collect application metrics
  - Store time-series data
  - Provide metrics endpoint for scraping
- **Metrics Collected**:
  - Ship tracking statistics
  - Recommendation processing times
  - AIS message processing rates
  - Social media analysis performance
  - System resource usage
- **Interfaces**:
  - HTTP endpoint for metrics
  - PromQL query language
- **Dependencies**: None (monitoring system)

#### **10. Grafana**
- **Technology**: Grafana
- **Purpose**: Metrics visualization and dashboards
- **Responsibilities**:
  - Visualize ship tracking data
  - Display recommendation performance metrics
  - Show AIS signal quality distributions
  - Monitor system health
- **Dashboards**:
  - Ship Tracking Dashboard
  - AIS Signal Quality Dashboard
  - Recommendation Performance Dashboard
  - System Health Dashboard
- **Interfaces**:
  - HTTP/HTTPS web interface
  - Prometheus data source
- **Dependencies**: Prometheus (data source)

#### **11. Kibana**
- **Technology**: Kibana
- **Purpose**: Data visualization and analytics dashboards
- **Responsibilities**:
  - Visualize AIS data on maps
  - Display passenger behavior analytics
  - Show recommendation diversity metrics
  - Analyze social media engagement
- **Visualizations**:
  - Real-time Ship Positions Map
  - Passenger Interest Distribution
  - Recommendation Performance Trends
  - Long Tail Item Discovery
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
1. **External AIS Provider** → **RabbitMQ** (publish AIS messages)
2. **RabbitMQ** → **API Application** (consume AIS messages)
3. **API Application** → **MySQL Database** (store AIS data)
4. **API Application** → **Elasticsearch** (index for search)
5. **API Application** → **Redis Cache** (cache current positions)
6. **API Application** → **WebSocket** (broadcast to dashboard)
7. **Grafana** → **Prometheus** (query metrics)
8. **Kibana** → **Elasticsearch** (query AIS data)

#### **Social Media Analysis Flow**
1. **API Application** → **Social Media APIs** (fetch profiles, with consent)
2. **API Application** → **Spark Cluster** (submit analysis job)
3. **Spark Cluster** → **MySQL Database** (read social media profiles)
4. **Spark Cluster** → **MySQL Database** (store extracted interests)
5. **API Application** → **Redis Cache** (cache analysis results)

#### **Publisher-Subscriber Flow**
1. **Publisher** → **API Application** (publish content)
2. **API Application** → **RabbitMQ** (publish notification event)
3. **RabbitMQ** → **API Application** (distribute to subscribers)
4. **API Application** → **Redis Cache** (update subscription state)
5. **API Application** → **WebSocket** (real-time notification)

### Technology Decisions

#### **Why Spring Boot?**
- Rapid development and deployment
- Comprehensive ecosystem
- Strong integration with all required technologies
- Production-ready features (actuator, security)

#### **Why Apache Spark?**
- Handles large-scale social media data processing
- Built-in ML algorithms (collaborative filtering, etc.)
- Graph processing capabilities (PageRank)
- Scalable distributed processing

#### **Why RabbitMQ?**
- Reliable message delivery
- Handles high-volume AIS data streams
- Decouples producers and consumers
- Supports complex routing patterns

#### **Why Elasticsearch?**
- Fast geographic queries for ship positions
- Full-text search on social media content
- Time-series data support
- Integration with Kibana

#### **Why Redis?**
- Sub-millisecond latency for caching
- Pub-sub capabilities
- Session management
- Real-time data storage

### Deployment Considerations

#### **Scalability**
- **API Application**: Horizontal scaling with load balancer
- **Spark Cluster**: Distributed processing across multiple nodes
- **MySQL Database**: Read replicas for query scaling
- **Elasticsearch**: Cluster mode for high availability
- **Redis**: Cluster mode for distributed caching

#### **High Availability**
- All containers can be deployed in redundant configurations
- Database replication for data durability
- Message queue clustering for reliability
- Health checks and auto-recovery

#### **Security**
- API Application: JWT authentication, HTTPS
- Database: Encrypted connections, access control
- Redis: Password protection, network isolation
- Elasticsearch: Security plugin, authentication
- RabbitMQ: TLS, user authentication

---

*This container diagram shows the high-level technical architecture and how containers interact to deliver the complete system functionality.*
