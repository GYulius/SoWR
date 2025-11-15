package com.cruise.recommender.repository;

import com.cruise.recommender.entity.PassengerInterest;
import com.cruise.recommender.entity.Passenger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Passenger Interest operations
 */
@Repository
public interface PassengerInterestRepository extends JpaRepository<PassengerInterest, Long> {
    
    List<PassengerInterest> findByPassenger(Passenger passenger);
    
    List<PassengerInterest> findByPassengerAndIsExplicitTrue(Passenger passenger);
    
    List<PassengerInterest> findByInterestCategory(String category);
}
