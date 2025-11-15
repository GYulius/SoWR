package com.cruise.recommender.repository;

import com.cruise.recommender.entity.Publisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Publisher entity operations
 */
@Repository
public interface PublisherRepository extends JpaRepository<Publisher, Long> {
    
    Optional<Publisher> findByUserId(Long userId);
    
    Optional<Publisher> findByUserEmail(String email);
    
    @Query("SELECT p FROM Publisher p WHERE " +
           "(:portId IS NULL OR p.port.id = :portId) AND " +
           "(:businessType IS NULL OR p.businessType = :businessType) AND " +
           "(:verifiedOnly = false OR p.verificationStatus = 'VERIFIED') AND " +
           "p.isActive = true")
    Page<Publisher> findPublishersWithFilters(
            @Param("portId") Long portId,
            @Param("businessType") String businessType,
            @Param("verifiedOnly") Boolean verifiedOnly,
            Pageable pageable);
    
    @Query("SELECT COUNT(p) FROM Publisher p WHERE p.port.id = :portId AND p.isActive = true")
    Long countByPortId(@Param("portId") Long portId);
    
    boolean existsByUserId(Long userId);
}
