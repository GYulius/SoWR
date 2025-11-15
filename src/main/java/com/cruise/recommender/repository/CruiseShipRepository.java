package com.cruise.recommender.repository;

import com.cruise.recommender.entity.CruiseShip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Cruise Ship operations
 */
@Repository
public interface CruiseShipRepository extends JpaRepository<CruiseShip, Long> {
    
    Optional<CruiseShip> findByMmsi(String mmsi);
    
    Optional<CruiseShip> findByImo(String imo);
    
    List<CruiseShip> findByCruiseLine(String cruiseLine);
    
    List<CruiseShip> findByAisEnabledTrue();
    
    List<CruiseShip> findByLastAisUpdateBefore(LocalDateTime threshold);
    
    @Query("SELECT s FROM CruiseShip s WHERE s.currentLatitude BETWEEN :minLat AND :maxLat " +
           "AND s.currentLongitude BETWEEN :minLng AND :maxLng " +
           "AND s.aisEnabled = true")
    List<CruiseShip> findShipsInArea(
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLng") Double minLng,
            @Param("maxLng") Double maxLng);
    
    @Query("SELECT s FROM CruiseShip s WHERE s.trackingStatus = :status")
    List<CruiseShip> findByTrackingStatus(@Param("status") CruiseShip.TrackingStatus status);
}
