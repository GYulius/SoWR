package com.cruise.recommender.config;

import com.cruise.recommender.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Security Configuration
 * Configures JWT authentication and authorization
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow all origins for development (use specific origins in production)
        // Note: When allowCredentials is true, we must use setAllowedOriginPatterns instead of setAllowedOrigins("*")
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true); // Allow credentials for JWT tokens
        configuration.setMaxAge(3600L);
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type"));
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf
                .ignoringRequestMatchers(
                    "/actuator/**",
                    "/auth/**",
                    "/ports/**",
                    "/dashboard/**",
                    "/passengers/**",
                    "/recommendations/**",
                    "/publishers/**",
                    "/admin/**",
                    "/api/rdf/**",
                    "/rdf/**",
                    "/error",
                    "/error/**"
                )
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                // Public endpoints (paths are relative to context-path /api/v1)
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**", "/v3/api-docs/**", "/swagger-ui/index.html").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                
                // Error endpoint - must be accessible for exception handling
                .requestMatchers("/error", "/error/**").permitAll()
                
                // Auth endpoints - public (including logout)
                .requestMatchers("/auth/**").permitAll()
                
                // Public web pages
                .requestMatchers("/login", "/login.html", "/").permitAll()
                .requestMatchers("/css/**", "/js/**", "/images/**", "/static/**").permitAll()
                
                // Public API endpoints (for frontend)
                .requestMatchers("/ports/**").permitAll()
                .requestMatchers("/ships").permitAll() // Public ships endpoint for dropdown
                .requestMatchers("/dashboard/**").permitAll()
                
                // RDF endpoints - public (for knowledge graph operations)
                .requestMatchers("/api/rdf/**", "/rdf/**").permitAll()
                
                // Passenger endpoints - require authentication (users can access their own passenger data)
                .requestMatchers("/passengers/**").authenticated()
                
                // Recommendations endpoints - require authentication
                .requestMatchers("/recommendations/**").authenticated()
                
                // Admin endpoints require ADMIN role
                .requestMatchers("/admin/**", "/maintenance/**").hasRole("ADMIN")
                
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}

