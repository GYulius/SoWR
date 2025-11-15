package com.cruise.recommender.repository;

import com.cruise.recommender.entity.AisData;
import com.cruise.recommender.entity.CruiseShip;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for AIS Data operations
 */
@Repository
public interface AisDataRepository extends JpaRepository<AisData, Long> {
    
    List<AisData> findByMmsiOrderByTimestampDesc(String mmsi);
    
    List<AisData> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
    
    Page<AisData> findByTimestampBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
    
    @Query("SELECT a FROM AisData a WHERE a.latitude BETWEEN :minLat AND :maxLat " +
           "AND a.longitude BETWEEN :minLng AND :maxLng " +
           "AND a.timestamp >= :since")
    List<AisData> findInAreaSince(
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLng") Double minLng,
            @Param("maxLng") Double maxLng,
            @Param("since") LocalDateTime since);
    
    @Query("SELECT a FROM AisData a WHERE a.cruiseShip.id = :shipId " +
           "ORDER BY a.timestamp DESC")
    List<AisData> findByShipIdOrderByTimestampDesc(@Param("shipId") Long shipId);
    
    @Query("SELECT COUNT(DISTINCT a.mmsi) FROM AisData a WHERE a.timestamp >= :since")
    Long countActiveShipsSince(@Param("since") LocalDateTime since);
}
