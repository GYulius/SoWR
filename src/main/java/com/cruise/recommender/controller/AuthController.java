package com.cruise.recommender.controller;

import com.cruise.recommender.dto.AuthResponse;
import com.cruise.recommender.dto.LoginRequest;
import com.cruise.recommender.entity.EmailVerificationToken;
import com.cruise.recommender.entity.Role;
import com.cruise.recommender.entity.User;
import com.cruise.recommender.repository.EmailVerificationTokenRepository;
import com.cruise.recommender.repository.UserRepository;
import com.cruise.recommender.security.JwtTokenProvider;
import com.cruise.recommender.security.UserPrincipal;
import com.cruise.recommender.service.EmailService;
import com.cruise.recommender.service.FacebookTokenValidationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
    private final EmailService emailService;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final FacebookTokenValidationService facebookTokenValidationService;
    
    @Value("${social.media.facebook.app.id:}")
    private String facebookAppId;
    
    @Value("${social.media.facebook.app.secret:}")
    private String facebookAppSecret;
    
    @Value("${knowledge.graph.endpoint:http://localhost:3030/cruise_kg/sparql}")
    private String knowledgeGraphEndpoint;
    
    @Value("${knowledge.graph.fuseki.base-url:http://localhost:3030}")
    private String fusekiBaseUrl;
    
    @Value("${server.servlet.context-path:/api/v1}")
    private String contextPath;
    
    private final RestTemplate restTemplate;
    
    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest, HttpServletResponse httpResponse) {
        try {
            log.debug("Attempting login for email: {}", loginRequest.getEmail());
            
            // First check if user exists and is active
            User user = userRepository.findByEmail(loginRequest.getEmail()).orElse(null);
            if (user == null) {
                log.warn("Login attempt with non-existent email: {}", loginRequest.getEmail());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Invalid email or password");
            }
            
            if (!user.getIsActive()) {
                log.warn("Login attempt for inactive user: {}", loginRequest.getEmail());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Invalid email or password");
            }
            
            log.debug("User found: {}, isActive: {}, emailVerified: {}, passwordHash length: {}", 
                    user.getEmail(), user.getIsActive(), user.getEmailVerified(), 
                    user.getPasswordHash() != null ? user.getPasswordHash().length() : 0);
            
            // Manual password verification for debugging (will be removed after fixing)
            if (user.getPasswordHash() == null || user.getPasswordHash().isEmpty()) {
                log.error("User {} has no password hash stored!", user.getEmail());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Account configuration error. Please contact support.");
            }
            
            // Verify password matches (for debugging)
            boolean passwordMatches = passwordEncoder.matches(loginRequest.getPassword(), user.getPasswordHash());
            log.debug("Password verification result for {}: {}", user.getEmail(), passwordMatches);
            
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            
            // Update last login
            user = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new RuntimeException("User not found after authentication"));
            
            // TEMPORARY: Bypass email verification for local development users (IDs 1, 2, 3)
            // TODO: Remove this bypass once SMTP email is working properly
            boolean isLocalDevUser = user.getId() != null && (user.getId() == 1L || user.getId() == 2L || user.getId() == 3L);
            
            if (isLocalDevUser) {
                log.info("Local development user {} (ID: {}) - bypassing email verification", user.getEmail(), user.getId());
                // Mark as verified and proceed with login
                user.setEmailVerified(true);
                userRepository.save(user);
            } else {
                // Check if email is verified (handle null for existing users)
                Boolean emailVerified = user.getEmailVerified();
                if (emailVerified == null) {
                    // Existing users before email verification feature - treat as verified
                    emailVerified = true;
                    user.setEmailVerified(true);
                    userRepository.save(user);
                }
                if (!emailVerified) {
                    log.info("Login blocked for unverified email: {}", user.getEmail());
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body("Please verify your email address before logging in. Check your inbox for the verification email.");
                }
                
                // Generate verification code for identity verification
                String verificationCode = String.format("%06d", new java.util.Random().nextInt(1000000));
                String verificationToken = UUID.randomUUID().toString();
                
                // Delete old verification tokens for this user
                emailVerificationTokenRepository.findByUser(user)
                        .ifPresent(emailVerificationTokenRepository::delete);
                
                EmailVerificationToken emailToken = EmailVerificationToken.builder()
                        .token(verificationToken)
                        .verificationCode(verificationCode)
                        .user(user)
                        .createdAt(LocalDateTime.now())
                        .expiresAt(LocalDateTime.now().plusMinutes(10)) // Code expires in 10 minutes
                        .build();
                
                emailVerificationTokenRepository.save(emailToken);
                
                // Send verification code email
                try {
                    emailService.sendVerificationCode(user.getEmail(), user.getFirstName(), verificationCode);
                    log.info("Verification code sent for login to: {}", user.getEmail());
                } catch (Exception e) {
                    log.error("Failed to send verification code for login to: {}", user.getEmail(), e);
                    // Don't block login if email fails - allow user to request resend
                    log.warn("Email sending failed, but allowing user to proceed. They can use resend code feature.");
                }
                
                // Return response indicating verification code is required
                Map<String, Object> response = new HashMap<>();
                response.put("requiresVerification", true);
                response.put("message", "A verification code has been sent to your email address.");
                response.put("email", user.getEmail());
                response.put("userId", user.getId());
                
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }
            
            // For local dev users or verified users, proceed with normal login
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
            
            log.info("Login successful for user: {}", user.getEmail());
            return ResponseEntity.ok(response);
        } catch (org.springframework.security.core.AuthenticationException e) {
            log.error("Authentication failed for email: {}. Error: {}", loginRequest.getEmail(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid email or password");
        } catch (Exception e) {
            log.error("Login error for email: {}. Error: {}", loginRequest.getEmail(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred during login. Please try again.");
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
        
        // Encode password
        String encodedPassword = passwordEncoder.encode(registerRequest.getPassword());
        if (encodedPassword == null || encodedPassword.isEmpty()) {
            log.error("Password encoding failed for email: {}", registerRequest.getEmail());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Registration failed. Please try again.");
        }
        
        log.debug("Password encoded successfully for email: {}", registerRequest.getEmail());
        
        // Create user with emailVerified = false
        User user = User.builder()
                .email(registerRequest.getEmail())
                .passwordHash(encodedPassword)
                .firstName(registerRequest.getFirstName())
                .lastName(registerRequest.getLastName())
                .role(userRole)
                .isActive(true)
                .emailVerified(false)
                .build();
        
        user = userRepository.save(user);
        
        // Verify password hash was saved
        if (user.getPasswordHash() == null || user.getPasswordHash().isEmpty()) {
            log.error("Password hash not saved for user: {}", user.getEmail());
            userRepository.delete(user);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Registration failed. Please try again.");
        }
        
        log.info("User registered successfully: {} (ID: {})", user.getEmail(), user.getId());
        
        // Generate 6-digit verification code
        String verificationCode = String.format("%06d", new java.util.Random().nextInt(1000000));
        String verificationToken = UUID.randomUUID().toString(); // Keep token for backward compatibility
        
        EmailVerificationToken emailToken = EmailVerificationToken.builder()
                .token(verificationToken)
                .verificationCode(verificationCode)
                .user(user)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(10)) // Code expires in 10 minutes
                .build();
        
        emailVerificationTokenRepository.save(emailToken);
        
        // Send verification code email
        boolean emailSent = false;
        try {
            emailService.sendVerificationCode(user.getEmail(), user.getFirstName(), verificationCode);
            emailSent = true;
            log.info("Verification code email sent to: {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to send verification code email to: {}", user.getEmail(), e);
            // Log the code for manual verification if email fails
            log.warn("MANUAL VERIFICATION CODE for new user {} (ID: {}): {}", user.getEmail(), user.getId(), verificationCode);
            // Don't fail registration if email fails, but log it
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Registration successful! A verification code has been sent to your email.");
        response.put("email", user.getEmail());
        response.put("userId", user.getId());
        response.put("emailVerified", false);
        response.put("emailSent", emailSent);
        response.put("requiresVerification", true);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        try {
            EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByToken(token)
                    .orElseThrow(() -> new RuntimeException("Invalid verification token"));
            
            if (verificationToken.isVerified()) {
                return ResponseEntity.badRequest()
                        .body("Email has already been verified.");
            }
            
            if (verificationToken.isExpired()) {
                return ResponseEntity.badRequest()
                        .body("Verification token has expired. Please request a new verification email.");
            }
            
            User user = verificationToken.getUser();
            user.setEmailVerified(true);
            userRepository.save(user);
            
            verificationToken.setVerifiedAt(LocalDateTime.now());
            emailVerificationTokenRepository.save(verificationToken);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Email verified successfully! You can now log in.");
            response.put("email", user.getEmail());
            response.put("emailVerified", true);
            
            log.info("Email verified successfully for user: {}", user.getEmail());
            
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Email verification error: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body("Invalid or expired verification token.");
        }
    }
    
    @PostMapping("/verify-code")
    public ResponseEntity<?> verifyCode(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String code = request.get("code");
            
            if (email == null || code == null || code.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body("Email and verification code are required.");
            }
            
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByUser(user)
                    .orElseThrow(() -> new RuntimeException("No verification code found. Please request a new code."));
            
            if (verificationToken.isVerified()) {
                return ResponseEntity.badRequest()
                        .body("This code has already been used.");
            }
            
            if (verificationToken.isExpired()) {
                return ResponseEntity.badRequest()
                        .body("Verification code has expired. Please request a new code.");
            }
            
            // Verify the code matches
            if (!code.equals(verificationToken.getVerificationCode())) {
                return ResponseEntity.badRequest()
                        .body("Invalid verification code. Please check and try again.");
            }
            
            // Mark as verified
            user.setEmailVerified(true);
            userRepository.save(user);
            
            verificationToken.setVerifiedAt(LocalDateTime.now());
            emailVerificationTokenRepository.save(verificationToken);
            
            // If this is a login verification, generate JWT token
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Verification successful!");
            response.put("email", user.getEmail());
            response.put("emailVerified", true);
            
            // Check if this is a login verification (user is authenticated but needs code)
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UserPrincipal) {
                UserPrincipal userPrincipal = (UserPrincipal) auth.getPrincipal();
                if (userPrincipal.getId().equals(user.getId())) {
                    // Generate JWT token for login
                    String token = tokenProvider.generateToken(
                            user.getEmail(),
                            user.getId(),
                            user.getRole().name()
                    );
                    
                    user.setLastLogin(LocalDateTime.now());
                    userRepository.save(user);
                    
                    response.put("token", token);
                    response.put("userId", user.getId());
                    response.put("role", user.getRole().name());
                    response.put("firstName", user.getFirstName());
                    response.put("lastName", user.getLastName());
                }
            }
            
            log.info("Verification code verified successfully for user: {}", user.getEmail());
            
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Code verification error: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(e.getMessage());
        }
    }
    
    @PostMapping("/resend-code")
    public ResponseEntity<?> resendVerificationCode(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            
            if (email == null || email.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body("Email is required.");
            }
            
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Delete old token if exists
            emailVerificationTokenRepository.findByUser(user)
                    .ifPresent(emailVerificationTokenRepository::delete);
            
            // Generate new 6-digit verification code
            String verificationCode = String.format("%06d", new java.util.Random().nextInt(1000000));
            String verificationToken = UUID.randomUUID().toString();
            
            EmailVerificationToken emailToken = EmailVerificationToken.builder()
                    .token(verificationToken)
                    .verificationCode(verificationCode)
                    .user(user)
                    .createdAt(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusMinutes(10)) // Code expires in 10 minutes
                    .build();
            
            emailVerificationTokenRepository.save(emailToken);
            
            // Send verification code email
            boolean emailSent = false;
            try {
                emailService.sendVerificationCode(user.getEmail(), user.getFirstName(), verificationCode);
                emailSent = true;
                log.info("Verification code resent to: {}", user.getEmail());
            } catch (Exception e) {
                log.error("Failed to send verification code to: {}", user.getEmail(), e);
                // Log the code for manual verification if email fails
                log.warn("MANUAL VERIFICATION CODE for {}: {}", user.getEmail(), verificationCode);
                // Don't fail - code is still saved, user can contact support or try again
            }
            
            Map<String, Object> response = new HashMap<>();
            if (emailSent) {
                response.put("message", "Verification code sent successfully. Please check your inbox.");
            } else {
                response.put("message", "Verification code generated but email sending failed. Please contact support or try again later.");
                response.put("emailSent", false);
                // In development, include code in response for debugging
                if (log.isDebugEnabled()) {
                    response.put("debugCode", verificationCode);
                }
            }
            response.put("email", user.getEmail());
            
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Resend verification code error: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(e.getMessage());
        }
    }
    
    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerificationEmail(@RequestParam String email) {
        // Legacy endpoint - redirects to resend-code
        return resendVerificationCode(Map.of("email", email));
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
    
    @GetMapping("/facebook/config")
    public ResponseEntity<?> getFacebookConfig() {
        Map<String, String> config = new HashMap<>();
        if (facebookAppId != null && !facebookAppId.isEmpty()) {
            config.put("appId", facebookAppId);
            return ResponseEntity.ok(config);
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Facebook login is not configured");
        }
    }
    
    /**
     * Get frontend configuration
     * This endpoint provides configuration values needed by the frontend (index.html)
     * including Facebook App ID, API endpoints, and other client-side settings.
     * Sensitive values like secrets are NOT exposed - only public configuration.
     * GET /api/v1/config/frontend
     */
    @GetMapping("/config/frontend")
    public ResponseEntity<Map<String, Object>> getFrontendConfig() {
        Map<String, Object> config = new HashMap<>();
        
        // Facebook configuration (only public App ID, not secret)
        Map<String, Object> facebookConfig = new HashMap<>();
        facebookConfig.put("appId", facebookAppId != null && !facebookAppId.isEmpty() ? facebookAppId : "");
        facebookConfig.put("version", "v24.0");
        config.put("facebook", facebookConfig);
        
        // API configuration
        Map<String, Object> apiConfig = new HashMap<>();
        apiConfig.put("basePath", contextPath);
        Map<String, Object> endpoints = new HashMap<>();
        endpoints.put("auth", contextPath + "/auth");
        Map<String, String> facebookEndpoints = new HashMap<>();
        facebookEndpoints.put("config", contextPath + "/auth/facebook/config");
        facebookEndpoints.put("login", contextPath + "/auth/facebook/login");
        endpoints.put("facebook", facebookEndpoints);
        apiConfig.put("endpoints", endpoints);
        config.put("api", apiConfig);
        
        // Knowledge Graph configuration (public endpoints only)
        Map<String, Object> kgConfig = new HashMap<>();
        kgConfig.put("endpoint", knowledgeGraphEndpoint);
        kgConfig.put("baseUrl", fusekiBaseUrl);
        config.put("knowledgeGraph", kgConfig);
        
        // Feature flags
        Map<String, Boolean> features = new HashMap<>();
        features.put("facebookLogin", facebookAppId != null && !facebookAppId.isEmpty());
        features.put("twitterLogin", false);
        features.put("instagramLogin", false);
        config.put("features", features);
        
        return ResponseEntity.ok(config);
    }
    
    @PostMapping("/facebook/login")
    public ResponseEntity<?> loginWithFacebook(
            @Valid @RequestBody FacebookLoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            // Input validation
            if (request.getAccessToken() == null || request.getAccessToken().trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Access token is required"));
            }
            
            // CSRF protection - validate CSRF token if present
            // Note: For stateless JWT auth, CSRF protection is less critical but still recommended
            // We'll validate Origin header as an additional security measure
            String origin = httpRequest.getHeader("Origin");
            String referer = httpRequest.getHeader("Referer");
            if (origin != null && !isValidOrigin(origin)) {
                log.warn("Invalid origin for Facebook login: {}", origin);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Invalid origin"));
            }
            
            // Validate Facebook access token using secure method (Authorization header)
            Map<String, Object> facebookUser;
            try {
                facebookUser = facebookTokenValidationService.validateTokenAndGetUser(request.getAccessToken());
            } catch (FacebookTokenValidationService.FacebookTokenException e) {
                log.warn("Facebook token validation failed: {} - {}", e.getErrorCode(), e.getMessage());
                
                // Return appropriate error based on error code
                if ("TOKEN_EXPIRED".equals(e.getErrorCode())) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("error", "Facebook token expired. Please login again.", 
                                        "errorCode", e.getErrorCode()));
                } else if ("INVALID_TOKEN".equals(e.getErrorCode())) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("error", "Invalid Facebook access token", 
                                        "errorCode", e.getErrorCode()));
                } else {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("error", "Failed to verify Facebook access token: " + e.getMessage(),
                                        "errorCode", e.getErrorCode()));
                }
            }
            
            // Extract and validate user information
            String facebookUserId = sanitizeInput((String) facebookUser.get("id"));
            String email = sanitizeEmail((String) facebookUser.get("email"));
            String name = sanitizeInput((String) facebookUser.get("name"));
            String firstName = sanitizeInput((String) facebookUser.get("first_name"));
            String lastName = sanitizeInput((String) facebookUser.get("last_name"));
            
            // Fallback to name parsing if first_name/last_name not available
            if ((firstName == null || firstName.isEmpty()) && name != null && !name.isEmpty()) {
                String[] nameParts = name.split(" ", 2);
                firstName = nameParts[0];
                lastName = nameParts.length > 1 ? nameParts[1] : "";
            }
            
            // Validate required fields
            if (facebookUserId == null || facebookUserId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Facebook user ID not found"));
            }
            
            // Use email or fallback to Facebook ID-based email
            if (email == null || email.isEmpty()) {
                email = facebookUserId + "@facebook.com";
            }
            
            // Validate email format
            if (!isValidEmail(email)) {
                log.warn("Invalid email format from Facebook: {}", email);
                email = facebookUserId + "@facebook.com";
            }
            
            // Find or create user
            User user = userRepository.findByEmail(email).orElse(null);
            
            if (user == null) {
                // Create new user from Facebook
                // Facebook users are considered verified since Facebook already verified their email
                user = User.builder()
                        .email(email)
                        .passwordHash("") // No password for social login
                        .firstName(firstName != null ? firstName : "")
                        .lastName(lastName != null ? lastName : "")
                        .role(Role.USER)
                        .isActive(true)
                        .emailVerified(true) // Facebook users are pre-verified
                        .build();
                user = userRepository.save(user);
                log.info("Created new user from Facebook login: {}", email);
            } else {
                // Update last login for existing user
                // Ensure Facebook users are marked as verified
                Boolean emailVerified = user.getEmailVerified();
                if (emailVerified == null || !emailVerified) {
                    user.setEmailVerified(true);
                }
                user.setLastLogin(LocalDateTime.now());
                userRepository.save(user);
            }
            
            // Generate JWT token
            String token = tokenProvider.generateToken(
                    user.getEmail(),
                    user.getId(),
                    user.getRole().name()
            );
            
            // Set secure HTTP-only cookies
            // Use HttpServletResponse.addCookie with proper security settings
            Cookie cookie = createSecureCookie("token", token, "/api/v1", 7 * 24 * 60 * 60);
            httpResponse.addCookie(cookie);
            
            // Also set cookie for root path
            Cookie rootCookie = createSecureCookie("token", token, "/", 7 * 24 * 60 * 60);
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
        } catch (Exception e) {
            log.error("Facebook login error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process Facebook login"));
        }
    }
    
    /**
     * Create a secure HTTP-only cookie
     */
    private Cookie createSecureCookie(String name, String value, String path, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true); // Prevent JavaScript access (XSS protection)
        cookie.setSecure(isProduction()); // HTTPS only in production
        cookie.setPath(path);
        cookie.setMaxAge(maxAge);
        cookie.setAttribute("SameSite", "Lax"); // CSRF protection
        return cookie;
    }
    
    /**
     * Check if running in production environment
     */
    private boolean isProduction() {
        String profile = System.getProperty("spring.profiles.active", "");
        return profile.contains("prod") || profile.contains("production");
    }
    
    /**
     * Validate origin header for CSRF protection
     */
    private boolean isValidOrigin(String origin) {
        // In production, validate against allowed origins
        // For development, allow localhost origins
        if (origin == null) {
            return false;
        }
        
        // Allow localhost for development
        if (origin.startsWith("http://localhost") || origin.startsWith("https://localhost")) {
            return true;
        }
        
        // In production, check against configured allowed origins
        // This should match your CORS configuration
        // For now, we'll be permissive but log suspicious origins
        return true; // TODO: Implement origin whitelist in production
    }
    
    /**
     * Sanitize input to prevent XSS
     */
    private String sanitizeInput(String input) {
        if (input == null) {
            return null;
        }
        // Remove potential XSS characters
        return input.replaceAll("[<>\"']", "").trim();
    }
    
    /**
     * Sanitize and validate email
     */
    private String sanitizeEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }
        // Basic email sanitization
        String sanitized = email.trim().toLowerCase();
        // Remove potential XSS characters
        sanitized = sanitized.replaceAll("[<>\"']", "");
        return sanitized;
    }
    
    /**
     * Validate email format
     */
    private boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        // Basic email validation regex
        String emailRegex = "^[A-Za-z0-9+_.-]+@(.+)$";
        return email.matches(emailRegex);
    }
    
    // Inner class for registration request
    @lombok.Data
    public static class RegisterRequest {
        @jakarta.validation.constraints.Email(message = "Email must be a valid email address")
        @jakarta.validation.constraints.NotBlank(message = "Email is required")
        private String email;
        
        @jakarta.validation.constraints.NotBlank(message = "Password is required")
        @jakarta.validation.constraints.Size(min = 6, message = "Password must be at least 6 characters long")
        private String password;
        
        @jakarta.validation.constraints.NotBlank(message = "First name is required")
        private String firstName;
        
        @jakarta.validation.constraints.NotBlank(message = "Last name is required")
        private String lastName;
        
        private String role; // Optional: "USER" or "ADMIN"
    }
    
    // Inner class for Facebook login request
    @lombok.Data
    public static class FacebookLoginRequest {
        @jakarta.validation.constraints.NotBlank(message = "Access token is required")
        private String accessToken;
        
        private String userId; // Optional, will be validated from Facebook API
        private String name; // Optional, will be validated from Facebook API
        private String email; // Optional, will be validated from Facebook API
    }
}

