package com.cruise.recommender.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security Configuration
 * Allows actuator endpoints for monitoring while securing application endpoints
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Allow actuator endpoints without authentication
                .requestMatchers("/actuator/**").permitAll()
                // Allow Swagger UI and API docs
                .requestMatchers("/swagger-ui/**", "/api-docs/**", "/v3/api-docs/**").permitAll()
                // Allow health check endpoint
                .requestMatchers("/api/v1/actuator/health").permitAll()
                // All other requests require authentication (to be configured later)
                .anyRequest().permitAll() // Temporarily permit all - update when auth is implemented
            )
            .csrf(csrf -> csrf
                // Disable CSRF for actuator endpoints and API endpoints
                .ignoringRequestMatchers("/actuator/**", "/ports/**", "/dashboard/**", "/passengers/**", "/recommendations/**", "/publishers/**")
            );
        
        return http.build();
    }
}

