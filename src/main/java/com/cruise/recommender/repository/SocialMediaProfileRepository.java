package com.cruise.recommender.repository;

import com.cruise.recommender.entity.SocialMediaProfile;
import com.cruise.recommender.entity.Passenger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Social Media Profile operations
 */
@Repository
public interface SocialMediaProfileRepository extends JpaRepository<SocialMediaProfile, Long> {
    
    List<SocialMediaProfile> findByPassenger(Passenger passenger);
    
    List<SocialMediaProfile> findByPlatform(SocialMediaProfile.SocialPlatform platform);
    
    List<SocialMediaProfile> findByAnalysisStatus(SocialMediaProfile.AnalysisStatus status);
}
