package com.cruise.recommender.repository;

import com.cruise.recommender.entity.Recommendation;
import com.cruise.recommender.entity.User;
import com.cruise.recommender.entity.Port;
import com.cruise.recommender.entity.Recommendation.ItemType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Recommendation entity operations
 */
@Repository
public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {
    
    List<Recommendation> findByUserAndPortOrderByScoreDesc(User user, Port port);
    
    List<Recommendation> findByUserOrderByCreatedAtDesc(User user);
    
    List<Recommendation> findByUserAndPortOrderByCreatedAtDesc(User user, Port port);
    
    Page<Recommendation> findByUserAndPortOrderByCreatedAtDesc(User user, Port port, Pageable pageable);
    
    Page<Recommendation> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
    
    List<Recommendation> findByUserAndItemTypeOrderByScoreDesc(User user, ItemType itemType);
    
    @Query("SELECT r FROM Recommendation r WHERE r.user = :user AND r.port = :port AND r.score >= :minScore ORDER BY r.score DESC")
    List<Recommendation> findByUserAndPortWithMinScore(@Param("user") User user, 
                                                       @Param("port") Port port, 
                                                       @Param("minScore") Double minScore);
    
    @Query("SELECT r FROM Recommendation r WHERE r.user = :user AND r.createdAt >= :since ORDER BY r.score DESC")
    List<Recommendation> findByUserSince(@Param("user") User user, @Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(r) FROM Recommendation r WHERE r.user = :user")
    Long countByUser(@Param("user") User user);
    
    @Query("SELECT AVG(r.score) FROM Recommendation r WHERE r.user = :user")
    Double getAverageScoreByUser(@Param("user") User user);
    
    @Query("SELECT r FROM Recommendation r WHERE r.user = :user AND r.port = :port ORDER BY r.createdAt DESC LIMIT 1")
    Recommendation findLatestByUserAndPort(@Param("user") User user, @Param("port") Port port);
    
    @Query("DELETE FROM Recommendation r WHERE r.user = :user AND r.port = :port")
    void deleteByUserAndPort(@Param("user") User user, @Param("port") Port port);
    
    @Query("SELECT r FROM Recommendation r WHERE r.itemType = :itemType AND r.itemId = :itemId ORDER BY r.score DESC")
    List<Recommendation> findByItemTypeAndItemId(@Param("itemType") ItemType itemType, @Param("itemId") Long itemId);
    
    @Query("SELECT COUNT(r) FROM Recommendation r WHERE r.itemType = :itemType AND r.itemId = :itemId")
    Long countByItemTypeAndItemId(@Param("itemType") ItemType itemType, @Param("itemId") Long itemId);
}
