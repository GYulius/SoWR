package com.cruise.recommender.service;

import com.cruise.recommender.dto.PublisherRequest;
import com.cruise.recommender.dto.PublisherResponse;
import com.cruise.recommender.entity.*;
import com.cruise.recommender.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for Publisher operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PublisherService {
    
    private final PublisherRepository publisherRepository;
    private final UserRepository userRepository;
    private final PortRepository portRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final NotificationRepository notificationRepository;
    private final CruiseScheduleRepository cruiseScheduleRepository;
    
    /**
     * Get all publishers with optional filtering
     */
    @Transactional(readOnly = true)
    public Page<PublisherResponse> getPublishers(Long portId, String businessType, 
                                                  Boolean verifiedOnly, Pageable pageable) {
        log.info("Getting publishers with filters: portId={}, businessType={}, verifiedOnly={}", 
                portId, businessType, verifiedOnly);
        
        Page<Publisher> publishers = publisherRepository.findPublishersWithFilters(
                portId, businessType, verifiedOnly, pageable);
        
        return publishers.map(this::convertToResponse);
    }
    
    /**
     * Create a new publisher
     */
    public PublisherResponse createPublisher(String userEmail, PublisherRequest request) {
        log.info("Creating publisher for user {}", userEmail);
        
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));
        
        if (publisherRepository.existsByUserId(user.getId())) {
            throw new RuntimeException("User already has a publisher account");
        }
        
        Publisher publisher = Publisher.builder()
                .user(user)
                .businessName(request.getBusinessName())
                .businessType(request.getBusinessType())
                .description(request.getDescription())
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .website(request.getWebsite())
                .address(request.getAddress())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .verificationStatus(Publisher.VerificationStatus.PENDING)
                .isActive(true)
                .build();
        
        if (request.getLatitude() != null && request.getLongitude() != null) {
            // Optionally find and set port based on location
            // This would require geospatial query - simplified for now
        }
        
        Publisher savedPublisher = publisherRepository.save(publisher);
        
        return convertToResponse(savedPublisher);
    }
    
    /**
     * Get publisher by ID
     */
    @Transactional(readOnly = true)
    public PublisherResponse getPublisherById(Long id) {
        log.info("Getting publisher by ID: {}", id);
        
        Publisher publisher = publisherRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Publisher not found: " + id));
        
        return convertToResponse(publisher);
    }
    
    /**
     * Update publisher information
     */
    public PublisherResponse updatePublisher(String userEmail, Long id, PublisherRequest request) {
        log.info("Updating publisher {} by user {}", id, userEmail);
        
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));
        
        Publisher publisher = publisherRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Publisher not found: " + id));
        
        // Verify ownership
        if (!publisher.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied: User does not own this publisher");
        }
        
        // Update fields
        publisher.setBusinessName(request.getBusinessName());
        publisher.setBusinessType(request.getBusinessType());
        publisher.setDescription(request.getDescription());
        publisher.setContactEmail(request.getContactEmail());
        publisher.setContactPhone(request.getContactPhone());
        publisher.setWebsite(request.getWebsite());
        publisher.setAddress(request.getAddress());
        publisher.setLatitude(request.getLatitude());
        publisher.setLongitude(request.getLongitude());
        
        Publisher updatedPublisher = publisherRepository.save(publisher);
        
        return convertToResponse(updatedPublisher);
    }
    
    /**
     * Publish content (offers, events, updates)
     */
    public void publishContent(String userEmail, Long id, String contentType, 
                              String title, String description, String data, String expiresAt) {
        log.info("Publishing content for publisher {} by user {}", id, userEmail);
        
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));
        
        Publisher publisher = publisherRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Publisher not found: " + id));
        
        // Verify ownership
        if (!publisher.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied: User does not own this publisher");
        }
        
        // Get active subscribers
        List<Subscription> activeSubscriptions = subscriptionRepository
                .findActiveSubscriptionsByPublisherId(id);
        
        // Create notifications for all subscribers
        for (Subscription subscription : activeSubscriptions) {
            Notification notification = Notification.builder()
                    .user(subscription.getUser())
                    .publisher(publisher)
                    .title(title)
                    .message(description)
                    .type(Notification.NotificationType.PUBLISHER_UPDATE)
                    .status(Notification.NotificationStatus.UNREAD)
                    .actionUrl("/publishers/" + id + "/content")
                    .build();
            
            notificationRepository.save(notification);
        }
        
        log.info("Published content to {} subscribers", activeSubscriptions.size());
    }
    
    /**
     * Get publisher content (simplified - in production, would use a Content entity)
     */
    @Transactional(readOnly = true)
    public List<Object> getPublisherContent(Long id, String contentType, Integer page, Integer size) {
        log.info("Getting content for publisher {}", id);
        
        Publisher publisher = publisherRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Publisher not found: " + id));
        
        // For now, return notifications sent by this publisher
        // In production, this would query a separate Content entity
        List<Notification> notifications = notificationRepository.findByPublisherId(id);
        
        return notifications.stream()
                .filter(n -> contentType == null || n.getType().name().equals(contentType))
                .map(n -> {
                    Map<String, Object> content = new HashMap<>();
                    content.put("id", n.getId());
                    content.put("title", n.getTitle());
                    content.put("message", n.getMessage());
                    content.put("type", n.getType().name());
                    content.put("createdAt", n.getCreatedAt());
                    content.put("actionUrl", n.getActionUrl());
                    return content;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Get subscriber analytics
     */
    @Transactional(readOnly = true)
    public Object getSubscriberAnalytics(String userEmail, Long id) {
        log.info("Getting subscriber analytics for publisher {} by user {}", id, userEmail);
        
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));
        
        Publisher publisher = publisherRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Publisher not found: " + id));
        
        // Verify ownership
        if (!publisher.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied: User does not own this publisher");
        }
        
        Long totalSubscribers = subscriptionRepository.countActiveSubscriptionsByPublisherId(id);
        List<Subscription> allSubscriptions = subscriptionRepository.findByPublisherId(id);
        
        Map<String, Object> analytics = new HashMap<>();
        analytics.put("totalSubscribers", totalSubscribers);
        analytics.put("totalSubscriptions", allSubscriptions.size());
        analytics.put("activeSubscriptions", allSubscriptions.stream()
                .filter(s -> s.getStatus() == Subscription.SubscriptionStatus.ACTIVE)
                .count());
        analytics.put("pausedSubscriptions", allSubscriptions.stream()
                .filter(s -> s.getStatus() == Subscription.SubscriptionStatus.PAUSED)
                .count());
        analytics.put("cancelledSubscriptions", allSubscriptions.stream()
                .filter(s -> s.getStatus() == Subscription.SubscriptionStatus.CANCELLED)
                .count());
        
        return analytics;
    }
    
    /**
     * Send cruise arrival alert to subscribers
     */
    public void sendCruiseAlert(String userEmail, Long id, Long cruiseScheduleId, String customMessage) {
        log.info("Sending cruise alert for publisher {} by user {}", id, userEmail);
        
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));
        
        Publisher publisher = publisherRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Publisher not found: " + id));
        
        // Verify ownership
        if (!publisher.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied: User does not own this publisher");
        }
        
        CruiseSchedule cruiseSchedule = cruiseScheduleRepository.findById(cruiseScheduleId)
                .orElseThrow(() -> new RuntimeException("Cruise schedule not found: " + cruiseScheduleId));
        
        // Get active subscribers
        List<Subscription> activeSubscriptions = subscriptionRepository
                .findActiveSubscriptionsByPublisherId(id);
        
        String message = customMessage != null ? customMessage : 
                "A cruise ship is approaching! Get ready for visitors.";
        
        // Create notifications for all subscribers
        for (Subscription subscription : activeSubscriptions) {
            Notification notification = Notification.builder()
                    .user(subscription.getUser())
                    .publisher(publisher)
                    .title("Cruise Ship Approaching")
                    .message(message)
                    .type(Notification.NotificationType.SHIP_APPROACHING)
                    .status(Notification.NotificationStatus.UNREAD)
                    .actionUrl("/cruises/" + cruiseScheduleId)
                    .build();
            
            notificationRepository.save(notification);
        }
        
        log.info("Sent cruise alert to {} subscribers", activeSubscriptions.size());
    }
    
    /**
     * Convert Publisher entity to PublisherResponse DTO
     */
    private PublisherResponse convertToResponse(Publisher publisher) {
        Long subscriptionCount = subscriptionRepository
                .countActiveSubscriptionsByPublisherId(publisher.getId());
        
        return PublisherResponse.builder()
                .id(publisher.getId())
                .businessName(publisher.getBusinessName())
                .businessType(publisher.getBusinessType())
                .description(publisher.getDescription())
                .contactEmail(publisher.getContactEmail())
                .contactPhone(publisher.getContactPhone())
                .website(publisher.getWebsite())
                .address(publisher.getAddress())
                .latitude(publisher.getLatitude())
                .longitude(publisher.getLongitude())
                .verificationStatus(publisher.getVerificationStatus().name())
                .subscriptionCount(subscriptionCount.intValue())
                .createdAt(publisher.getCreatedAt())
                .updatedAt(publisher.getUpdatedAt())
                .build();
    }
}
