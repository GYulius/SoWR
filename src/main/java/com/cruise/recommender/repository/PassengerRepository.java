package com.cruise.recommender.repository;

import com.cruise.recommender.entity.Passenger;
import com.cruise.recommender.entity.PassengerInterest;
import com.cruise.recommender.entity.SocialMediaProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Passenger operations
 */
@Repository
public interface PassengerRepository extends JpaRepository<Passenger, Long> {
    
    List<Passenger> findByCruiseScheduleId(Long cruiseScheduleId);
    
    List<Passenger> findByUserId(Long userId);
    
    List<Passenger> findBySocialMediaConsentTrue();
}
