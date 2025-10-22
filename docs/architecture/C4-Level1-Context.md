# C4 Level 1 - Context Diagram
## Social Web Recommender for Cruising Ports

### System Overview
The Social Web Recommender for Cruising Ports is a comprehensive web-based system designed to enhance the cruise tourism experience by providing intelligent recommendations and facilitating communication between cruise passengers, local businesses, and port authorities.

### External Entities

#### **Cruise Passengers**
- **Role**: Primary users seeking recommendations for ports of call
- **Interactions**: 
  - Browse and receive personalized recommendations
  - Subscribe to publishers (local businesses, attractions)
  - Make reservations and bookings
  - Provide feedback and preferences

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
  - Social media feeds

### Core System Components

#### **Social Web Recommender System**
The main Spring Boot application that orchestrates all functionality:

**Key Responsibilities:**
- User preference learning and recommendation generation using ML algorithms
- Knowledge graph construction and management via Apache Jena
- Publisher-subscriber system management with Spring WebSocket
- Real-time notification system with Redis caching
- Integration with external data sources through REST APIs
- Web interface serving via Thymeleaf templates

**Key Interactions:**
- Receives user preferences and browsing behavior through REST APIs
- Processes cruise ship arrival data and stores in MySQL via JPA
- Generates personalized recommendations using collaborative filtering
- Manages subscription relationships with real-time updates
- Coordinates with local businesses and authorities through notification system
- Serves web interface and API documentation via Spring MVC

### Data Flow Overview

1. **Recommendation Flow**: Users interact via web interface/REST APIs → Spring Security authenticates → Preferences stored in MySQL via JPA → ML algorithms generate recommendations → Results cached in Redis → Presented via Thymeleaf templates

2. **Publisher-Subscriber Flow**: Local businesses publish content via REST APIs → Spring WebSocket broadcasts notifications → Redis manages subscription state → Real-time updates delivered to subscribers

3. **Cruise Arrival Flow**: Cruise ship schedules received via REST APIs → Passenger estimates calculated → Spring services notify local businesses → Infrastructure and services prepared → Updates stored in MySQL

4. **Data Integration Flow**: External APIs provide data → Apache Jena processes RDF/SPARQL queries → Spring Data JPA synchronizes MySQL database → Recommendations enhanced with semantic data → Results cached in Redis

### Technology Stack
- **Backend**: Spring Boot 3.2+ with Java 17
- **Database**: MySQL 8.0 for structured data
- **ORM**: Spring Data JPA with Hibernate
- **Cache**: Redis for performance optimization
- **Security**: Spring Security with JWT authentication
- **Knowledge Graph**: Apache Jena for RDF/SPARQL processing
- **Real-time**: Spring WebSocket for notifications
- **Web Interface**: Thymeleaf templates with Bootstrap 5
- **APIs**: RESTful APIs with OpenAPI 3 documentation
- **Build**: Maven for dependency management

### Success Metrics
- User engagement and recommendation accuracy
- Local business revenue increase
- Port authority operational efficiency
- System scalability and performance
- Data quality and knowledge graph completeness

---

*This context diagram provides a high-level view of the system's interactions with external entities and establishes the foundation for more detailed architectural documentation.*
