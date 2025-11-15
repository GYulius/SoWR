package com.cruise.recommender.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Social Media Profile entity for analyzing passenger digital presence
 */
@Entity
@Table(name = "social_media_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class SocialMediaProfile {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_id", nullable = false)
    private Passenger passenger;
    
    @Column(name = "platform", nullable = false)
    @Enumerated(EnumType.STRING)
    private SocialPlatform platform;
    
    @Column(name = "handle", nullable = false)
    private String handle;
    
    @Column(name = "profile_url")
    private String profileUrl;
    
    @Column(name = "follower_count")
    private Long followerCount;
    
    @Column(name = "following_count")
    private Long followingCount;
    
    @Column(name = "post_count")
    private Long postCount;
    
    @Column(name = "engagement_rate")
    private Double engagementRate;
    
    @Column(name = "interests_extracted", columnDefinition = "JSON")
    private String interestsExtracted; // Interests extracted from posts
    
    @Column(name = "hashtags_frequently_used", columnDefinition = "JSON")
    private String hashtagsFrequentlyUsed;
    
    @Column(name = "locations_tagged", columnDefinition = "JSON")
    private String locationsTagged; // Frequently tagged locations
    
    @Column(name = "activity_pattern", columnDefinition = "JSON")
    private String activityPattern; // Posting times, frequency
    
    @Column(name = "sentiment_analysis", columnDefinition = "JSON")
    private String sentimentAnalysis; // Overall sentiment from posts
    
    @Column(name = "last_analyzed")
    private LocalDateTime lastAnalyzed;
    
    @Column(name = "analysis_status")
    @Enumerated(EnumType.STRING)
    private AnalysisStatus analysisStatus;
    
    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Relationships
    @OneToMany(mappedBy = "socialMediaProfile", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SocialMediaPost> posts;
    
    public enum SocialPlatform {
        TWITTER, INSTAGRAM, FACEBOOK, LINKEDIN, TIKTOK
    }
    
    public enum AnalysisStatus {
        PENDING, ANALYZING, COMPLETED, FAILED
    }
}
