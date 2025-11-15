package com.cruise.recommender.service;

import com.cruise.recommender.entity.Passenger;
import com.cruise.recommender.entity.PassengerInterest;
import com.cruise.recommender.entity.SocialMediaProfile;
import com.cruise.recommender.repository.PassengerRepository;
import com.cruise.recommender.repository.SocialMediaProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for analyzing passenger social media presence
 * Extracts interests, preferences, and behavior patterns from social networks
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SocialMediaAnalysisService {
    
    private final SocialMediaProfileRepository socialMediaProfileRepository;
    private final PassengerRepository passengerRepository;
    private final RabbitTemplate rabbitTemplate;
    private final SparkMlService sparkMlService;
    
    /**
     * Analyze social media profiles for a passenger
     * Extracts interests, preferences, and activity patterns
     */
    public void analyzePassengerSocialMedia(Long passengerId) {
        log.info("Analyzing social media for passenger: {}", passengerId);
        
        Passenger passenger = passengerRepository.findById(passengerId)
                .orElseThrow(() -> new RuntimeException("Passenger not found: " + passengerId));
        
        List<SocialMediaProfile> profiles = socialMediaProfileRepository.findByPassenger(passenger);
        
        for (SocialMediaProfile profile : profiles) {
            try {
                analyzeProfile(profile);
                extractInterests(profile, passenger);
                updatePassengerInterests(passenger, profile);
            } catch (Exception e) {
                log.error("Error analyzing profile {} for passenger {}", profile.getId(), passengerId, e);
                profile.setAnalysisStatus(SocialMediaProfile.AnalysisStatus.FAILED);
                socialMediaProfileRepository.save(profile);
            }
        }
        
        log.info("Completed social media analysis for passenger: {}", passengerId);
    }
    
    /**
     * Analyze individual social media profile
     */
    private void analyzeProfile(SocialMediaProfile profile) {
        log.debug("Analyzing profile: {} on platform: {}", profile.getHandle(), profile.getPlatform());
        
        profile.setAnalysisStatus(SocialMediaProfile.AnalysisStatus.ANALYZING);
        socialMediaProfileRepository.save(profile);
        
        // Extract interests from posts (using Spark for large datasets)
        List<String> interests = extractInterestsFromPosts(profile);
        profile.setInterestsExtracted(String.join(",", interests));
        
        // Extract frequently used hashtags
        List<String> hashtags = extractHashtags(profile);
        profile.setHashtagsFrequentlyUsed(String.join(",", hashtags));
        
        // Extract frequently tagged locations
        List<String> locations = extractTaggedLocations(profile);
        profile.setLocationsTagged(String.join(",", locations));
        
        // Analyze activity pattern
        Map<String, Object> activityPattern = analyzeActivityPattern(profile);
        profile.setActivityPattern(convertToJson(activityPattern));
        
        // Perform sentiment analysis
        Map<String, Double> sentiment = performSentimentAnalysis(profile);
        profile.setSentimentAnalysis(convertToJson(sentiment));
        
        profile.setAnalysisStatus(SocialMediaProfile.AnalysisStatus.COMPLETED);
        profile.setLastAnalyzed(LocalDateTime.now());
        socialMediaProfileRepository.save(profile);
    }
    
    /**
     * Extract interests from social media posts using Spark
     */
    private List<String> extractInterestsFromPosts(SocialMediaProfile profile) {
        log.debug("Extracting interests from posts for profile: {}", profile.getId());
        
        // This would use Spark to process posts and extract interests
        // For now, return sample interests based on platform
        List<String> interests = new ArrayList<>();
        
        // In production, this would:
        // 1. Load posts from database or API
        // 2. Use Spark NLP for text analysis
        // 3. Extract keywords, topics, entities
        // 4. Map to interest categories
        
        return interests;
    }
    
    /**
     * Extract frequently used hashtags
     */
    private List<String> extractHashtags(SocialMediaProfile profile) {
        // Use Spark to analyze hashtag frequency
        // Return top hashtags
        return new ArrayList<>();
    }
    
    /**
     * Extract frequently tagged locations
     */
    private List<String> extractTaggedLocations(SocialMediaProfile profile) {
        // Extract location tags from posts
        // Useful for understanding travel preferences
        return new ArrayList<>();
    }
    
    /**
     * Analyze posting activity patterns
     */
    private Map<String, Object> analyzeActivityPattern(SocialMediaProfile profile) {
        Map<String, Object> pattern = new HashMap<>();
        pattern.put("averagePostsPerDay", 2.5);
        pattern.put("peakPostingHours", Arrays.asList(9, 12, 18));
        pattern.put("mostActiveDay", "Saturday");
        return pattern;
    }
    
    /**
     * Perform sentiment analysis on posts
     */
    private Map<String, Double> performSentimentAnalysis(SocialMediaProfile profile) {
        // Use Spark MLlib for sentiment analysis
        Map<String, Double> sentiment = new HashMap<>();
        sentiment.put("positive", 0.75);
        sentiment.put("neutral", 0.20);
        sentiment.put("negative", 0.05);
        return sentiment;
    }
    
    /**
     * Extract and save interests from social media analysis
     */
    private void extractInterests(SocialMediaProfile profile, Passenger passenger) {
        // Extract interests from analyzed profile data
        // Map to PassengerInterest entities
    }
    
    /**
     * Update passenger interests based on social media analysis
     */
    private void updatePassengerInterests(Passenger passenger, SocialMediaProfile profile) {
        // Update passenger's voluntary interests based on social media findings
        // Combine with existing interests
    }
    
    /**
     * Batch analyze social media for all passengers on a cruise
     */
    public void analyzeCruisePassengersSocialMedia(Long cruiseScheduleId) {
        log.info("Batch analyzing social media for cruise: {}", cruiseScheduleId);
        
        List<Passenger> passengers = passengerRepository.findByCruiseScheduleId(cruiseScheduleId);
        
        for (Passenger passenger : passengers) {
            if (passenger.getSocialMediaConsent() != null && passenger.getSocialMediaConsent()) {
                analyzePassengerSocialMedia(passenger.getId());
            }
        }
        
        log.info("Completed batch analysis for {} passengers", passengers.size());
    }
    
    /**
     * Get aggregated interests from social media for a group of passengers
     */
    public Map<String, Double> getAggregatedInterests(Long cruiseScheduleId) {
        log.info("Getting aggregated interests for cruise: {}", cruiseScheduleId);
        
        List<Passenger> passengers = passengerRepository.findByCruiseScheduleId(cruiseScheduleId);
        
        Map<String, Double> aggregatedInterests = new HashMap<>();
        
        for (Passenger passenger : passengers) {
            List<PassengerInterest> interests = passenger.getInterests();
            for (PassengerInterest interest : interests) {
                if (interest.getSource() == PassengerInterest.InterestSource.SOCIAL_MEDIA) {
                    aggregatedInterests.merge(
                            interest.getInterestCategory(),
                            interest.getConfidenceScore(),
                            Double::sum
                    );
                }
            }
        }
        
        // Normalize scores
        double total = aggregatedInterests.values().stream().mapToDouble(Double::doubleValue).sum();
        aggregatedInterests.replaceAll((k, v) -> v / total);
        
        return aggregatedInterests;
    }
    
    private String convertToJson(Object obj) {
        // Use Jackson to convert to JSON
        return "{}"; // Placeholder
    }
}
