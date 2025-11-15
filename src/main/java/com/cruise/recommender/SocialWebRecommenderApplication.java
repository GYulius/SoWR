package com.cruise.recommender;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot Application Class
 * Social Web Recommender for Cruising Ports
 * 
 * @author Social Web Recommender Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableCaching
@EnableAsync
@EnableScheduling
public class SocialWebRecommenderApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocialWebRecommenderApplication.class, args);
        System.out.println("🚢 Social Web Recommender for Cruising Ports started successfully!");
        System.out.println("📊 Swagger UI available at: http://localhost:8080/api/v1/swagger-ui.html");
        System.out.println("🔗 Health check: http://localhost:8080/api/v1/actuator/health");
    }
}
