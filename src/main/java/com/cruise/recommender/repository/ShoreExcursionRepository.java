package com.cruise.recommender.repository;

import com.cruise.recommender.entity.ShoreExcursion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Shore Excursion operations
 */
@Repository
public interface ShoreExcursionRepository extends JpaRepository<ShoreExcursion, Long> {
    
    List<ShoreExcursion> findByPortId(Long portId);
    
    List<ShoreExcursion> findByPortIdAndMustSeeHighlightTrue(Long portId);
    
    List<ShoreExcursion> findByPortIdAndExcursionType(Long portId, ShoreExcursion.ExcursionType type);
    
    List<ShoreExcursion> findByPortIdOrderByPopularityScoreDesc(Long portId);
}
