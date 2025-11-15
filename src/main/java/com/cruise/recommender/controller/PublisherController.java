package com.cruise.recommender.controller;

import com.cruise.recommender.dto.PublisherRequest;
import com.cruise.recommender.dto.PublisherResponse;
import com.cruise.recommender.service.PublisherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Publisher operations
 */
@RestController
@RequestMapping("/publishers")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Publishers", description = "Publisher (local business) management APIs")
public class PublisherController {
    
    private final PublisherService publisherService;
    
    @GetMapping
    @Operation(summary = "Get all publishers", 
               description = "Retrieve list of publishers with optional filtering")
    public ResponseEntity<Page<PublisherResponse>> getPublishers(
            @Parameter(description = "Port ID filter") @RequestParam(required = false) Long portId,
            @Parameter(description = "Business type filter") @RequestParam(required = false) String businessType,
            @Parameter(description = "Verified only") @RequestParam(defaultValue = "false") Boolean verifiedOnly,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") Integer page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") Integer size,
            Pageable pageable) {
        
        log.info("Getting publishers with filters: portId={}, businessType={}, verifiedOnly={}", 
                portId, businessType, verifiedOnly);
        
        Page<PublisherResponse> publishers = publisherService.getPublishers(
                portId, businessType, verifiedOnly, pageable);
        
        return ResponseEntity.ok(publishers);
    }
    
    @PostMapping
    @Operation(summary = "Register as publisher", 
               description = "Register a new publisher (local business)")
    public ResponseEntity<PublisherResponse> createPublisher(
            @Valid @RequestBody PublisherRequest request,
            Authentication authentication) {
        
        log.info("Creating publisher for user {}", authentication.getName());
        
        PublisherResponse publisher = publisherService.createPublisher(authentication.getName(), request);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(publisher);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get publisher details", 
               description = "Retrieve detailed information about a specific publisher")
    public ResponseEntity<PublisherResponse> getPublisher(
            @Parameter(description = "Publisher ID") @PathVariable Long id) {
        
        log.info("Getting publisher details for ID {}", id);
        
        PublisherResponse publisher = publisherService.getPublisherById(id);
        
        return ResponseEntity.ok(publisher);
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Update publisher information", 
               description = "Update publisher information (publisher only)")
    public ResponseEntity<PublisherResponse> updatePublisher(
            @Parameter(description = "Publisher ID") @PathVariable Long id,
            @Valid @RequestBody PublisherRequest request,
            Authentication authentication) {
        
        log.info("Updating publisher {} by user {}", id, authentication.getName());
        
        PublisherResponse publisher = publisherService.updatePublisher(
                authentication.getName(), id, request);
        
        return ResponseEntity.ok(publisher);
    }
    
    @PostMapping("/{id}/content")
    @Operation(summary = "Publish content", 
               description = "Publish new content (offers, events, updates)")
    public ResponseEntity<Void> publishContent(
            @Parameter(description = "Publisher ID") @PathVariable Long id,
            @Parameter(description = "Content type") @RequestParam String contentType,
            @Parameter(description = "Content title") @RequestParam String title,
            @Parameter(description = "Content description") @RequestParam String description,
            @Parameter(description = "Content data JSON") @RequestParam String data,
            @Parameter(description = "Expiration date") @RequestParam(required = false) String expiresAt,
            Authentication authentication) {
        
        log.info("Publishing content for publisher {} by user {}", id, authentication.getName());
        
        publisherService.publishContent(authentication.getName(), id, contentType, 
                title, description, data, expiresAt);
        
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/{id}/content")
    @Operation(summary = "Get publisher content", 
               description = "Retrieve published content for a specific publisher")
    public ResponseEntity<List<Object>> getPublisherContent(
            @Parameter(description = "Publisher ID") @PathVariable Long id,
            @Parameter(description = "Content type filter") @RequestParam(required = false) String contentType,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") Integer page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") Integer size) {
        
        log.info("Getting content for publisher {}", id);
        
        List<Object> content = publisherService.getPublisherContent(id, contentType, page, size);
        
        return ResponseEntity.ok(content);
    }
    
    @GetMapping("/{id}/subscribers")
    @Operation(summary = "Get publisher subscribers", 
               description = "Get subscriber count and analytics (publisher only)")
    public ResponseEntity<Object> getSubscriberAnalytics(
            @Parameter(description = "Publisher ID") @PathVariable Long id,
            Authentication authentication) {
        
        log.info("Getting subscriber analytics for publisher {} by user {}", id, authentication.getName());
        
        Object analytics = publisherService.getSubscriberAnalytics(authentication.getName(), id);
        
        return ResponseEntity.ok(analytics);
    }
    
    @PostMapping("/{id}/cruise-alert")
    @Operation(summary = "Send cruise arrival alert", 
               description = "Send cruise arrival alert to subscribers")
    public ResponseEntity<Void> sendCruiseAlert(
            @Parameter(description = "Publisher ID") @PathVariable Long id,
            @Parameter(description = "Cruise schedule ID") @RequestParam Long cruiseScheduleId,
            @Parameter(description = "Custom message") @RequestParam(required = false) String customMessage,
            Authentication authentication) {
        
        log.info("Sending cruise alert for publisher {} by user {}", id, authentication.getName());
        
        publisherService.sendCruiseAlert(authentication.getName(), id, cruiseScheduleId, customMessage);
        
        return ResponseEntity.ok().build();
    }
}
