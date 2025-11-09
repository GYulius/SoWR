# Contributing to Social Web Recommender for Cruising Ports

Thank you for your interest in contributing to the Social Web Recommender project! This document provides guidelines and information for contributors.

## 🚀 Getting Started

### Prerequisites
- Java 17 or higher
- Maven 3.8+
- MySQL 8.0 or higher
- Redis 6.0 or higher
- RabbitMQ 3.12+ (for message queuing)
- Elasticsearch 8.0+ (for AIS data search)
- Apache Spark 3.5+ (for big data processing)
- Prometheus & Grafana (for monitoring)
- Git

### Development Setup
1. Fork the repository
2. Clone your fork: `git clone https://github.com/YOUR_USERNAME/social-web-recommender.git`
3. Create a feature branch: `git checkout -b feature/amazing-feature`
4. Set up your development environment following the [README](README.md)

## 📋 How to Contribute

### Reporting Issues
- Use the GitHub issue tracker
- Provide detailed information about the bug or feature request
- Include steps to reproduce for bugs
- Use appropriate labels

### Submitting Changes
1. **Fork the repository** and create your branch from `main`
2. **Make your changes** following our coding standards
3. **Add tests** for new functionality
4. **Update documentation** as needed
5. **Commit your changes** with clear, descriptive messages
6. **Push to your fork** and submit a pull request

### Code Style Guidelines

#### Java Code
- Follow Java naming conventions
- Use meaningful variable and method names
- Add Javadoc comments for public methods
- Keep methods focused and small
- Use Spring Boot best practices

#### YAML Configuration
- Use proper YAML indentation (2 spaces)
- Group related configurations
- Add comments for clarity
- Use environment-specific profiles

#### Database
- Use descriptive table and column names
- Add proper indexes for performance
- Include foreign key constraints
- Document schema changes in migrations

### Testing Requirements
- Write unit tests for new services and utilities
- Include integration tests for API endpoints
- Test with different user roles and permissions
- Test passenger-focused recommendations with various interest profiles
- Test social media analysis with sample data (respecting privacy)
- Test AIS data processing with mock ship positions
- Test Spark jobs with sample datasets
- Ensure tests pass before submitting PR

### Passenger-Focused Development Guidelines
- **Priority**: Passenger interests and preferences are the primary focus
- **Privacy**: Always require explicit consent for social media analysis
- **Interest Sources**: Prioritize voluntarily expressed interests over inferred ones
- **Recommendations**: Focus on locally active venues during port calls
- **Personalization**: Use multi-factor scoring (interests, local recommendations, ratings)

### Documentation
- Update README.md for significant changes
- Add Javadoc for new public APIs
- Update API documentation (OpenAPI/Swagger)
- Include examples for new features

## 🏗️ Project Structure

```
src/main/java/com/cruise/recommender/
├── controller/          # REST controllers
│   ├── PassengerRecommendationController.java  # Passenger-focused recommendations
│   ├── DashboardController.java               # Ship tracking dashboard
│   ├── RecommendationController.java          # General recommendations
│   └── PublisherController.java               # Publisher management
├── service/            # Business logic services
│   ├── SocialMediaAnalysisService.java        # Social media analysis
│   ├── ShoreExcursionRecommendationService.java # Shore excursion recommendations
│   ├── MealVenueRecommendationService.java    # Meal venue recommendations
│   ├── AisDataService.java                    # AIS ship tracking
│   ├── SparkMlService.java                    # Spark ML processing
│   ├── PageRankService.java                   # Social network analysis
│   └── RecommendationService.java             # Core recommendation engine
├── repository/         # Data access layer
│   ├── PassengerRepository.java
│   ├── AisDataRepository.java
│   ├── ShoreExcursionRepository.java
│   └── MealVenueRepository.java
├── entity/             # JPA entities
│   ├── Passenger.java                         # Passenger entity (priority)
│   ├── PassengerInterest.java                 # Interest tracking
│   ├── SocialMediaProfile.java                # Social media data
│   ├── ShoreExcursion.java                    # Shore excursions
│   ├── MealVenue.java                         # Breakfast/lunch venues
│   ├── AisData.java                           # AIS tracking data
│   └── CruiseShip.java                        # Cruise ship data
├── dto/                # Data transfer objects
├── config/             # Configuration classes
│   ├── RabbitMQConfig.java                    # RabbitMQ setup
│   ├── ElasticsearchConfig.java               # Elasticsearch setup
│   └── PrometheusConfig.java                  # Prometheus metrics
└── SocialWebRecommenderApplication.java

src/main/resources/
├── templates/          # Thymeleaf templates
├── static/             # Static resources
└── application.yml     # Configuration

database/
└── schema.sql          # Database schema

docs/
├── architecture/       # Architecture documentation
│   └── C4-Level1-Context.md
├── grafana/            # Grafana dashboards
│   └── dashboard-ship-tracking.json
├── kibana/             # Kibana visualizations
│   └── dashboard-analytics.json
└── ADVANCED_ANALYTICS.md # Advanced analytics guide
```

## 🔧 Development Workflow

### Branch Naming
- `feature/feature-name` - New features
- `bugfix/issue-description` - Bug fixes
- `hotfix/critical-issue` - Critical fixes
- `docs/documentation-update` - Documentation changes

### Commit Messages
Use conventional commit format:
```
type(scope): description

[optional body]

[optional footer]
```

Types:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes
- `refactor`: Code refactoring
- `test`: Adding tests
- `chore`: Maintenance tasks

### Pull Request Process
1. Ensure your branch is up to date with `main`
2. Run tests: `mvn test`
3. Check code style: `mvn checkstyle:check`
4. Update documentation if needed
5. Submit PR with clear description
6. Respond to review feedback promptly

## 🐛 Bug Reports

When reporting bugs, please include:
- **Environment**: OS, Java version, Maven version
- **Steps to reproduce**: Clear, numbered steps
- **Expected behavior**: What should happen
- **Actual behavior**: What actually happens
- **Screenshots**: If applicable
- **Logs**: Relevant error messages

## 💡 Feature Requests

For feature requests, please provide:
- **Use case**: Why is this feature needed?
- **Proposed solution**: How should it work?
- **Alternatives**: Other approaches considered
- **Additional context**: Any other relevant information

## 📚 Resources

### Core Technologies
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Spring Data JPA Documentation](https://spring.io/projects/spring-data-jpa)
- [MySQL Documentation](https://dev.mysql.com/doc/)
- [Redis Documentation](https://redis.io/documentation)
- [OpenAPI Specification](https://swagger.io/specification/)

### Advanced Technologies
- [Apache Spark Documentation](https://spark.apache.org/docs/latest/)
- [Spark MLlib Guide](https://spark.apache.org/docs/latest/ml-guide.html)
- [RabbitMQ Documentation](https://www.rabbitmq.com/documentation.html)
- [Elasticsearch Guide](https://www.elastic.co/guide/en/elasticsearch/reference/current/index.html)
- [Prometheus Documentation](https://prometheus.io/docs/)
- [Grafana Documentation](https://grafana.com/docs/)
- [Apache Jena Documentation](https://jena.apache.org/documentation/)

### Analytics & ML
- [PageRank Algorithm](https://en.wikipedia.org/wiki/PageRank)
- [Collaborative Filtering](https://en.wikipedia.org/wiki/Collaborative_filtering)
- [Long Tail Recommendations](https://en.wikipedia.org/wiki/Long_tail)
- [AIS (Automatic Identification System)](https://en.wikipedia.org/wiki/Automatic_identification_system)

### Project-Specific Documentation
- [Advanced Analytics Guide](docs/ADVANCED_ANALYTICS.md)
- [C4 Architecture Documentation](docs/architecture/C4-Level1-Context.md)

## 🤝 Code of Conduct

### Our Pledge
We are committed to providing a welcoming and inclusive environment for all contributors.

### Expected Behavior
- Use welcoming and inclusive language
- Be respectful of differing viewpoints
- Accept constructive criticism gracefully
- Focus on what's best for the community
- Show empathy towards other community members

### Unacceptable Behavior
- Harassment, trolling, or insulting comments
- Public or private harassment
- Publishing private information without permission
- Other unprofessional conduct

## 📞 Getting Help

- **GitHub Issues**: For bugs and feature requests
- **Discussions**: For questions and general discussion
- **Email**: team@cruise-recommender.com

## 🎉 Recognition

Contributors will be recognized in:
- CONTRIBUTORS.md file
- Release notes
- Project documentation

Thank you for contributing to the Social Web Recommender project! 🚢
