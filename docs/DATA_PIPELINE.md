# Complete Data Pipeline Documentation

## Overview

This document describes the complete data ingestion, processing, and visualization pipeline for the Social Web Recommender system. The pipeline integrates AIS transceiver data, social media data (Facebook, Twitter, Instagram), processes them through Apache Spark with Apache Jena for Knowledge Graph construction, and visualizes results on Grafana, Kibana, and Leaflet maps.

## Architecture

```
External APIs/Webhooks
    ↓
[AIS Data Ingestion] ──┐
    ↓                   │
[Social Media Ingestion]│
    ↓                   │
[RabbitMQ Queues] ←─────┘
    ↓
[Knowledge Graph Processing (Spark + Jena)]
    ↓
[RDF/SPARQL Processing]
    ↓
[Data Persistence Service]
    ├──→ MySQL (Structured Data)
    └──→ Elasticsearch (Search & Analytics)
    ↓
[Visualization]
    ├──→ Grafana (Metrics)
    ├──→ Kibana (Elasticsearch Analytics)
    └──→ Leaflet Maps (Frontend)
```

## Components

### 1. AIS Data Ingestion Service

**File**: `src/main/java/com/cruise/recommender/service/AisDataIngestionService.java`

**Purpose**: Fetches AIS (Automatic Identification System) data from external APIs or generates simulated data for testing.

**Features**:
- Scheduled ingestion every 30 seconds (configurable)
- Supports external API integration (MarineTraffic, VesselFinder, etc.)
- Webhook endpoint for receiving AIS data from external systems
- Simulated data generation for development/testing

**Configuration** (`application.yml`):
```yaml
ais:
  data:
    source:
      api:
        url: ${AIS_API_URL:}
        key: ${AIS_API_KEY:}
    simulation:
      enabled: ${AIS_SIMULATION_ENABLED:true}
    ingestion:
      interval: ${AIS_INGESTION_INTERVAL:30000}
```

**RabbitMQ Queue**: `ais.data.queue`

### 2. Social Media Ingestion Service

**File**: `src/main/java/com/cruise/recommender/service/SocialMediaIngestionService.java`

**Purpose**: Fetches passenger interest data from Facebook, Twitter, and Instagram APIs.

**Features**:
- Scheduled ingestion every 10 minutes (configurable)
- Supports Facebook Graph API, Twitter API v2, Instagram Basic Display API
- Simulated data generation for development/testing
- Extracts keywords, hashtags, and locations from posts

**Configuration** (`application.yml`):
```yaml
social:
  media:
    facebook:
      app:
        id: ${FACEBOOK_APP_ID:}
        secret: ${FACEBOOK_APP_SECRET:}
      access:
        token: ${FACEBOOK_ACCESS_TOKEN:}
    twitter:
      consumer:
        key: ${TWITTER_CONSUMER_KEY:}
        secret: ${TWITTER_CONSUMER_SECRET:}
      access:
        token: ${TWITTER_ACCESS_TOKEN:}
        token-secret: ${TWITTER_ACCESS_TOKEN_SECRET:}
    instagram:
      client:
        id: ${INSTAGRAM_CLIENT_ID:}
        secret: ${INSTAGRAM_CLIENT_SECRET:}
      access:
        token: ${INSTAGRAM_ACCESS_TOKEN:}
    ingestion:
      enabled: ${SOCIAL_MEDIA_INGESTION_ENABLED:true}
    simulation:
      enabled: ${SOCIAL_MEDIA_SIMULATION_ENABLED:true}
    ingestion:
      interval: ${SOCIAL_MEDIA_INGESTION_INTERVAL:600000}
```

**RabbitMQ Queue**: `social.media.queue`

### 3. Knowledge Graph Processing Service

**File**: `src/main/java/com/cruise/recommender/service/KnowledgeGraphSparkService.java`

**Purpose**: Processes AIS and social media data using Apache Spark and Apache Jena to build RDF Knowledge Graphs.

**Features**:
- Converts AIS data to RDF triples
- Converts social media posts to RDF triples
- SPARQL query execution
- RDF model persistence using Jena TDB
- Integration with Apache Spark for big data processing

**RDF Namespace**: `http://cruise.recommender.org/kg/`

**SPARQL Queries**:
- Find passenger interests: `findPassengerInterests(passengerId)`
- Find ships near location: `findShipsNearLocation(lat, lng, radius)`
- Find popular interests by location: `findPopularInterestsByLocation(location)`

**RabbitMQ Queue**: `knowledge.graph.queue`

### 4. Data Persistence Service

**File**: `src/main/java/com/cruise/recommender/service/DataPersistenceService.java`

**Purpose**: Persists processed data from Knowledge Graph to MySQL and Elasticsearch.

**Features**:
- Saves AIS data to MySQL (`ais_data` table)
- Indexes AIS data in Elasticsearch for fast search
- Creates `PassengerInterest` entities from social media data
- Syncs Knowledge Graph data to databases periodically

**Databases**:
- **MySQL**: Structured data (AIS data, passenger interests, etc.)
- **Elasticsearch**: Search and analytics (AIS data documents)

### 5. Frontend Visualization

**Files**:
- `src/main/java/com/cruise/recommender/controller/MapController.java`
- `src/main/resources/templates/map/ships.html`
- `src/main/resources/templates/map/port.html`

**Purpose**: Interactive map visualization using Leaflet.js.

**Features**:
- Real-time ship position tracking
- Port area visualization with radius circles
- Ship markers with popup information
- Auto-refresh every 30 seconds

**Endpoints**:
- `/api/v1/map/ships` - Global ship tracking map
- `/api/v1/map/port?latitude=X&longitude=Y&radius=Z` - Port-specific map

### 6. Grafana Dashboards

**File**: `docker/grafana/dashboards/ship-tracking.json`

**Purpose**: Metrics visualization for ship tracking and AIS data.

**Metrics**:
- Active ships count
- AIS data ingestion rate
- Ship positions on map
- Average ship speed
- Signal quality distribution

**Access**: http://localhost:3001 (admin/admin)

### 7. Kibana Dashboards

**File**: `docker/kibana/dashboards/knowledge-graph.json`

**Purpose**: Elasticsearch data visualization and analytics.

**Visualizations**:
- RDF triples count over time
- Passenger interests by category
- Knowledge Graph analytics

**Access**: http://localhost:5601

## Data Flow

### AIS Data Flow

1. **Ingestion**: `AisDataIngestionService` fetches/generates AIS data
2. **Queue**: Data sent to `ais.data.queue` via RabbitMQ
3. **Processing**: `AisDataService` processes messages and saves to MySQL
4. **Knowledge Graph**: `KnowledgeGraphSparkService` converts to RDF triples
5. **Elasticsearch**: `AisDataDocument` indexed for fast search
6. **Visualization**: Leaflet maps display ship positions

### Social Media Data Flow

1. **Ingestion**: `SocialMediaIngestionService` fetches posts from APIs
2. **Queue**: Data sent to `social.media.queue` via RabbitMQ
3. **Knowledge Graph**: `KnowledgeGraphSparkService` converts to RDF triples
4. **Processing**: SPARQL queries extract passenger interests
5. **Persistence**: `DataPersistenceService` creates `PassengerInterest` entities
6. **Storage**: Data saved to MySQL and indexed in Elasticsearch

## Setup Instructions

### 1. Start Docker Services

```bash
docker-compose up -d
```

This starts:
- MySQL (port 3306)
- Redis (port 6379)
- RabbitMQ (ports 5672, 15672)
- Elasticsearch (port 9200)
- Kibana (port 5601)
- Prometheus (port 9090)
- Grafana (port 3001)

### 2. Configure API Keys (Optional)

For production use, configure API keys in `application.yml` or environment variables:

```bash
export AIS_API_URL="https://api.example.com/ais"
export AIS_API_KEY="your-api-key"
export FACEBOOK_ACCESS_TOKEN="your-facebook-token"
export TWITTER_ACCESS_TOKEN="your-twitter-token"
export INSTAGRAM_ACCESS_TOKEN="your-instagram-token"
```

### 3. Run Application

```bash
mvn spring-boot:run
```

### 4. Access Dashboards

- **Grafana**: http://localhost:3001 (admin/admin)
- **Kibana**: http://localhost:5601
- **RabbitMQ Management**: http://localhost:15672 (cruise_app/cruise_rabbitmq_password_2024)
- **Ship Map**: http://localhost:8080/api/v1/map/ships
- **Port Map**: http://localhost:8080/api/v1/map/port?latitude=25.7617&longitude=-80.1918&radius=50

## Monitoring

### Prometheus Metrics

The application exposes metrics at `/api/v1/actuator/prometheus`:
- `ais_data_ingested_total` - Total AIS messages ingested
- `ais_ship_latitude` - Ship latitude coordinates
- `ais_ship_longitude` - Ship longitude coordinates
- `ais_ship_speed` - Ship speed in knots

### RabbitMQ Monitoring

Monitor queue depths and message rates:
- `ais.data.queue` - AIS data messages
- `social.media.queue` - Social media posts
- `knowledge.graph.queue` - Processed Knowledge Graph data

## Troubleshooting

### No Data in MySQL

1. Check RabbitMQ queues are receiving messages
2. Verify `AisDataService` is listening to `ais.data.queue`
3. Check application logs for errors

### Elasticsearch Not Indexing

1. Verify Elasticsearch is running: `curl http://localhost:9200`
2. Check `AisDataElasticsearchRepository` is configured
3. Verify index exists: `curl http://localhost:9200/ais_data`

### Social Media APIs Not Working

1. Verify API credentials are correct
2. Check API rate limits
3. Enable simulation mode for testing: `social.media.simulation.enabled=true`

## Next Steps

1. **Production API Integration**: Configure real AIS and social media API credentials
2. **Advanced SPARQL Queries**: Add more complex queries for recommendation generation
3. **Real-time Updates**: Implement WebSocket for live map updates
4. **Machine Learning**: Integrate Spark MLlib for predictive analytics
5. **Scalability**: Configure Spark cluster mode for production workloads

