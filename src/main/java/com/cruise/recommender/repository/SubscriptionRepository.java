package com.cruise.recommender.repository;

import com.cruise.recommender.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Subscription entity operations
 */
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    
    Optional<Subscription> findByUserIdAndPublisherId(Long userId, Long publisherId);
    
    List<Subscription> findByPublisherId(Long publisherId);
    
    List<Subscription> findByUserId(Long userId);
    
    @Query("SELECT COUNT(s) FROM Subscription s WHERE s.publisher.id = :publisherId AND s.status = 'ACTIVE'")
    Long countActiveSubscriptionsByPublisherId(@Param("publisherId") Long publisherId);
    
    @Query("SELECT s FROM Subscription s WHERE s.publisher.id = :publisherId AND s.status = 'ACTIVE'")
    List<Subscription> findActiveSubscriptionsByPublisherId(@Param("publisherId") Long publisherId);
    
    boolean existsByUserIdAndPublisherId(Long userId, Long publisherId);
}
