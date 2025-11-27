package com.cruise.recommender.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.core.Ordered;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication Filter to process JWT tokens in requests
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter implements Ordered {
    
    private static final int FILTER_ORDER = 2; // Order 2: After ApiPerformanceFilter (Order 1)
    
    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }
    
    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService customUserDetailsService;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String requestPath = request.getRequestURI();
        log.debug("Processing request: {} {}", request.getMethod(), requestPath);
        
        try {
            String jwt = getJwtFromRequest(request);
            
            if (StringUtils.hasText(jwt)) {
                log.debug("JWT token found in request");
                if (tokenProvider.validateToken(jwt)) {
                    log.debug("JWT token is valid");
                    String email = tokenProvider.getEmailFromToken(jwt);
                    log.debug("Extracted email from token: {}", email);
                    
                    UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    
                    log.info("Authenticated user: {} with authorities: {} for path: {}", 
                            email, userDetails.getAuthorities(), requestPath);
                } else {
                    log.warn("Invalid JWT token for request: {} {}", request.getMethod(), requestPath);
                }
            } else {
                log.debug("No JWT token found for request: {} {}", request.getMethod(), requestPath);
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context for path: {}", requestPath, ex);
        }
        
        filterChain.doFilter(request, response);
    }
    
    private String getJwtFromRequest(HttpServletRequest request) {
        // First check Authorization header
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7);
            log.debug("Found JWT token in Authorization header");
            return token;
        }
        
        // Check for token in query parameter (fallback for cookie issues)
        String tokenParam = request.getParameter("token");
        if (StringUtils.hasText(tokenParam)) {
            log.debug("Found JWT token in query parameter");
            return tokenParam;
        }
        
        // Also check for token in cookie
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                if ("token".equals(cookie.getName())) {
                    log.debug("Found JWT token in cookie");
                    return cookie.getValue();
                }
            }
        }
        
        log.debug("No JWT token found in request");
        return null;
    }
}

