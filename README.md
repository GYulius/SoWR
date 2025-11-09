# Social Web Recommender for Cruising Ports

<div align="center">

![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)
![License](https://img.shields.io/badge/license-MIT-green.svg)
![Java](https://img.shields.io/badge/java-%3E%3D17.0.0-brightgreen.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2+-green.svg)
![MySQL](https://img.shields.io/badge/MySQL-8.0-orange.svg)
![Redis](https://img.shields.io/badge/Redis-6.0+-red.svg)

**An intelligent Spring Boot recommendation system for cruise passengers, local businesses, and port authorities**

[Features](#-features) • [Architecture](#-architecture) • [Quick Start](#-quick-start) • [API Documentation](#-api-documentation) • [Contributing](#-contributing) • [License](#-license)

</div>

---

## 📋 Table of Contents

- [Overview](#-overview)
- [Features](#-features)
- [Architecture](#-architecture)
- [Technology Stack](#-technology-stack)
- [Quick Start](#-quick-start)
- [Installation](#-installation)
- [Configuration](#-configuration)
- [API Documentation](#-api-documentation)
- [Database Schema](#-database-schema)
- [Development Roadmap](#-development-roadmap)
- [Contributing](#-contributing)
- [Support](#-support)
- [License](#-license)

## 🌟 Overview

The **Social Web Recommender for Cruising Ports** is a comprehensive Spring Boot application designed to revolutionize the cruise tourism experience. By leveraging RDF knowledge graphs, machine learning, and real-time publisher-subscriber systems, it provides intelligent recommendations for cruise passengers while enabling local businesses and port authorities to prepare for incoming ships.

### Key Capabilities

- 🎯 **Passenger-Focused Recommendations**: AI-powered personalized suggestions based on voluntary interests and social media analysis
- 📱 **Social Media Analysis**: Extract interests from passenger digital presence (with consent)
- 🏛️ **Must-See Highlights**: Personalized touristic attractions based on passenger interests
- 🚢 **Shore Excursion Recommendations**: Tailored excursion suggestions matching passenger preferences
- 🍽️ **Meal Venue Recommendations**: Locally active breakfast and lunch venues during port calls
- 📡 **Real-time Notifications**: Publisher-subscriber system for instant updates
- 🗺️ **Knowledge Graph Integration**: RDF/SPARQL-based semantic data processing
- 🚢 **AIS Ship Tracking**: Real-time cruise ship position monitoring with signal quality handling
- 📊 **Big Data Analytics**: Apache Spark for ML, graph analysis, and long tail recommendations
- 🔍 **PageRank Analysis**: Social network analysis for influence detection
- 🏢 **Business Intelligence**: Analytics for local businesses and port authorities
- 🔒 **Security & Privacy**: GDPR-compliant data handling and user privacy protection

## ✨ Features

### For Cruise Passengers (Priority Focus)
- **Personalized Recommendations**: AI-powered suggestions based on voluntarily expressed interests
- **Social Media Integration**: Optional analysis of digital presence to enhance recommendations (with consent)
- **Must-See Highlights**: Personalized touristic attractions matching your interests
- **Shore Excursion Recommendations**: Tailored excursion suggestions for each port of call
- **Meal Venue Recommendations**: Locally active breakfast and lunch venues during port calls
- **Interest-Based Matching**: Recommendations weighted by your expressed preferences (40% weight)
- **Local Recommendations**: Prioritizes venues recommended by locals (30% weight)
- **Interactive Web Interface**: Beautiful Thymeleaf-based UI with Bootstrap 5
- **Booking Integration**: Seamless reservations for restaurants, tours, and activities
- **Real-time Updates**: Live notifications about port activities and offers
- **Ship Tracking**: View real-time position of your cruise ship via AIS

### For Local Businesses
- **Publisher Dashboard**: Manage content, offers, and availability via REST APIs
- **Cruise Arrival Alerts**: Advance notifications about incoming ships and passenger estimates
- **Analytics & Insights**: Track customer preferences and booking patterns
- **Dynamic Pricing**: Adjust rates based on demand and capacity
- **Customer Management**: Track reservations and customer preferences

### For Port Authorities
- **Infrastructure Planning**: Monitor capacity and resource requirements
- **Security Coordination**: Real-time passenger tracking and safety management
- **Economic Impact**: Analyze tourism revenue and business performance
- **Emergency Response**: Rapid communication system for incidents
- **Regulatory Compliance**: Automated reporting and documentation

## 🏗️ Architecture

### System Components

```mermaid
graph TB
    subgraph "Web Layer"
        A[Thymeleaf Templates] --> B[Bootstrap 5 UI]
        A --> C[Swagger UI]
        A --> D1[Grafana Dashboards]
        A --> D2[Kibana Analytics]
    end
    
    subgraph "API Layer"
        E[Spring MVC Controllers] --> F[REST Endpoints]
        E --> G[OpenAPI Documentation]
        E1[PassengerRecommendationController] --> F
        E2[DashboardController] --> F
    end
    
    subgraph "Core Services - Passenger Focused"
        H1[SocialMediaAnalysisService] --> H2[Interest Extraction]
        H3[ShoreExcursionService] --> H4[Personalized Recommendations]
        H5[MealVenueService] --> H6[Breakfast/Lunch Venues]
        H7[RecommendationService] --> H8[Multi-Factor Scoring]
    end
    
    subgraph "Big Data & Analytics"
        I1[SparkMlService] --> I2[ML Processing]
        I3[PageRankService] --> I4[Social Network Analysis]
        I5[AisDataService] --> I6[Ship Tracking]
        I1 --> I7[Long Tail Recommendations]
    end
    
    subgraph "Data Layer"
        J[Spring Data JPA] --> K[MySQL Database]
        J --> L[Redis Cache]
        J --> M[Elasticsearch]
        N[RabbitMQ] --> I5
        O[Apache Jena] --> P[RDF Store]
    end
    
    A --> E
    B --> E
    C --> G
    E1 --> H3
    E1 --> H5
    E2 --> I5
    H1 --> I1
    H2 --> H7
    H3 --> H7
    H5 --> H7
    H7 --> I1
    I1 --> J
    I3 --> J
    I5 --> N
    I5 --> M
    H7 --> K
    H7 --> L
```

### Data Flow

1. **Passenger Interest Collection** (Priority):
   - Passengers voluntarily express interests → Stored in MySQL with high confidence
   - Social media analysis (with consent) → Spark extracts interests → Combined profile created
   
2. **Personalized Recommendation Generation**:
   - Port call identified → Passenger interests retrieved → Multi-factor scoring applied
   - Interest match (40%) + Local recommendations (30%) + Popularity (15%) + Rating (10%)
   - Results cached in Redis → Presented via REST API
   
3. **AIS Ship Tracking**:
   - AIS transceivers → RabbitMQ queue → Spark processing → MySQL + Elasticsearch
   - Real-time updates via WebSocket → Grafana dashboard visualization
   
4. **Big Data Analytics**:
   - Spark processes large datasets → MLlib trains models → PageRank calculated
   - Long tail recommendations generated → Kibana visualizes results
   
5. **Knowledge Graph** → Apache Jena processes RDF data and SPARQL queries
6. **Publisher-Subscriber** → Spring WebSocket distributes real-time notifications
7. **Database** → Spring Data JPA stores and analyzes all interactions

## 🛠️ Technology Stack

### Backend
- **Runtime**: Java 17+
- **Framework**: Spring Boot 3.2+
- **Database**: MySQL 8.0
- **Cache**: Redis 6.0+
- **ORM**: Spring Data JPA / Hibernate
- **Security**: Spring Security with JWT

### API Documentation
- **Framework**: SpringDoc OpenAPI 3
- **UI**: Swagger UI
- **Documentation**: OpenAPI Specification

### Data & AI
- **Knowledge Graph**: Apache Jena / Virtuoso
- **RDF Processing**: SPARQL 1.1
- **Big Data Processing**: Apache Spark 3.5+ (Spark SQL, MLlib, GraphX)
- **ML Framework**: Collaborative filtering, long tail recommendations, social network analysis
- **Social Media Analysis**: Multi-platform support (Twitter, Instagram, Facebook, LinkedIn, TikTok)
- **PageRank Algorithm**: Social network influence analysis
- **AIS Data Processing**: Real-time ship tracking and analytics

### Infrastructure
- **Containerization**: Docker & Docker Compose
- **Orchestration**: Kubernetes ready
- **Message Queue**: RabbitMQ for asynchronous processing
- **Search Engine**: Elasticsearch for AIS data and analytics
- **Monitoring**: Prometheus & Grafana for metrics and dashboards
- **Analytics**: Kibana for data visualization
- **Logging**: SLF4J with Logback

## 🚀 Quick Start

### Prerequisites

- Java 17 or higher
- Maven 3.8+
- MySQL 8.0 or higher
- Redis 6.0 or higher
- RabbitMQ 3.12+ (for message queuing)
- Elasticsearch 8.0+ (for AIS data search)
- Apache Spark 3.5+ (for big data processing, optional for local development)
- Prometheus & Grafana (for monitoring, optional)
- Docker and Docker Compose (optional, recommended)

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/your-org/social-web-recommender.git
   cd social-web-recommender
   ```

2. **Set up environment variables**
   ```bash
   cp src/main/resources/application.yml.example src/main/resources/application.yml
   # Edit application.yml with your configuration
   ```

3. **Initialize the database**
   ```bash
   # Create database and run migrations
   mysql -u root -p -e "CREATE DATABASE cruise_recommender;"
   mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"
   ```

4. **Start the application**
   ```bash
   mvn spring-boot:run
   ```

The application will be available at:
- **Web Interface**: `http://localhost:8080`
- **API Documentation**: `http://localhost:8080/swagger-ui.html`
- **Health Check**: `http://localhost:8080/actuator/health`

## ⚙️ Configuration

### Application Configuration

```yaml
# Database Configuration
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/cruise_recommender
    username: cruise_app
    password: TBD
    driver-class-name: com.mysql.cj.jdbc.Driver

# Redis Configuration
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: TBD

# External APIs
google:
  maps:
    api:
      key: your_google_maps_key
weather:
  api:
    key: TBD
sparql:
  endpoint: https://your-sparql-endpoint.com/sparql

# Security
jwt:
  secret: TBD
  expiration: 604800000

# Notification Services
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: your_email
    password: TBD
```

## 📚 API Documentation

### Authentication
All API endpoints require authentication via JWT tokens.

### Core Endpoints

#### Recommendations
```http
GET /api/v1/recommendations
POST /api/v1/recommendations/feedback
GET /api/v1/recommendations/history
POST /api/v1/recommendations/refresh
GET /api/v1/recommendations/explain/{id}
```

#### Publishers
```http
GET /api/v1/publishers
POST /api/v1/publishers
GET /api/v1/publishers/{id}
PUT /api/v1/publishers/{id}
POST /api/v1/publishers/{id}/content
GET /api/v1/publishers/{id}/subscribers
```

#### Subscriptions
```http
GET /api/v1/subscriptions
POST /api/v1/subscriptions
DELETE /api/v1/subscriptions/{id}
PUT /api/v1/subscriptions/{id}
GET /api/v1/subscriptions/{id}/notifications
```

#### Passenger-Focused Recommendations (Priority)
```http
GET /api/v1/passengers/{passengerId}/recommendations
GET /api/v1/passengers/{passengerId}/shore-excursions
GET /api/v1/passengers/{passengerId}/must-see-highlights
GET /api/v1/passengers/{passengerId}/breakfast-venues
GET /api/v1/passengers/{passengerId}/lunch-venues
POST /api/v1/passengers/{passengerId}/analyze-social-media
```

#### Ship Tracking Dashboard
```http
GET /api/v1/dashboard/ships/positions
GET /api/v1/dashboard/ships/near-port
GET /api/v1/dashboard/ships/{id}/tracking
GET /api/v1/dashboard/ships/statistics
```

#### Ports
```http
GET /api/v1/ports
GET /api/v1/ports/{id}
GET /api/v1/ports/{id}/capacity
GET /api/v1/ports/{id}/cruises
```

### Response Format
```json
{
  "success": true,
  "data": {
    // Response data
  },
  "meta": {
    "timestamp": "2024-01-01T00:00:00Z",
    "version": "1.0.0"
  }
}
```

## 🗄️ Database Schema

### Core Tables

#### Users
```sql
CREATE TABLE users (
  id INT PRIMARY KEY AUTO_INCREMENT,
  email VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  preferences JSON,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

#### Ports
```sql
CREATE TABLE ports (
  id INT PRIMARY KEY AUTO_INCREMENT,
  port_code VARCHAR(10) UNIQUE NOT NULL,
  name VARCHAR(255) NOT NULL,
  country VARCHAR(100) NOT NULL,
  coordinates POINT NOT NULL,
  capacity INT NOT NULL,
  facilities JSON,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### Recommendations
```sql
CREATE TABLE recommendations (
  id INT PRIMARY KEY AUTO_INCREMENT,
  user_id INT NOT NULL,
  port_id INT NOT NULL,
  item_type ENUM('attraction', 'restaurant', 'activity', 'shop'),
  item_id INT NOT NULL,
  score DECIMAL(3,2) NOT NULL,
  reasoning TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id),
  FOREIGN KEY (port_id) REFERENCES ports(id)
);
```

## 📅 Development Roadmap

### Week 1-2: Foundation & Core Setup
- [x] **Project Structure**: Set up Spring Boot architecture
- [x] **Database Design**: Implement MySQL schema with JPA
- [x] **API Framework**: Create RESTful API endpoints
- [x] **Authentication**: Implement JWT-based auth system
- [x] **Basic CRUD**: User, port, and recommendation operations
- [x] **Passenger Entity**: Core passenger-focused data model
- [x] **Interest Tracking**: Voluntary and social media-based interests

### Week 3-4: Passenger-Focused Analytics & Recommendations
- [x] **Social Media Analysis**: Multi-platform interest extraction
- [x] **Shore Excursion Service**: Personalized excursion recommendations
- [x] **Meal Venue Service**: Breakfast/lunch venue recommendations
- [x] **Interest-Based Scoring**: Multi-factor recommendation algorithm
- [x] **Must-See Highlights**: Personalized touristic attractions
- [ ] **RDF Integration**: Set up Apache Jena SPARQL endpoint
- [ ] **Data Import**: Import ports_A.json and external data
- [ ] **ML Pipeline**: Implement recommendation algorithms with Spark
- [ ] **Knowledge Graph**: Build semantic relationships
- [ ] **Testing**: Unit and integration tests

### Week 5-6: Advanced Analytics & Big Data
- [x] **AIS Ship Tracking**: Real-time position monitoring
- [x] **RabbitMQ Integration**: Message queuing for AIS data
- [x] **Elasticsearch Setup**: AIS data indexing and search
- [x] **Spark ML Integration**: Big data processing and ML
- [x] **PageRank Service**: Social network analysis
- [x] **Long Tail Recommendations**: Niche item discovery
- [ ] **Prometheus Metrics**: Comprehensive monitoring setup
- [ ] **Grafana Dashboards**: Ship tracking visualizations
- [ ] **Kibana Analytics**: Passenger behavior analytics
- [ ] **Publisher-Subscriber System**: Real-time notification system with WebSocket

### Week 5-6: Cruise Integration & Advanced Features
- [ ] **Cruise API**: Integration with cruise line systems
- [ ] **Capacity Management**: Port authority dashboard
- [ ] **Booking System**: Reservation and payment processing
- [ ] **Mobile Responsiveness**: Enhanced mobile experience
- [ ] **Performance Optimization**: Caching and scaling

### Week 7-8: Production & Deployment
- [ ] **Security Audit**: Penetration testing and hardening
- [ ] **Performance Testing**: Load testing and optimization
- [ ] **Documentation**: Complete API and user documentation
- [ ] **Deployment**: Production environment setup
- [ ] **Monitoring**: Logging and alerting systems

## 🎯 Passenger-Focused Recommendation Workflow

### Interest Collection
1. **Voluntary Expression**: Passengers fill out profile forms with interests (highest priority)
2. **Social Media Consent**: Passengers opt-in for social media analysis
3. **Interest Extraction**: System analyzes social media profiles using Spark NLP
4. **Confidence Scoring**: All interests stored with confidence scores and sources

### Recommendation Generation
1. **Port Call Detection**: System identifies upcoming port arrival
2. **Interest Matching**: Match passenger interests with available options
3. **Multi-Factor Scoring**:
   - Interest Match: 40% weight
   - Local Recommendations: 30% weight
   - Popularity: 15% weight
   - Rating: 10% weight
   - Accessibility/Budget: 5% weight
4. **Personalization**: Filter and rank based on passenger preferences

### Recommendation Types
- **Must-See Highlights**: Top touristic attractions personalized by interests
- **Shore Excursions**: Tailored excursion suggestions matching preferences
- **Breakfast Venues**: Locally active breakfast spots during port calls
- **Lunch Venues**: Locally active lunch spots during port calls

## 🤝 Contributing

We welcome contributions from the community! Please follow these guidelines:

### Getting Started
1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Commit your changes: `git commit -m 'Add amazing feature'`
4. Push to the branch: `git push origin feature/amazing-feature`
5. Open a Pull Request

### Development Guidelines
- Follow the existing code style and conventions
- Write comprehensive tests for new features
- Update documentation for API changes
- Ensure all tests pass before submitting PR

### Code Style
- Use Spring Boot best practices
- Follow Java naming conventions
- Use meaningful variable and method names
- Add Javadoc for public methods
- Maintain test coverage above 80%

## 📞 Support

### Documentation
- [API Documentation](http://localhost:8080/swagger-ui.html)
- [Architecture Documentation](docs/architecture/C4-Level1-Context.md)
- [Contributing Guide](CONTRIBUTING.md)

### Community
- [GitHub Discussions](https://github.com/your-org/social-web-recommender/discussions)
- [Issues](https://github.com/your-org/social-web-recommender/issues)
- [Security](SECURITY.md)

### Commercial Support
For enterprise support and custom implementations, contact us at:
- Email: enterprise@TBD
- Phone: TBD

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

### Third-Party Licenses
- [Spring Boot](https://spring.io/projects/spring-boot)
- [Apache Jena](https://jena.apache.org/)
- [MySQL](https://www.mysql.com/about/legal/licensing/)
- [Redis](https://redis.io/license)

---

<div align="center">

**Made with ❤️ for the cruise tourism community**

[Website](https://TBD) • [Blog](https://blog.TBD) • [Twitter](https://twitter.com/TBD)

</div>