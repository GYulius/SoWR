package com.cruise.recommender.repository;

import com.cruise.recommender.entity.Port;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Port entity operations
 */
@Repository
public interface PortRepository extends JpaRepository<Port, Long> {
    
    Optional<Port> findByPortCode(String portCode);
    
    List<Port> findByCountry(String country);
    
    List<Port> findByCountryAndRegion(String country, String region);
    
    Page<Port> findByCountry(String country, Pageable pageable);
    
    @Query("SELECT p FROM Port p WHERE p.berthsCapacity >= :minCapacity ORDER BY p.berthsCapacity ASC")
    List<Port> findByMinCapacity(@Param("minCapacity") Integer minCapacity);
    
    @Query("SELECT p FROM Port p WHERE p.name LIKE %:name% OR p.city LIKE %:name%")
    List<Port> findByNameContaining(@Param("name") String name);
    
    @Query("SELECT DISTINCT p.country FROM Port p ORDER BY p.country")
    List<String> findAllCountries();
    
    @Query("SELECT DISTINCT p.region FROM Port p WHERE p.country = :country ORDER BY p.region")
    List<String> findRegionsByCountry(@Param("country") String country);
    
    @Query("SELECT p FROM Port p WHERE p.latitude BETWEEN :minLat AND :maxLat AND p.longitude BETWEEN :minLng AND :maxLng")
    List<Port> findByLocationRange(@Param("minLat") Double minLat, 
                                   @Param("maxLat") Double maxLat,
                                   @Param("minLng") Double minLng, 
                                   @Param("maxLng") Double maxLng);
}
