package com.cruise.recommender.repository;

import com.cruise.recommender.entity.MealVenue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Meal Venue operations
 */
@Repository
public interface MealVenueRepository extends JpaRepository<MealVenue, Long> {
    
    List<MealVenue> findByPortId(Long portId);
    
    @Query("SELECT m FROM MealVenue m WHERE m.port.id = :portId AND m.mealTypesServed LIKE %:mealType%")
    List<MealVenue> findByPortIdAndMealTypesServedContaining(
            @Param("portId") Long portId, 
            @Param("mealType") String mealType);
    
    List<MealVenue> findByPortIdAndLocalRecommendationScoreGreaterThan(
            Long portId, Double minScore);
    
    List<MealVenue> findByPortIdAndIsActiveDuringPortCallsTrue(Long portId);
}
