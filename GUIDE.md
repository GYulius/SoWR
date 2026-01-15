# Social Web Recommender for Cruising Ports - Comprehensive Guide

<div align="center">

![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)
![License](https://img.shields.io/badge/license-MIT-green.svg)
![Java](https://img.shields.io/badge/java-%3E%3D17.0.0-brightgreen.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-green.svg)

**An intelligent Spring Boot recommendation system for cruise passengers, local businesses, and port authorities**

[Quick Start](#-quick-start) • [Architecture](#-architecture) • [Facebook Integration](#-facebook-integration) • [API Documentation](#-api-documentation) • [Development](#-development) • [Troubleshooting](#-troubleshooting)

</div>

---

## 📋 Table of Contents

- [Overview](#-overview)
- [Quick Start](#-quick-start)
- [Architecture](#-architecture)
- [Facebook Integration](#-facebook-integration)
- [Configuration](#-configuration)
- [API Documentation](#-api-documentation)
- [Development](#-development)
- [Testing](#-testing)
- [Monitoring & Observability](#-monitoring--observability)
- [Troubleshooting](#-troubleshooting)
- [Additional Resources](#-additional-resources)

---

## 🌟 Overview

The **Social Web Recommender for Cruising Ports** is a comprehensive Spring Boot application designed to revolutionize the cruise tourism experience. By leveraging RDF knowledge graphs, machine learning, real-time AIS ship tracking, and advanced analytics, it provides intelligent recommendations for cruise passengers while enabling local businesses and port authorities to prepare for incoming ships.

### Key Capabilities

- 🎯 **Passenger-Focused Recommendations**: AI-powered personalized suggestions based on voluntary interests and social media analysis
- 📱 **Social Media Integration**: Extract interests from passenger digital presence (with consent)
- 🔗 **Social Media to RDF Integration**: Automatic conversion of social media posts (Facebook, Twitter, Instagram) to RDF triples for semantic querying
- 🏛️ **Must-See Highlights**: Personalized touristic attractions based on passenger interests
- 🚢 **Shore Excursion Recommendations**: Tailored excursion suggestions matching passenger preferences
- 🍽️ **Meal Venue Recommendations**: Locally active breakfast and lunch venues during port calls
- 📡 **Real-time AIS Ship Tracking**: Live cruise ship position monitoring with signal quality handling
- 🗺️ **Knowledge Graph Integration**: RDF/SPARQL-based semantic data processing via Apache Jena Fuseki
- 📊 **Big Data Analytics**: Apache Spark for ML, graph analysis, and long tail recommendations
- 🔍 **PageRank Analysis**: Social network analysis for influence detection
- 🏢 **Business Intelligence**: Analytics for local businesses and port authorities
- 📈 **System Performance Monitoring**: Comprehensive API performance, error tracking, and resource utilization monitoring
- 🔒 **Security & Privacy**: GDPR-compliant data handling, JWT authentication, secure Facebook integration, and user privacy protection

---

## 🚀 Quick Start

### Prerequisites

- **Java**: 17 or higher
- **Maven**: 3.8+
- **MySQL**: 8.0 or higher
- **Redis**: 7.0+ (or Docker)
- **RabbitMQ**: 3.12+ (or Docker)
- **Elasticsearch**: 8.11.0 (or Docker)
- **Docker & Docker Compose**: (recommended)

### Installation Steps

1. **Clone the repository**
   ```bash
   git clone https://github.com/GYulius/SoWR.git
   cd SoWR
   ```

2. **Set up environment variables**
   ```bash
   cp env.example .env
   # Edit .env with your configuration
   ```

3. **Start infrastructure services with Docker Compose**
   ```bash
   docker-compose up -d redis rabbitmq elasticsearch kibana prometheus grafana fuseki
   ```

4. **Initialize the database**
   ```bash
   mysql -u root -p -e "CREATE DATABASE cruise_recommender;"
   # Run migrations if available
   ```

5. **Configure AIS data source (Optional)**
   
   **Option A: VesselFinder (Recommended)**
   - Get API key from https://www.vesselfinder.com/
   - Free account includes DEFAULT FLEET with 10 ships
   - Paid account for a FLEET up to 500 ships
   - Set `AIS_API_KEY` in `application.yml`
   
   **Option B: Use Simulation Mode**
   - Set `ais.data.simulation.enabled: true` in `application.yml`

6. **Start the application**
   ```bash
   mvn spring-boot:run
   ```

### Access Points

Once running, access the application at:

- **Web Interface**: `http://localhost:8080`
- **API Base**: `http://localhost:8080/api/v1`
- **API Documentation**: `http://localhost:8080/swagger-ui.html`
- **Health Check**: `http://localhost:8080/actuator/health`
- **Prometheus Metrics**: `http://localhost:8080/actuator/prometheus`
- **Kibana**: `http://localhost:5601`
- **Grafana**: `http://localhost:3001`
- **RabbitMQ Management**: `http://localhost:15672`
- **Fuseki SPARQL**: `http://localhost:3030`

For detailed setup instructions, see [docs/STARTUP_GUIDE.md](docs/STARTUP_GUIDE.md).

---

## 🏗️ Architecture

### System Overview

The system follows a microservices-oriented architecture with clear separation of concerns:

```
┌─────────────────────────────────────────────────────────────────┐
│                    Web Application Layer                        │
│(Thymeleaf Templates, Bootstrap 5 UI, Leaflet/VesselFinder Maps) │
└──────────────────────┬──────────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                    API Application Layer                    │
│  (Spring MVC Controllers, REST Endpoints, OpenAPI)          │
└──────────────────────┬──────────────────────────────────────┘
                       │
        ┌──────────────┼──────────────┐
        │              │              │
┌───────▼──────┐ ┌─────▼──────┐ ┌─────▼──────┐
│ Core Services│ │ Big Data   │ │ Monitoring │
│              │ │ Analytics  │ │            │
└──────────────┘ └────────────┘ └────────────┘
        │              │              │
┌───────▼──────────────────────────────────────┐
│            Data Layer                        │
│  (MySQL, Redis, Elasticsearch, RabbitMQ)     │
└──────────────────────────────────────────────┘
```

### Key Components

#### 1. **Web Layer**
- **Thymeleaf Templates**: Server-side rendering
- **Bootstrap 5 UI**: Modern, responsive interface
- **Swagger UI**: Interactive API documentation
- **Grafana Dashboards**: Real-time monitoring
- **Kibana Analytics**: Data visualization
- **Admin Maintenance Portal**: CRUD operations

#### 2. **API Layer**
- **Spring MVC Controllers**: REST endpoint handlers
- **OpenAPI Documentation**: Auto-generated API docs
- **Key Controllers**:
  - `PassengerRecommendationController`: Passenger-focused recommendations
  - `DashboardController`: Ship tracking and analytics
  - `AdminController`: Administrative operations
  - `StatisticsController`: System statistics
  - `PortRdfController`: RDF/SPARQL operations
  - `AuthController`: Authentication (including Facebook)

#### 3. **Core Services**
- **SocialMediaAnalysisService**: Interest extraction from social media
- **ShoreExcursionService**: Personalized excursion recommendations
- **MealVenueService**: Breakfast/lunch venue recommendations
- **RecommendationService**: Multi-factor scoring algorithm
- **FacebookInterestService**: Facebook likes extraction and processing

#### 4. **Big Data & Analytics**
- **SparkMlService**: ML processing with Spark MLlib
- **PageRankService**: Social network analysis
- **AisDataService**: Real-time ship tracking
- **Long Tail Recommendations**: Niche item discovery

#### 5. **Data Layer**
- **MySQL**: Primary relational database
- **Redis**: Caching layer
- **Elasticsearch**: Search and analytics
- **RabbitMQ**: Message queuing
- **Apache Jena Fuseki**: RDF knowledge graph storage

For detailed architecture documentation, see:
- [C4 Level 1 - Context](docs/architecture/C4-Level1-Context.md)
- [C4 Level 2 - Container](docs/architecture/C4-Level2-Container.md)
- [C4 Level 3 - Component](docs/architecture/C4-Level3-Component.md)

---

## 📱 Facebook Integration

### Current Status

✅ **Fully Functional** - Facebook login and interest extraction are implemented and ready for testing.

#### What's Working

1. **Facebook Login Authentication**
   - Frontend: Facebook SDK integration (`social-login.js`)
   - Backend: Token validation (`FacebookTokenValidationService`)
   - Endpoint: `POST /api/v1/auth/facebook/login`
   - Permissions: Requests `email`, `public_profile`, and `user_likes`

2. **User Creation & Authentication**
   - Creates/updates user from Facebook profile
   - Stores: email, name, Facebook user ID
   - Generates JWT token for session
   - Marks email as verified (Facebook verified)

3. **Facebook Interest Extraction Service**
   - Service: `FacebookInterestService`
   - Fetches user's liked pages from Facebook Graph API
   - Extracts interests from page categories
   - Maps to port interest categories
   - Stores as `PassengerInterest` entities

4. **Integration with Recommendation Orchestrator**
   - Orchestrator automatically uses Facebook interests
   - Queries `PassengerInterest` repository (includes Facebook interests)
   - Generates recommendations based on Facebook likes

### Setup for Testing

To test with a real Facebook account **without requiring Facebook App Review**:

1. **Create/Configure Facebook App**
   - Go to [Facebook Developers Console](https://developers.facebook.com/)
   - Create a new app or use existing one
   - Add your test account as **Developer** or **Administrator** role

2. **Configure Permissions**
   - Go to **App Review** → **Permissions and Features**
   - Request `user_likes` permission
   - Fill in use case: "Personalized cruise port recommendations based on user interests"

3. **Set Environment Variables**
   ```bash
   FACEBOOK_APP_ID=your_app_id_here
   FACEBOOK_APP_SECRET=your_app_secret_here
   ```

4. **Test Login Flow**
   - Click "Login with Facebook" in the web interface
   - Grant permissions (including `user_likes`)
   - Verify interests are extracted and stored

For detailed setup instructions, see:
- [Facebook Testing Setup Guide](docs/FACEBOOK_TESTING_SETUP.md)
- [Facebook Status Summary](docs/FACEBOOK_STATUS_SUMMARY.md)
- [Facebook Demo Checklist](docs/FACEBOOK_DEMO_CHECKLIST.md)

### Permission Requirements

- **`email`**: User's email address ✅
- **`public_profile`**: Basic profile information ✅
- **`user_likes`**: Liked pages for interest extraction ⚠️ (requires app review for production)

**Note**: For development/testing, developers/admins/testers can use `user_likes` without formal review.

### Data Flow

```
1. User clicks "Login with Facebook"
   ↓
2. Facebook SDK requests permissions
   - email ✅
   - public_profile ✅
   - user_likes ⚠️ (needs approval for production)
   ↓
3. User grants permissions
   ↓
4. Backend validates token
   ↓
5. User created/updated in database
   ↓
6. JWT token generated
   ↓
7. Fetch Facebook interests (async)
   - GET /me/likes?fields=name,category
   - Extract interests
   - Store as PassengerInterest
   ↓
8. User logged in ✅
```

### Security Features

- ✅ Token validation using Authorization header (not URL parameters)
- ✅ CSRF protection via Origin header validation and SameSite cookies
- ✅ Input sanitization to prevent XSS attacks
- ✅ Secure HTTP-only cookies with SameSite attribute
- ✅ GDPR-compliant data handling

---

## ⚙️ Configuration

### Application Configuration

Key configuration files:
- `src/main/resources/application.yml` - Main application configuration
- `docker-compose.yml` - Docker services configuration
- `.env` - Environment variables (create from `env.example`)

### Key Configuration Sections

#### Database
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/cruise_recommender
    username: ${DB_USERNAME:cruise_app}
    password: ${DB_PASSWORD:cruise_password}
```

#### Facebook Integration
```yaml
social:
  media:
    facebook:
      app:
        id: ${FACEBOOK_APP_ID:}
        secret: ${FACEBOOK_APP_SECRET:}
```

#### Elasticsearch
```yaml
elasticsearch:
  enabled: true
  host: localhost
  port: 9200
```

#### AIS Data Source
```yaml
ais:
  data:
    source:
      api:
        url: https://www.vesselfinder.com/api
        key: ${AIS_API_KEY:}
        provider: VESSELFINDER
```

#### Knowledge Graph
```yaml
knowledge:
  graph:
    endpoint: http://localhost:3030/cruise_kg/sparql
    username: admin
    password: admin
```

#### Monitoring
```yaml
monitoring:
  api-performance:
    enabled: true
  resource-collection-interval: 60000  # 1 minute
```

For complete configuration options, see:
- [Configuration Security Guide](docs/CONFIGURATION_SECURITY.md)
- [Environment Configuration](docs/ENV_CONFIGURATION.md)

---

## 📚 API Documentation

### Authentication

All API endpoints (except public endpoints) require authentication via JWT tokens.

#### Standard Login
```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password"
}
```

#### Facebook Login
```http
POST /api/v1/auth/facebook/login
Content-Type: application/json

{
  "accessToken": "facebook_access_token",
  "userId": "facebook_user_id",
  "name": "User Name",
  "email": "user@example.com"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "email": "user@example.com",
  "userId": 1,
  "role": "USER",
  "firstName": "John",
  "lastName": "Doe"
}
```

### Core Endpoints

#### Passenger Recommendations
```http
GET /api/v1/passengers/{passengerId}/recommendations?portId={portId}
GET /api/v1/passengers/{passengerId}/shore-excursions?portId={portId}
GET /api/v1/passengers/{passengerId}/must-see-highlights?portId={portId}
GET /api/v1/passengers/{passengerId}/breakfast-venues?portId={portId}
GET /api/v1/passengers/{passengerId}/lunch-venues?portId={portId}
POST /api/v1/passengers/{passengerId}/analyze-social-media
```

#### Facebook Interests
```http
POST /api/v1/facebook/interests/fetch
Content-Type: application/json
Authorization: Bearer {jwt_token}

{
  "accessToken": "facebook_access_token"
}
```

#### Ship Tracking Dashboard
```http
GET /api/v1/dashboard/ships/positions
GET /api/v1/dashboard/ships/near-port?portId={portId}&radiusKm={radius}
GET /api/v1/dashboard/ships/{id}/tracking
GET /api/v1/dashboard/ships/statistics
```

#### Admin Operations (ADMIN role required)
```http
# Ports Management
GET /api/v1/admin/ports
POST /api/v1/admin/ports
PUT /api/v1/admin/ports/{id}
DELETE /api/v1/admin/ports/{id}

# Similar endpoints for ships, meal-venues, restaurants
```

#### RDF/SPARQL Operations
```http
# Port RDF Operations
GET /api/v1/rdf/ports/{portId}
POST /api/v1/rdf/ports/{portId}/query
GET /api/v1/rdf/ports/by-country?country={country}

# Social Media RDF Operations
GET /api/v1/rdf/social-media/posts/by-port?portCode={code}
GET /api/v1/rdf/social-media/posts/by-keyword?keyword={keyword}
POST /api/v1/rdf/social-media/posts/matching-interests
```

Complete API documentation is available at `http://localhost:8080/swagger-ui.html` when the application is running.

---

## 💻 Development

### Development Setup

1. **IDE Setup**
   - IntelliJ IDEA recommended
   - Install Lombok plugin
   - Configure Java 17 SDK
   - See [IntelliJ Setup Guide](docs/INTELLIJ_SETUP.md)

2. **Build the Project**
   ```bash
   mvn clean install
   ```

3. **Run Tests**
   ```bash
   mvn test
   ```

4. **Run in Debug Mode**
   ```bash
   mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
   ```
   See [Debug Guide](docs/RUN_SPRING_BOOT_DEBUG.md)

### Code Structure

```
src/
├── main/
│   ├── java/com/cruise/recommender/
│   │   ├── controller/      # REST controllers
│   │   ├── service/         # Business logic services
│   │   ├── repository/      # Data access layer
│   │   ├── entity/          # JPA entities
│   │   ├── config/          # Configuration classes
│   │   └── security/       # Security configuration
│   └── resources/
│       ├── static/          # Static resources (JS, CSS)
│       ├── templates/       # Thymeleaf templates
│       └── application.yml  # Application configuration
└── test/                    # Test files
```

### Development Guidelines

- Follow Spring Boot best practices
- Use meaningful variable and method names
- Add Javadoc for public methods
- Maintain test coverage above 80%
- Follow Java naming conventions

### Key Services

- **FacebookInterestService**: Fetches and processes Facebook likes
- **RecommendationOrchestratorService**: Coordinates recommendation generation
- **SocialMediaAnalysisService**: Analyzes social media content
- **AisDataService**: Processes AIS ship tracking data
- **SparkMlService**: Machine learning processing

---

## 🧪 Testing

### Facebook Integration Testing

1. **Setup Test Account**
   - Add your Facebook account as Developer/Admin in Facebook App
   - Ensure account has liked pages (restaurants, attractions, etc.)

2. **Test Login Flow**
   ```bash
   # 1. Click "Login with Facebook" in web UI
   # 2. Grant permissions (including user_likes)
   # 3. Verify login successful
   ```

3. **Verify Interests Extracted**
   ```sql
   SELECT * FROM passenger_interests 
   WHERE source = 'SOCIAL_MEDIA' 
   ORDER BY created_at DESC;
   ```

4. **Test Manual Interest Fetching**
   ```bash
   POST /api/v1/facebook/interests/fetch
   {
     "accessToken": "facebook_access_token"
   }
   ```

5. **Test Recommendations**
   ```bash
   POST /api/v1/orchestrator/recommendations/generate?passengerId=123&portId=456
   ```

### Testing Checklist

- [ ] Facebook login works
- [ ] Permission dialog shows `user_likes`
- [ ] Interests are extracted and stored
- [ ] Recommendations use Facebook interests
- [ ] Error handling works correctly
- [ ] Security features are enforced

For detailed testing guides, see:
- [Facebook Testing Setup](docs/FACEBOOK_TESTING_SETUP.md)
- [Facebook Demo Checklist](docs/FACEBOOK_DEMO_CHECKLIST.md)

---

## 📊 Monitoring & Observability

### System Performance Monitoring

The system includes comprehensive monitoring capabilities:

#### API Performance Tracking
- **Filter**: `ApiPerformanceFilter` intercepts all HTTP requests/responses
- **Metrics Captured**: Endpoint, method, HTTP status, response time, success/failure
- **User Context**: Email, role, client IP, user agent
- **Storage**: Indexed to Elasticsearch (`api-performance-*` indices)
- **Visualization**: Kibana dashboards for API performance analysis

#### Resource Utilization Monitoring
- **Service**: `ResourceUtilizationService` collects metrics every 60 seconds
- **Metrics Captured**:
  - CPU usage percentage and system load average
  - Memory: heap, non-heap, system memory usage
  - Threads: active, peak, daemon thread counts
  - Garbage Collection: collection count and time
  - Disk: total, free, usage percentage
- **Storage**: Indexed to Elasticsearch (`resource-utilization-*` indices)
- **Visualization**: Kibana dashboards for resource monitoring

#### Elasticsearch Indices
- `ais-data-*`: AIS ship tracking data
- `api-performance-*`: API performance metrics
- `resource-utilization-*`: System resource metrics
- `sparql-query-stats-*`: SPARQL query performance
- `message-tracking-*`: RabbitMQ message flow

#### Monitoring Dashboards

**Kibana Dashboards**:
- System Performance Dashboard: API endpoints performance, response times, error rates
- Resource Utilization Dashboard: CPU, memory, disk, threads, GC metrics
- AIS Ship Tracking Dashboard: Real-time ship positions and tracking
- SPARQL Query Statistics Dashboard: Query performance and success rates
- RabbitMQ Message Tracking Dashboard: Message flow and delivery metrics

**Grafana Dashboards**:
- Real-time system health and performance visualizations
- Prometheus metrics integration

**Admin Statistics Dashboard**:
- Accessible at `/admin/maintenance` (ADMIN role required)
- Displays API performance, resource utilization, SPARQL stats, message tracking
- Quick links to external monitoring tools

For detailed monitoring documentation, see:
- [System Performance Monitoring](docs/SYSTEM_PERFORMANCE_MONITORING.md)
- [Kibana Setup Guide](docs/KIBANA_SETUP_GUIDE.md)

---

## 🔧 Troubleshooting

### Common Issues

#### Facebook Login Issues

**Issue**: "user_likes permission required" Error
- **Solution**: Ensure your Facebook account is added as Developer/Admin/Tester
- **Check**: Verify permission request includes `user_likes` in scope
- **Verify**: Check browser console for permission status

**Issue**: Permission Dialog Doesn't Show `user_likes`
- **Solution**: Clear browser cache and cookies, try again
- **Check**: Verify `social-login.js` requests `user_likes` permission
- **Verify**: Check Facebook SDK version compatibility

**Issue**: "App Not Setup" Error
- **Solution**: Verify `FACEBOOK_APP_ID` is correct
- **Check**: Ensure OAuth Redirect URIs are configured
- **Verify**: Check App Domains settings

#### Database Issues

**Issue**: Connection refused
- **Solution**: Ensure MySQL is running
- **Check**: Verify database credentials in `application.yml`
- **Verify**: Check database exists: `CREATE DATABASE cruise_recommender;`

#### Elasticsearch Issues

**Issue**: Elasticsearch connection failed
- **Solution**: Ensure Elasticsearch is running (`docker-compose up elasticsearch`)
- **Check**: Verify Elasticsearch host/port in configuration
- **Verify**: Check Elasticsearch health: `curl http://localhost:9200/_cluster/health`

#### AIS Data Issues

**Issue**: No ship tracking data
- **Solution**: Configure VesselFinder API key or enable simulation mode
- **Check**: Verify `AIS_API_KEY` is set or `ais.data.simulation.enabled: true`
- **Verify**: Check RabbitMQ is running and AIS queue is active

### Getting Help

1. **Check Logs**: Application logs are in `logs/app.log`
2. **Review Documentation**: See relevant docs in `docs/` folder
3. **Check Health Endpoint**: `GET /actuator/health`
4. **Review API Docs**: `http://localhost:8080/swagger-ui.html`

For more troubleshooting guides, see:
- [Facebook Troubleshooting](docs/FACEBOOK_TESTING_SETUP.md#troubleshooting)
- [RDF Troubleshooting](docs/TROUBLESHOOTING_RDF.md)
- [Configuration Issues](docs/CONFIGURATION_SECURITY.md#troubleshooting)

---

## 📖 Additional Resources

### Documentation

- [README.md](README.md) - Project overview and quick reference
- [Architecture Documentation](docs/architecture/) - C4 model diagrams
- [Facebook Integration](docs/FACEBOOK_STATUS_SUMMARY.md) - Facebook features status
- [Data Pipeline](docs/DATA_PIPELINE.md) - Data flow documentation
- [Orchestrator](docs/ORCHESTRATOR.md) - Recommendation orchestration

### Setup Guides

- [Startup Guide](docs/STARTUP_GUIDE.md) - Complete startup instructions
- [Docker Setup](docs/DOCKER_SETUP.md) - Docker configuration
- [Environment Configuration](docs/ENV_CONFIGURATION.md) - Environment variables
- [IntelliJ Setup](docs/INTELLIJ_SETUP.md) - IDE configuration

### Feature-Specific Guides

- [Facebook Testing Setup](docs/FACEBOOK_TESTING_SETUP.md) - Facebook integration testing
- [Facebook Demo Checklist](docs/FACEBOOK_DEMO_CHECKLIST.md) - Demo recording guide
- [RDF Integration](docs/RDF_INTEGRATION_SUMMARY.md) - Knowledge graph setup
- [Spark ML Integration](docs/SPARKML_RDF_INTEGRATION.md) - Machine learning setup
- [System Performance](docs/SYSTEM_PERFORMANCE_MONITORING.md) - Monitoring setup

### Security & Configuration

- [Configuration Security](docs/CONFIGURATION_SECURITY.md) - Security best practices
- [Facebook Security Fixes](docs/FACEBOOK_SECURITY_FIXES.md) - Security improvements
- [Facebook Privacy Policy](docs/FACEBOOK_PRIVACY_POLICY_TEMPLATE.md) - Privacy template

### External Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Facebook Developers](https://developers.facebook.com/)
- [Apache Jena Documentation](https://jena.apache.org/documentation/)
- [Apache Spark Documentation](https://spark.apache.org/docs/latest/)
- [Elasticsearch Documentation](https://www.elastic.co/guide/en/elasticsearch/reference/current/index.html)

---

## 🎯 Current Development Status

### Completed Features ✅

- [x] Project Structure: Spring Boot architecture
- [x] Database Design: MySQL schema with JPA
- [x] API Framework: RESTful API endpoints
- [x] Authentication: JWT-based auth system
- [x] Facebook Login: Secure social authentication
- [x] Facebook Interest Extraction: Automatic interest fetching
- [x] Passenger Entity: Core passenger-focused data model
- [x] Interest Tracking: Voluntary and social media-based interests
- [x] AIS Ship Tracking: Real-time position monitoring
- [x] RabbitMQ Integration: Message queuing for AIS data
- [x] Elasticsearch Setup: AIS data indexing and search
- [x] Spark ML Integration: Big data processing and ML
- [x] PageRank Service: Social network analysis
- [x] Long Tail Recommendations: Niche item discovery
- [x] System Performance Monitoring: API performance and resource utilization tracking
- [x] Kibana Dashboards: System performance visualizations
- [x] Admin Maintenance Portal: Comprehensive CRUD operations
- [x] RDF/SPARQL Integration: Apache Jena Fuseki integration
- [x] Social Media to RDF Integration: Automatic conversion of social media posts to RDF triples
- [x] Social Media SPARQL Queries: Semantic querying of social media data

### In Progress 🚧

- [ ] Enhanced Social Media Analysis: Advanced multi-platform interest extraction
- [ ] Shore Excursion Service: Personalized excursion recommendations
- [ ] Meal Venue Service: Breakfast/lunch venue recommendations
- [ ] Interest-Based Scoring: Multi-factor recommendation algorithm with social media RDF integration
- [ ] Must-See Highlights: Personalized touristic attractions

### Planned Features 📋

- [ ] ML Pipeline: Implement recommendation algorithms with Spark
- [ ] Knowledge Graph: Build semantic relationships
- [ ] Testing: Unit and integration tests
- [ ] Publisher-Subscriber System: Real-time notification system with WebSocket
- [ ] Cruise API: Integration with cruise line systems
- [ ] Capacity Management: Port authority dashboard
- [ ] Booking System: Reservation and payment processing
- [ ] Mobile Responsiveness: Enhanced mobile experience
- [ ] Performance Optimization: Caching and scaling

---

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

---

## 📞 Support

### Documentation

- [API Documentation](http://localhost:8080/swagger-ui.html) - Interactive API docs
- [Architecture Documentation](docs/architecture/) - System architecture
- [Contributing Guide](CONTRIBUTING.md) - How to contribute
- [Security Policy](SECURITY.md) - Security guidelines

### Community

- [GitHub Discussions](https://github.com/your-org/social-web-recommender/discussions) - Community discussions
- [Issues](https://github.com/your-org/social-web-recommender/issues) - Bug reports and feature requests

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

### Third-Party Licenses

- [Spring Boot](https://spring.io/projects/spring-boot)
- [Apache Jena](https://jena.apache.org/)
- [MySQL](https://www.mysql.com/about/legal/licensing/)
- [Redis](https://redis.io/license)
- [Elasticsearch](https://www.elastic.co/licensing)
- [Apache Spark](https://www.apache.org/licenses/)

---

<div align="center">

**Made with ❤️ for the cruise tourism community**

[Website](https://TBD) • [Blog](https://blog.TBD) • [Twitter](https://twitter.com/TBD)

</div>
