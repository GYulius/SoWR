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

/**
 * Social Media Post entity for storing analyzed posts
 */
@Entity
@Table(name = "social_media_posts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class SocialMediaPost {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "social_media_profile_id", nullable = false)
    private SocialMediaProfile socialMediaProfile;
    
    @Column(name = "post_id")
    private String postId;
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    @Column(name = "posted_at")
    private LocalDateTime postedAt;
    
    @Column(name = "hashtags", columnDefinition = "JSON")
    private String hashtags;
    
    @Column(name = "locations", columnDefinition = "JSON")
    private String locations;
    
    @Column(name = "sentiment_score")
    private Double sentimentScore;
    
    @Column(name = "interests_detected", columnDefinition = "JSON")
    private String interestsDetected;
    
    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
