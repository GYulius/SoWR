# Advanced Analytics Integration Guide

## Overview

The Social Web Recommender for Cruising Ports integrates multiple advanced technologies for big data processing, machine learning, real-time tracking, and analytics:

- **Apache Spark** (Spark SQL & MLlib) - Big data processing and ML
- **RabbitMQ** - Message queuing for AIS data
- **Prometheus & Grafana** - Metrics and monitoring
- **Elasticsearch & Kibana** - Search and analytics
- **PageRank** - Social network analysis
- **Long Tail Recommendations** - Niche item recommendations
- **AIS Tracking** - Real-time ship position monitoring

## Architecture Components

### 1. Passenger-Focused Analytics (Priority)

**Purpose**: Analyze passenger interests, social media presence, and provide personalized recommendations

**Components**:
- `Passenger` entity - Core entity representing cruise guests
- `SocialMediaAnalysisService` - Analyzes passenger digital presence
- `ShoreExcursionRecommendationService` - Recommends touristic highlights and excursions
- `MealVenueRecommendationService` - Recommends breakfast and lunch venues
- `PassengerInterest` entity - Tracks voluntarily expressed interests
- `SocialMediaProfile` entity - Stores social media analysis data

**Data Flow**:
1. Passengers voluntarily express interests (profile forms, preferences)
2. Social media profiles analyzed (with consent) to extract interests
3. Interests stored and weighted by confidence scores
4. Recommendations generated based on:
   - Voluntarily expressed interests (highest priority)
   - Social media analysis (secondary)
   - Previous bookings and interactions
   - Group preferences and demographics

**Social Media Analysis**:
- **Platforms Supported**: Twitter, Instagram, Facebook, LinkedIn, TikTok
- **Extraction Methods**:
  - Hashtag analysis for interest detection
  - Location tagging for travel preferences
  - Sentiment analysis for preference understanding
  - Activity pattern analysis
- **Interest Categories**: History, Art, Nature, Adventure, Food, Shopping, Culture, etc.

**Recommendation Types**:
1. **Must-See Highlights**: Top touristic attractions personalized by interests
2. **Shore Excursions**: Personalized tour recommendations
3. **Breakfast Venues**: Locally active breakfast spots during port calls
4. **Lunch Venues**: Locally active lunch spots during port calls

**Scoring Algorithm**:
- Interest Match: 40% weight
- Local Recommendation Score: 30% weight
- Popularity: 15% weight
- Rating: 10% weight
- Accessibility/Budget: 5% weight

### 2. AIS (Automatic Identification System) Data Processing

**Purpose**: Real-time tracking of cruise ships using AIS transceivers

**Components**:
- `AisDataService` - Processes incoming AIS messages
- `AisData` entity - Stores ship position data
- `CruiseShip` entity - Tracks ship metadata and current status

**Data Flow**:
1. AIS transceivers on ships broadcast position data
2. AIS stations (terrestrial or satellite) receive signals
3. Data sent to RabbitMQ queue (`ais.data.queue`)
4. `AisDataService` processes and stores in MySQL
5. Data indexed in Elasticsearch for fast search
6. Real-time updates published via WebSocket

**Signal Quality Handling**:
- **GOOD**: Within range of AIS station (< 50 nautical miles)
- **FAIR**: Moderate signal strength
- **POOR**: Weak signal, may be intermittent
- **NONE**: Out of range or transceiver offline

### 3. Apache Spark Integration

**Purpose**: Big data processing and machine learning

**Components**:
- `SparkMlService` - ML algorithms and data processing
- Spark SQL for analytics
- Spark MLlib for collaborative filtering
- Spark GraphX for graph processing

**Use Cases**:

#### Long Tail Recommendations
- Identifies less popular but highly relevant items
- Uses ALS (Alternating Least Squares) algorithm
- Filters items in bottom 80% of popularity
- Provides personalized niche recommendations

#### User Behavior Analysis
- Analyzes interaction patterns
- Identifies popular time slots
- Calculates engagement metrics
- Tracks category preferences

#### AIS Data Analytics
- Processes large volumes of AIS data
- Calculates ship movement patterns
- Identifies ships approaching ports
- Generates predictive analytics

### 4. PageRank for Social Network Analysis

**Purpose**: Identify influential users and analyze social networks

**Components**:
- `PageRankService` - Calculates PageRank scores
- JGraphT library for graph processing
- Social network graph mining

**Networks Analyzed**:

#### User Social Network
- Nodes: Users
- Edges: Subscriptions, interactions, similar interests
- Output: Influence scores for each user

#### Publisher Network
- Nodes: Publishers (local businesses)
- Edges: Shared subscribers, collaborations
- Output: Publisher influence rankings

#### Recommendation Influence Network
- Nodes: Items (attractions, restaurants, activities)
- Edges: Co-recommendation patterns
- Output: Item influence scores

**Community Detection**:
- Identifies user communities
- Groups similar users
- Enables targeted recommendations

### 5. RabbitMQ Message Queuing

**Purpose**: Asynchronous processing and real-time updates

**Queues**:
- `ais.data.queue` - AIS position updates
- `notification.queue` - User notifications
- `recommendation.queue` - Recommendation updates
- `analytics.queue` - Analytics processing

**Exchanges**:
- `ais.exchange` - AIS data routing
- `notification.exchange` - Notification routing
- `recommendation.exchange` - Recommendation routing

**Benefits**:
- Decouples data producers and consumers
- Scalable processing
- Handles high message volumes
- Reliable message delivery

### 6. Prometheus & Grafana Monitoring

**Purpose**: Real-time metrics and visualization

**Metrics Collected**:
- Ship tracking statistics
- AIS signal quality distribution
- Recommendation processing time
- PageRank score distributions
- User engagement metrics
- System performance metrics

**Grafana Dashboards**:
- **Ship Tracking Dashboard**: Real-time ship positions on map
- **AIS Signal Quality**: Distribution of signal strengths
- **Recommendation Performance**: Processing times and accuracy
- **System Health**: Application performance metrics

**Access**:
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3001`
- Metrics endpoint: `http://localhost:8080/actuator/prometheus`

### 7. Elasticsearch & Kibana Analytics

**Purpose**: Fast search and advanced analytics on AIS data

**Indexes**:
- `ais-data-*` - AIS position data
- `recommendations-*` - Recommendation data
- `user-interactions-*` - User behavior data
- `pagerank-scores-*` - PageRank calculations

**Kibana Visualizations**:
- **Ship Positions Map**: Real-time geographic visualization
- **Signal Quality Distribution**: Pie chart of signal strengths
- **Speed Distribution**: Histogram of ship speeds
- **Recommendation Performance**: Time series of recommendation scores
- **Long Tail Items**: Table of niche recommendations
- **PageRank Influence**: Heatmap of user/publisher influence

**Benefits**:
- Fast full-text search
- Complex aggregations
- Real-time analytics
- Geographic queries

## Implementation Details

### Passenger Interest Extraction from Social Media

```java
// 1. Analyze social media profiles
socialMediaService.analyzePassengerSocialMedia(passengerId);

// 2. Extract interests from posts using Spark NLP
List<String> interests = extractInterestsFromPosts(profile);

// 3. Calculate confidence scores
Double confidenceScore = calculateConfidence(interest, source);

// 4. Store as PassengerInterest
PassengerInterest interest = PassengerInterest.builder()
    .passenger(passenger)
    .interestCategory(category)
    .interestKeyword(keyword)
    .source(InterestSource.SOCIAL_MEDIA)
    .confidenceScore(confidenceScore)
    .isExplicit(false) // Inferred from social media
    .build();
```

### Shore Excursion Recommendation Algorithm

```java
// 1. Get passenger interests (voluntary + social media)
List<PassengerInterest> interests = getPassengerInterests(passengerId);

// 2. Get available excursions for port
List<ShoreExcursion> excursions = getAvailableExcursions(portId);

// 3. Score each excursion
for (ShoreExcursion excursion : excursions) {
    double score = 0.0;
    
    // Interest match (40%)
    score += calculateInterestMatch(excursion, interests) * 0.4;
    
    // Must-see highlight boost (20%)
    if (excursion.isMustSeeHighlight()) {
        score += 0.2;
    }
    
    // Local recommendation (30%)
    score += excursion.getLocalRecommendationScore() * 0.3;
    
    // Rating (10%)
    score += (excursion.getRating() / 5.0) * 0.1;
}

// 4. Return top recommendations
return topScoredExcursions(10);
```

### Meal Venue Recommendation Algorithm

```java
// 1. Get locally active venues for port call time
List<MealVenue> venues = getActiveVenues(portId, mealType, portCallTime);

// 2. Score based on passenger preferences
for (MealVenue venue : venues) {
    double score = 0.0;
    
    // Local recommendation (30%)
    score += venue.getLocalRecommendationScore() * 0.3;
    
    // Tourist friendly (10%)
    if (venue.isTouristFriendly()) score += 0.1;
    
    // Walking distance (10%)
    if (venue.getWalkingDistance() <= 10) score += 0.1;
    
    // Dietary compatibility (10%)
    if (isDietaryCompatible(venue, passenger)) score += 0.1;
    
    // Rating and popularity (40%)
    score += (venue.getRating() / 5.0) * 0.2;
    score += venue.getPopularityScore() * 0.2;
}

// 3. Return top recommendations
return topScoredVenues(10);
```

### Long Tail Recommendation Algorithm

```java
// 1. Train collaborative filtering model
ALSModel model = trainCollaborativeFilteringModel(userItemRatings);

// 2. Calculate item popularity
Dataset<Row> itemPopularity = calculatePopularity(userItemRatings);

// 3. Identify long tail items (bottom 80%)
Dataset<Row> longTailItems = filterLongTail(itemPopularity, 0.8);

// 4. Generate recommendations
Dataset<Row> recommendations = model.recommendForAllUsers(10);

// 5. Filter to long tail items only
Dataset<Row> longTailRecommendations = filterToLongTail(recommendations, longTailItems);
```

### PageRank Calculation

```java
// 1. Build graph from relationships
JGraphTGraph<Long, DefaultEdge> graph = buildGraph(relationships);

// 2. Calculate PageRank scores
PageRank<Long, DefaultEdge> pageRank = new PageRank<>(graph);
Map<Long, Double> scores = pageRank.getScores();

// 3. Identify influential users
List<InfluentialUser> topUsers = findTopInfluentialUsers(scores, 10);
```

### AIS Data Processing Pipeline

```java
// 1. Receive AIS message from RabbitMQ
@RabbitListener(queues = "ais.data.queue")
public void processAisData(AisDataMessage message) {
    // 2. Find or create ship
    CruiseShip ship = findOrCreateShip(message);
    
    // 3. Create AIS data record
    AisData aisData = createAisData(message, ship);
    
    // 4. Save to MySQL
    aisDataRepository.save(aisData);
    
    // 5. Index in Elasticsearch
    elasticsearchOperations.save(aisData);
    
    // 6. Update ship position
    updateShipPosition(ship, aisData);
    
    // 7. Publish to WebSocket
    publishPositionUpdate(ship, aisData);
}
```

## Configuration

### Spark Configuration
```yaml
spark:
  app:
    name: CruiseRecommenderML
  master: local[*]
  driver:
    memory: 2g
  executor:
    memory: 2g
```

### RabbitMQ Configuration
```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

### Elasticsearch Configuration
```yaml
elasticsearch:
  host: localhost
  port: 9200
```

## API Endpoints

### Passenger-Focused Recommendations (Priority)
- `GET /passengers/{passengerId}/recommendations` - Comprehensive recommendations (excursions + meals)
- `GET /passengers/{passengerId}/shore-excursions` - Personalized shore excursion recommendations
- `GET /passengers/{passengerId}/must-see-highlights` - Personalized must-see highlights
- `GET /passengers/{passengerId}/breakfast-venues` - Breakfast venue recommendations
- `GET /passengers/{passengerId}/lunch-venues` - Lunch venue recommendations
- `POST /passengers/{passengerId}/analyze-social-media` - Trigger social media analysis

### Ship Tracking
- `GET /dashboard/ships/positions` - Current ship positions
- `GET /dashboard/ships/near-port` - Ships near a port
- `GET /dashboard/ships/{id}/tracking` - Ship tracking history
- `GET /dashboard/ships/statistics` - Tracking statistics

### Analytics
- `GET /analytics/pagerank/users` - User PageRank scores
- `GET /analytics/pagerank/publishers` - Publisher PageRank scores
- `GET /analytics/long-tail/recommendations` - Long tail recommendations
- `GET /analytics/user-behavior` - User behavior analysis
- `GET /analytics/passenger-interests` - Aggregated passenger interests for a cruise

## Monitoring & Observability

### Prometheus Metrics
- `cruise_ships_tracked_total` - Number of tracked ships
- `ais_messages_received_total` - Total AIS messages received
- `ais_ship_speed_knots` - Current ship speeds
- `recommendation_processing_duration_seconds` - Recommendation processing time
- `user_pagerank_score` - User PageRank scores

### Grafana Dashboards
- Ship Tracking Dashboard
- AIS Signal Quality Dashboard
- Recommendation Performance Dashboard
- System Health Dashboard

### Kibana Dashboards
- Real-time Ship Positions
- AIS Data Analytics
- Recommendation Analytics
- User Engagement Analytics

## Passenger-Focused Recommendation Workflow

### Step 1: Interest Collection
1. **Voluntary Expression**: Passengers fill out profile forms with interests
2. **Social Media Consent**: Passengers opt-in for social media analysis
3. **Social Media Analysis**: System analyzes profiles and extracts interests
4. **Interest Storage**: All interests stored with confidence scores and sources

### Step 2: Recommendation Generation
1. **Port Arrival**: System identifies upcoming port call
2. **Interest Matching**: Match passenger interests with available options
3. **Scoring**: Calculate recommendation scores using multi-factor algorithm
4. **Ranking**: Sort by score and return top recommendations

### Step 3: Personalization
1. **Must-See Highlights**: Filter and rank based on interests
2. **Shore Excursions**: Match excursion types with interest categories
3. **Meal Venues**: Consider dietary restrictions, budget, walking distance
4. **Time Optimization**: Ensure venues are active during port call hours

### Step 4: Continuous Learning
1. **Feedback Collection**: Track passenger interactions with recommendations
2. **Interest Refinement**: Update confidence scores based on behavior
3. **Model Improvement**: Retrain recommendation models periodically

## Best Practices

1. **Passenger Interest Management**:
   - Prioritize voluntarily expressed interests over inferred ones
   - Regularly update social media analysis (with consent)
   - Weight interests by confidence scores
   - Respect privacy and consent preferences

2. **Recommendation Quality**:
   - Focus on locally active venues during port calls
   - Prioritize must-see highlights for first-time visitors
   - Consider group size and accessibility needs
   - Balance popularity with personalization

3. **Social Media Analysis**:
   - Always require explicit consent
   - Use Spark for large-scale text analysis
   - Extract interests with confidence scores
   - Respect platform rate limits

4. **AIS Data Processing**:
   - Handle out-of-range scenarios gracefully
   - Cache frequently accessed ship positions
   - Use batch processing for historical data

5. **Spark Jobs**:
   - Run ML training jobs asynchronously
   - Cache intermediate results
   - Use appropriate partitioning

6. **PageRank**:
   - Recalculate periodically (e.g., daily)
   - Cache results for frequently accessed networks
   - Use incremental updates when possible

7. **Long Tail Recommendations**:
   - Balance between popularity and relevance
   - Monitor recommendation diversity
   - A/B test different algorithms

8. **Monitoring**:
   - Set up alerts for critical metrics
   - Monitor AIS signal quality trends
   - Track recommendation performance over time

## Future Enhancements

- Real-time stream processing with Kafka
- Advanced graph algorithms (Louvain, Infomap)
- Deep learning for recommendations
- Predictive analytics for ship arrivals
- Anomaly detection for AIS data
- Multi-armed bandit for recommendation optimization
