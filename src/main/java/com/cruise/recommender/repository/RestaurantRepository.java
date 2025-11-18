package com.cruise.recommender.repository;

import com.cruise.recommender.entity.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Restaurant operations
 */
@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {
    
    List<Restaurant> findByPortId(Long portId);
    
    List<Restaurant> findByPortIdAndCuisineType(Long portId, String cuisineType);
}

