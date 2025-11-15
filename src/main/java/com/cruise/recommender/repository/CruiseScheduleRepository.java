package com.cruise.recommender.repository;

import com.cruise.recommender.entity.CruiseSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Cruise Schedule operations
 */
@Repository
public interface CruiseScheduleRepository extends JpaRepository<CruiseSchedule, Long> {
    
    List<CruiseSchedule> findByPortId(Long portId);
    
    List<CruiseSchedule> findByShipId(Long shipId);
}
