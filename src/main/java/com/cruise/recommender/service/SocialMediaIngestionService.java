package com.cruise.recommender.service;

import com.cruise.recommender.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for ingesting social media data from Facebook, Twitter, and Instagram
 * Extracts passenger interests and preferences from social media posts
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SocialMediaIngestionService {
    
    private final RabbitTemplate rabbitTemplate;
    private final RestTemplate restTemplate = new RestTemplate();
    
    // Facebook API Configuration
    @Value("${social.media.facebook.app.id:}")
    private String facebookAppId;
    
    @Value("${social.media.facebook.app.secret:}")
    private String facebookAppSecret;
    
    @Value("${social.media.facebook.access.token:}")
    private String facebookAccessToken;
    
    // Twitter API Configuration
    @Value("${social.media.twitter.consumer.key:}")
    private String twitterConsumerKey;
    
    @Value("${social.media.twitter.consumer.secret:}")
    private String twitterConsumerSecret;
    
    @Value("${social.media.twitter.access.token:}")
    private String twitterAccessToken;
    
    @Value("${social.media.twitter.access.token.secret:}")
    private String twitterAccessTokenSecret;
    
    // Instagram API Configuration
    @Value("${social.media.instagram.client.id:}")
    private String instagramClientId;
    
    @Value("${social.media.instagram.client.secret:}")
    private String instagramClientSecret;
    
    @Value("${social.media.instagram.access.token:}")
    private String instagramAccessToken;
    
    // Configuration
    @Value("${social.media.ingestion.enabled:true}")
    private boolean ingestionEnabled;
    
    @Value("${social.media.simulation.enabled:true}")
    private boolean simulationEnabled;
    
    @Value("${social.media.ingestion.interval:600000}") // 10 minutes
    private long ingestionInterval;
    
    private final Random random = new Random();
    
    /**
     * Scheduled task to fetch social media data
     * Runs every 10 minutes by default
     */
    @Scheduled(fixedRateString = "${social.media.ingestion.interval:600000}")
    public void ingestSocialMediaData() {
        if (!ingestionEnabled) {
            log.debug("Social media ingestion is disabled");
            return;
        }
        
        log.debug("Starting social media data ingestion cycle");
        
        try {
            List<SocialMediaPost> posts = new ArrayList<>();
            
            if (simulationEnabled) {
                // Generate simulated social media data for testing
                posts.addAll(generateSimulatedFacebookPosts());
                posts.addAll(generateSimulatedTwitterPosts());
                posts.addAll(generateSimulatedInstagramPosts());
            } else {
                // Fetch from real APIs
                posts.addAll(fetchFacebookPosts());
                posts.addAll(fetchTwitterPosts());
                posts.addAll(fetchInstagramPosts());
            }
            
            // Send each post to RabbitMQ for processing
            for (SocialMediaPost post : posts) {
                rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SOCIAL_MEDIA_EXCHANGE,
                    "social.media.post",
                    post
                );
                log.debug("Sent social media post to queue: {} from {}", post.getPlatform(), post.getAuthorId());
            }
            
            log.info("Ingested {} social media posts", posts.size());
            
        } catch (Exception e) {
            log.error("Error during social media data ingestion", e);
        }
    }
    
    /**
     * Fetch posts from Facebook Graph API
     */
    private List<SocialMediaPost> fetchFacebookPosts() {
        List<SocialMediaPost> posts = new ArrayList<>();
        
        if (facebookAccessToken == null || facebookAccessToken.isEmpty()) {
            log.warn("Facebook access token not configured");
            return posts;
        }
        
        try {
            // Search for posts related to cruise ports, activities, and destinations
            String[] searchTerms = {
                "cruise port", "shore excursion", "port activities",
                "cruise ship", "port of call", "cruise destination"
            };
            
            for (String term : searchTerms) {
                String url = String.format(
                    "https://graph.facebook.com/v18.0/search?q=%s&type=post&access_token=%s&limit=10",
                    term, facebookAccessToken
                );
                
                // Make API call and parse response
                // Note: Actual implementation would parse Facebook Graph API response
                // This is a placeholder structure
            }
            
        } catch (Exception e) {
            log.error("Error fetching Facebook posts", e);
        }
        
        return posts;
    }
    
    /**
     * Fetch tweets from Twitter API
     */
    private List<SocialMediaPost> fetchTwitterPosts() {
        List<SocialMediaPost> posts = new ArrayList<>();
        
        if (twitterAccessToken == null || twitterAccessToken.isEmpty()) {
            log.warn("Twitter access token not configured");
            return posts;
        }
        
        try {
            // Search for tweets related to cruise ports and activities
            String[] searchTerms = {
                "cruise port", "shore excursion", "#cruiseport",
                "#cruiseship", "port activities", "#cruisedestination"
            };
            
            for (String term : searchTerms) {
                // Use Twitter API v2 search endpoint
                // Note: Actual implementation would use Twitter4J or Twitter API v2
                // This is a placeholder structure
            }
            
        } catch (Exception e) {
            log.error("Error fetching Twitter posts", e);
        }
        
        return posts;
    }
    
    /**
     * Fetch posts from Instagram Basic Display API
     */
    private List<SocialMediaPost> fetchInstagramPosts() {
        List<SocialMediaPost> posts = new ArrayList<>();
        
        if (instagramAccessToken == null || instagramAccessToken.isEmpty()) {
            log.warn("Instagram access token not configured");
            return posts;
        }
        
        try {
            // Fetch user media from Instagram Basic Display API
            String url = String.format(
                "https://graph.instagram.com/me/media?fields=id,caption,media_type,media_url,timestamp&access_token=%s",
                instagramAccessToken
            );
            
            // Make API call and parse response
            // Note: Actual implementation would parse Instagram API response
            // This is a placeholder structure
            
        } catch (Exception e) {
            log.error("Error fetching Instagram posts", e);
        }
        
        return posts;
    }
    
    /**
     * Generate simulated Facebook posts for testing
     */
    private List<SocialMediaPost> generateSimulatedFacebookPosts() {
        List<SocialMediaPost> posts = new ArrayList<>();
        
        String[] authors = {"john.doe", "jane.smith", "cruise.lover", "travel.enthusiast"};
        String[] locations = {"Miami, FL", "Barcelona, Spain", "Venice, Italy", "New York, NY"};
        String[] interests = {
            "Just visited the amazing art galleries in Barcelona! #cruiseport #barcelona",
            "Best shore excursion ever! Snorkeling in the Caribbean was incredible!",
            "Love exploring local markets at cruise ports. Found amazing souvenirs!",
            "The food in Venice is out of this world! Highly recommend the local restaurants.",
            "Beautiful sunset at the port of Miami. Can't wait for the next cruise!"
        };
        
        for (int i = 0; i < 3; i++) {
            SocialMediaPost post = SocialMediaPost.builder()
                .platform("FACEBOOK")
                .postId("fb_" + UUID.randomUUID().toString())
                .authorId(authors[random.nextInt(authors.length)])
                .content(interests[random.nextInt(interests.length)])
                .location(locations[random.nextInt(locations.length)])
                .timestamp(LocalDateTime.now().minusHours(random.nextInt(24)))
                .likes(random.nextInt(100))
                .shares(random.nextInt(50))
                .comments(random.nextInt(30))
                .hashtags(extractHashtags(interests[random.nextInt(interests.length)]))
                .keywords(extractKeywords(interests[random.nextInt(interests.length)]))
                .build();
            
            posts.add(post);
        }
        
        return posts;
    }
    
    /**
     * Generate simulated Twitter posts for testing
     */
    private List<SocialMediaPost> generateSimulatedTwitterPosts() {
        List<SocialMediaPost> posts = new ArrayList<>();
        
        String[] authors = {"@cruisefan", "@travelbug", "@portexplorer", "@shipspotter"};
        String[] locations = {"Miami, FL", "Barcelona, Spain", "Venice, Italy"};
        String[] interests = {
            "Just docked at #Miami! Excited to explore the city! #cruiseport #cruiseship",
            "Amazing #shoreexcursion today! Snorkeling was incredible! #cruise #travel",
            "Love the local culture at cruise ports! #portofcall #travel",
            "Best food ever at the port restaurants! #cruiseport #foodie",
            "Beautiful architecture in Barcelona! #cruiseport #barcelona #travel"
        };
        
        for (int i = 0; i < 5; i++) {
            SocialMediaPost post = SocialMediaPost.builder()
                .platform("TWITTER")
                .postId("tw_" + UUID.randomUUID().toString())
                .authorId(authors[random.nextInt(authors.length)])
                .content(interests[random.nextInt(interests.length)])
                .location(locations[random.nextInt(locations.length)])
                .timestamp(LocalDateTime.now().minusHours(random.nextInt(24)))
                .likes(random.nextInt(200))
                .retweets(random.nextInt(100))
                .hashtags(extractHashtags(interests[random.nextInt(interests.length)]))
                .keywords(extractKeywords(interests[random.nextInt(interests.length)]))
                .build();
            
            posts.add(post);
        }
        
        return posts;
    }
    
    /**
     * Generate simulated Instagram posts for testing
     */
    private List<SocialMediaPost> generateSimulatedInstagramPosts() {
        List<SocialMediaPost> posts = new ArrayList<>();
        
        String[] authors = {"cruise_lover", "port_explorer", "travel_enthusiast", "ship_spotter"};
        String[] locations = {"Miami, FL", "Barcelona, Spain", "Venice, Italy"};
        String[] interests = {
            "Beautiful sunset at the port! #cruiseport #sunset #travel",
            "Amazing art galleries! #barcelona #cruiseport #art",
            "Best seafood ever! #venice #foodie #cruiseport",
            "Exploring local markets! #cruiseport #shopping #travel",
            "Incredible architecture! #barcelona #cruiseport #architecture"
        };
        
        for (int i = 0; i < 4; i++) {
            SocialMediaPost post = SocialMediaPost.builder()
                .platform("INSTAGRAM")
                .postId("ig_" + UUID.randomUUID().toString())
                .authorId(authors[random.nextInt(authors.length)])
                .content(interests[random.nextInt(interests.length)])
                .location(locations[random.nextInt(locations.length)])
                .timestamp(LocalDateTime.now().minusHours(random.nextInt(24)))
                .likes(random.nextInt(500))
                .hashtags(extractHashtags(interests[random.nextInt(interests.length)]))
                .keywords(extractKeywords(interests[random.nextInt(interests.length)]))
                .build();
            
            posts.add(post);
        }
        
        return posts;
    }
    
    private List<String> extractHashtags(String content) {
        List<String> hashtags = new ArrayList<>();
        String[] words = content.split("\\s+");
        for (String word : words) {
            if (word.startsWith("#")) {
                hashtags.add(word.substring(1));
            }
        }
        return hashtags;
    }
    
    private List<String> extractKeywords(String content) {
        List<String> keywords = new ArrayList<>();
        String[] commonKeywords = {
            "cruise", "port", "ship", "excursion", "travel", "destination",
            "food", "restaurant", "shopping", "art", "culture", "beach",
            "snorkeling", "diving", "sightseeing", "tour", "activity"
        };
        
        String lowerContent = content.toLowerCase();
        for (String keyword : commonKeywords) {
            if (lowerContent.contains(keyword)) {
                keywords.add(keyword);
            }
        }
        
        return keywords;
    }
    
    /**
     * DTO for social media posts
     */
    @lombok.Data
    @lombok.Builder
    public static class SocialMediaPost {
        private String platform; // FACEBOOK, TWITTER, INSTAGRAM
        private String postId;
        private String authorId;
        private String content;
        private String location;
        private LocalDateTime timestamp;
        private Integer likes;
        private Integer shares;
        private Integer comments;
        private Integer retweets; // Twitter specific
        private List<String> hashtags;
        private List<String> keywords;
        private String mediaUrl; // For Instagram images
        private String mediaType; // photo, video, etc.
    }
}

