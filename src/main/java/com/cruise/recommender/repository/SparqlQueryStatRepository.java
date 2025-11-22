package com.cruise.recommender.repository;

import com.cruise.recommender.entity.SparqlQueryStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Repository for SPARQL query statistics
 */
@Repository
public interface SparqlQueryStatRepository extends JpaRepository<SparqlQueryStat, Long> {
    
    @Query("SELECT s.queryType, COUNT(s) as count, AVG(s.durationMs) as avgDuration, " +
           "SUM(CASE WHEN s.success = true THEN 1 ELSE 0 END) as successCount " +
           "FROM SparqlQueryStat s " +
           "WHERE s.timestamp >= :since " +
           "GROUP BY s.queryType")
    List<Object[]> getStatsByTypeSince(@Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(s) FROM SparqlQueryStat s WHERE s.timestamp >= :since")
    Long countSince(@Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(s) FROM SparqlQueryStat s WHERE s.success = true AND s.timestamp >= :since")
    Long countSuccessfulSince(@Param("since") LocalDateTime since);
    
    @Query("SELECT AVG(s.durationMs) FROM SparqlQueryStat s WHERE s.timestamp >= :since")
    Double getAverageDurationSince(@Param("since") LocalDateTime since);
    
    @Query("SELECT s.errorType, COUNT(s) FROM SparqlQueryStat s " +
           "WHERE s.success = false AND s.timestamp >= :since " +
           "GROUP BY s.errorType")
    List<Object[]> getErrorStatsSince(@Param("since") LocalDateTime since);
}

