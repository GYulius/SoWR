package com.cruise.recommender.config;

import com.cruise.recommender.service.ApiPerformanceService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Filter to track API performance metrics
 * Captures request/response details and indexes to Elasticsearch
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiPerformanceFilter extends OncePerRequestFilter implements Ordered {
    
    private static final int FILTER_ORDER = 1; // Execute early in the filter chain
    
    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }
    
    private final ApiPerformanceService apiPerformanceService;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        // Skip actuator endpoints and static resources
        String requestPath = request.getRequestURI();
        if (shouldSkip(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        long startTime = System.currentTimeMillis();
        LocalDateTime timestamp = LocalDateTime.now();
        
        // Wrap request/response to cache content for size calculation
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        
        String errorType = null;
        String errorMessage = null;
        boolean success = true;
        
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
            
            int statusCode = wrappedResponse.getStatus();
            success = statusCode < 400;
            
            if (!success) {
                errorType = "HTTP_" + statusCode;
                errorMessage = "HTTP Error: " + statusCode;
            }
            
        } catch (Exception e) {
            success = false;
            errorType = e.getClass().getSimpleName();
            errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.length() > 500) {
                errorMessage = errorMessage.substring(0, 500);
            }
            throw e; // Re-throw to maintain normal error handling
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            
            // Get user info if authenticated
            String userEmail = null;
            String userRole = null;
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() 
                    && !"anonymousUser".equals(authentication.getPrincipal().toString())) {
                userEmail = authentication.getName();
                if (authentication.getAuthorities() != null && !authentication.getAuthorities().isEmpty()) {
                    userRole = authentication.getAuthorities().iterator().next().getAuthority();
                }
            }
            
            // Record performance metrics (async, non-blocking)
            try {
                apiPerformanceService.recordApiCall(
                        requestPath,
                        request.getMethod(),
                        wrappedResponse.getStatus(),
                        duration,
                        success,
                        errorType,
                        errorMessage,
                        userEmail,
                        userRole,
                        getClientIp(request),
                        request.getHeader("User-Agent"),
                        wrappedRequest.getContentAsByteArray().length,
                        wrappedResponse.getContentAsByteArray().length,
                        timestamp
                );
            } catch (Exception e) {
                log.debug("Failed to record API performance metrics: {}", e.getMessage());
            }
            
            // Copy response content back to original response
            wrappedResponse.copyBodyToResponse();
        }
    }
    
    private boolean shouldSkip(String path) {
        // Skip actuator endpoints, swagger, and static resources
        return path.startsWith("/actuator") ||
               path.startsWith("/swagger") ||
               path.startsWith("/api-docs") ||
               path.startsWith("/webjars") ||
               path.startsWith("/favicon.ico") ||
               path.startsWith("/error");
    }
    
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}

