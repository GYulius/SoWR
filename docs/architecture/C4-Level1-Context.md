# C4 Level 1 - Context Diagram
## Social Web Recommender for Cruising Ports

### System Overview
The Social Web Recommender for Cruising Ports is a comprehensive web-based system designed to enhance the cruise tourism experience by providing intelligent recommendations and facilitating communication between cruise passengers, local businesses, and port authorities.

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

#### **Local Businesses**
- **Role**: Service providers at cruise ports
- **Interactions**:
  - Publish content and offers
  - Receive notifications about incoming cruise ships
  - Manage reservations and bookings
  - Update availability and special offers

#### **Port Authorities**
- **Role**: Infrastructure and security management
- **Interactions**:
  - Receive advance notifications about cruise arrivals
  - Monitor passenger capacity and infrastructure needs
  - Coordinate security and logistics
  - Access analytics and reporting

#### **Cruise Lines**
- **Role**: Cruise ship operators
- **Interactions**:
  - Provide ship schedules and passenger estimates
  - Integrate with booking systems
  - Access passenger preference data (anonymized)

#### **External Data Sources**
- **Role**: Third-party data providers
- **Interactions**:
  - Google Places API for commercial locations
  - Open source RDF datasets via SPARQL
  - Weather and event data APIs
  - Social media APIs (Twitter, Instagram, Facebook, LinkedIn, TikTok) for passenger interest analysis
  - AIS (Automatic Identification System) data providers for ship tracking
  - Local business data sources for venue information

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
- **Big Data Processing**:
  - Process AIS data using Apache Spark
  - Analyze social media content with Spark NLP
  - Calculate PageRank for social network analysis
  - Generate long tail recommendations
- **Real-Time Tracking**:
  - Process AIS messages via RabbitMQ
  - Index ship positions in Elasticsearch
  - Provide real-time ship tracking dashboards
- **Machine Learning**:
  - Collaborative filtering with Spark MLlib
  - Interest-based recommendation scoring
  - Multi-factor recommendation algorithms
- **Knowledge Graph**: Construction and management via Apache Jena
- **Publisher-Subscriber**: System management with Spring WebSocket
- **Notifications**: Real-time system with Redis caching
- **Integration**: External data sources through REST APIs
- **Web Interface**: Serving via Thymeleaf templates

**Key Interactions:**
- **Passenger Interest Collection**:
  - Receives voluntarily expressed interests through profile forms
  - Analyzes social media profiles (with consent) to extract interests
  - Stores interests with confidence scores and sources
- **Recommendation Generation**:
  - Matches passenger interests with available options (40% weight)
  - Scores recommendations using multi-factor algorithm
  - Prioritizes locally active venues during port calls
  - Generates personalized must-see highlights
- **Real-Time Processing**:
  - Receives AIS data via RabbitMQ queues
  - Processes ship positions with Spark
  - Indexes data in Elasticsearch for fast search
  - Publishes real-time updates via WebSocket
- **Analytics & ML**:
  - Processes large datasets with Apache Spark
  - Calculates PageRank for social networks
  - Generates long tail recommendations
  - Analyzes user behavior patterns
- **Data Management**:
  - Stores passenger data and interests in MySQL via JPA
  - Caches recommendations in Redis
  - Manages subscription relationships with real-time updates
  - Coordinates with local businesses and authorities
- **Interface**:
  - Serves web interface and API documentation via Spring MVC
  - Provides Grafana dashboards for monitoring
  - Offers Kibana visualizations for analytics

### Data Flow Overview

1. **Passenger Interest Collection Flow** (Priority):
   - Passengers voluntarily express interests via profile forms → Stored in MySQL with high confidence
   - Passengers opt-in for social media analysis → Social media APIs accessed (with consent) → Spark processes posts and extracts interests → Interests stored with confidence scores → Combined with voluntary interests for comprehensive profile

2. **Personalized Recommendation Flow**:
   - Port call identified → Passenger interests retrieved (voluntary + social media) → Available options fetched (excursions, venues) → Multi-factor scoring applied (interests 40%, local recommendations 30%, popularity 15%, rating 10%) → Top recommendations cached in Redis → Presented via REST API/Thymeleaf templates

3. **AIS Ship Tracking Flow**:
   - AIS transceivers broadcast ship positions → AIS stations receive signals → Data sent to RabbitMQ queue → AisDataService processes messages → Data stored in MySQL and indexed in Elasticsearch → Ship positions updated in real-time → WebSocket broadcasts to dashboard subscribers → Grafana displays on map

4. **Social Media Analysis Flow**:
   - Passenger consents to social media analysis → SocialMediaAnalysisService fetches profiles → Spark processes posts using NLP → Interests extracted with confidence scores → Sentiment analysis performed → Activity patterns identified → Results stored as PassengerInterest entities → Used for recommendation enhancement

5. **Big Data Analytics Flow**:
   - Large datasets loaded into Spark → Spark SQL analyzes user behavior → Spark MLlib trains recommendation models → PageRank calculated for social networks → Long tail items identified → Results stored and cached → Kibana visualizes analytics

6. **Publisher-Subscriber Flow**: Local businesses publish content via REST APIs → Spring WebSocket broadcasts notifications → Redis manages subscription state → Real-time updates delivered to subscribers

7. **Data Integration Flow**: External APIs provide data → Apache Jena processes RDF/SPARQL queries → Spring Data JPA synchronizes MySQL database → Recommendations enhanced with semantic data → Results cached in Redis

### Technology Stack

#### Core Backend
- **Runtime**: Java 17+
- **Framework**: Spring Boot 3.2+
- **Database**: MySQL 8.0 for structured data
- **ORM**: Spring Data JPA with Hibernate
- **Cache**: Redis 6.0+ for performance optimization
- **Security**: Spring Security with JWT authentication
- **Build**: Maven for dependency management

#### Big Data & Analytics
- **Big Data Processing**: Apache Spark 3.5+ (Spark SQL, MLlib, GraphX)
- **Machine Learning**: Collaborative filtering, long tail recommendations
- **Social Network Analysis**: PageRank algorithm with JGraphT
- **Message Queue**: RabbitMQ for asynchronous AIS data processing
- **Search Engine**: Elasticsearch 8.0+ for AIS data indexing
- **Analytics Visualization**: Kibana for data dashboards

#### Knowledge & Semantics
- **Knowledge Graph**: Apache Jena for RDF/SPARQL processing
- **Semantic Data**: SPARQL 1.1 queries for enhanced recommendations

#### Monitoring & Observability
- **Metrics**: Prometheus for metrics collection
- **Dashboards**: Grafana for real-time visualizations
- **Health Checks**: Spring Boot Actuator

#### Real-Time & Communication
- **Real-time Updates**: Spring WebSocket for notifications
- **AIS Tracking**: Real-time ship position monitoring
- **Publisher-Subscriber**: RabbitMQ for message distribution

#### Web & API
- **Web Interface**: Thymeleaf templates with Bootstrap 5
- **APIs**: RESTful APIs with OpenAPI 3 documentation
- **API Documentation**: Swagger UI

### Success Metrics

#### Passenger-Focused Metrics (Priority)
- **Recommendation Accuracy**: Match rate between passenger interests and recommendations
- **Interest Coverage**: Percentage of passenger interests successfully matched
- **Social Media Analysis Effectiveness**: Quality of interests extracted from social media
- **Must-See Highlight Relevance**: Passenger satisfaction with highlighted attractions
- **Meal Venue Utilization**: Booking rate for recommended breakfast/lunch venues
- **Shore Excursion Engagement**: Participation rate in recommended excursions

#### System Performance Metrics
- **AIS Data Processing**: Real-time ship position update latency
- **Recommendation Generation Time**: Average time to generate personalized recommendations
- **Social Media Analysis Time**: Processing time for profile analysis
- **Spark Job Performance**: Big data processing throughput
- **System Scalability**: Concurrent passenger recommendation capacity

#### Business Metrics
- **User Engagement**: Active passenger participation rate
- **Local Business Revenue**: Increase from recommendations
- **Port Authority Efficiency**: Operational improvements
- **Data Quality**: Knowledge graph completeness and accuracy
- **Recommendation Diversity**: Long tail item discovery rate

---

*This context diagram provides a high-level view of the system's interactions with external entities and establishes the foundation for more detailed architectural documentation.*
