package com.cruise.recommender.repository;

import com.cruise.recommender.entity.MessageTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for RabbitMQ message tracking
 */
@Repository
public interface MessageTrackingRepository extends JpaRepository<MessageTracking, Long> {
    
    List<MessageTracking> findByProducerId(Long producerId);
    
    List<MessageTracking> findByConsumerId(Long consumerId);
    
    List<MessageTracking> findByProducerIdAndConsumerId(Long producerId, Long consumerId);
    
    @Query("SELECT COUNT(m) FROM MessageTracking m WHERE m.producerId = :producerId AND m.timestamp >= :since")
    Long countByProducerSince(@Param("producerId") Long producerId, @Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(m) FROM MessageTracking m WHERE m.consumerId = :consumerId AND m.consumed = true AND m.timestamp >= :since")
    Long countConsumedByConsumerSince(@Param("consumerId") Long consumerId, @Param("since") LocalDateTime since);
    
    @Query("SELECT m.producerId, COUNT(m) as count FROM MessageTracking m " +
           "WHERE m.timestamp >= :since GROUP BY m.producerId ORDER BY count DESC")
    List<Object[]> getProducerStatsSince(@Param("since") LocalDateTime since);
    
    @Query("SELECT m.consumerId, COUNT(m) as count FROM MessageTracking m " +
           "WHERE m.consumed = true AND m.timestamp >= :since GROUP BY m.consumerId ORDER BY count DESC")
    List<Object[]> getConsumerStatsSince(@Param("since") LocalDateTime since);
}

