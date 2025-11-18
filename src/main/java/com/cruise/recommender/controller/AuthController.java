package com.cruise.recommender.controller;

import com.cruise.recommender.dto.AuthResponse;
import com.cruise.recommender.dto.LoginRequest;
import com.cruise.recommender.entity.Role;
import com.cruise.recommender.entity.User;
import com.cruise.recommender.repository.UserRepository;
import com.cruise.recommender.security.JwtTokenProvider;
import com.cruise.recommender.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Authentication Controller
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AuthController {
    
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    
    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest, HttpServletResponse httpResponse) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            
            // Update last login
            User user = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);
            
            String token = tokenProvider.generateToken(
                    userPrincipal.getUsername(),
                    userPrincipal.getId(),
                    userPrincipal.getUser().getRole().name()
            );
            
            // Set token as HTTP-only cookie for web page access
            Cookie cookie = new Cookie("token", token);
            cookie.setHttpOnly(false); // Set to false so JavaScript can also access it (for API calls)
            cookie.setPath("/api/v1"); // Match the context path
            cookie.setMaxAge(7 * 24 * 60 * 60); // 7 days
            // For development: use Lax for same-site, None for cross-site (requires Secure in production)
            // Since we're on localhost, Lax should work for same-domain navigation
            cookie.setAttribute("SameSite", "Lax");
            httpResponse.addCookie(cookie);
            
            // Also set cookie for root path to ensure it works
            Cookie rootCookie = new Cookie("token", token);
            rootCookie.setHttpOnly(false);
            rootCookie.setPath("/");
            rootCookie.setMaxAge(7 * 24 * 60 * 60);
            rootCookie.setAttribute("SameSite", "Lax");
            httpResponse.addCookie(rootCookie);
            
            AuthResponse response = AuthResponse.builder()
                    .token(token)
                    .email(userPrincipal.getUsername())
                    .userId(userPrincipal.getId())
                    .role(userPrincipal.getUser().getRole().name())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .build();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Login error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid email or password");
        }
    }
    
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest, HttpServletResponse httpResponse) {
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            return ResponseEntity.badRequest()
                    .body("Email is already in use!");
        }
        
        // Determine role - default to USER if not specified or invalid
        Role userRole = Role.USER;
        if (registerRequest.getRole() != null) {
            try {
                userRole = Role.valueOf(registerRequest.getRole().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid role specified: {}, defaulting to USER", registerRequest.getRole());
                userRole = Role.USER;
            }
        }
        
        User user = User.builder()
                .email(registerRequest.getEmail())
                .passwordHash(passwordEncoder.encode(registerRequest.getPassword()))
                .firstName(registerRequest.getFirstName())
                .lastName(registerRequest.getLastName())
                .role(userRole)
                .isActive(true)
                .build();
        
        user = userRepository.save(user);
        
        String token = tokenProvider.generateToken(
                user.getEmail(),
                user.getId(),
                user.getRole().name()
        );
        
        // Set token as HTTP-only cookie for web page access
        Cookie cookie = new Cookie("token", token);
        cookie.setHttpOnly(false); // Set to false so JavaScript can also access it
        cookie.setPath("/api/v1"); // Match the context path
        cookie.setMaxAge(7 * 24 * 60 * 60); // 7 days
        cookie.setAttribute("SameSite", "Lax");
        httpResponse.addCookie(cookie);
        
        // Also set cookie for root path
        Cookie rootCookie = new Cookie("token", token);
        rootCookie.setHttpOnly(false);
        rootCookie.setPath("/");
        rootCookie.setMaxAge(7 * 24 * 60 * 60);
        rootCookie.setAttribute("SameSite", "Lax");
        httpResponse.addCookie(rootCookie);
        
        AuthResponse response = AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .userId(user.getId())
                .role(user.getRole().name())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse httpResponse) {
        // Clear the token cookie
        Cookie cookie = new Cookie("token", null);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0); // Delete cookie
        httpResponse.addCookie(cookie);
        
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok("Logged out successfully");
    }
    
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || 
            authentication.getPrincipal() instanceof String) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User user = userPrincipal.getUser();
        
        AuthResponse response = AuthResponse.builder()
                .email(user.getEmail())
                .userId(user.getId())
                .role(user.getRole().name())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();
        
        return ResponseEntity.ok(response);
    }
    
    // Inner class for registration request
    @lombok.Data
    public static class RegisterRequest {
        private String email;
        private String password;
        private String firstName;
        private String lastName;
        private String role; // Optional: "USER" or "ADMIN"
    }
}

