package com.cruise.recommender.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity to track SPARQL query statistics
 */
@Entity
@Table(name = "sparql_query_stats")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SparqlQueryStat {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String queryType;
    
    @Column(nullable = false)
    private Boolean success;
    
    @Column(nullable = false)
    private Long durationMs;
    
    @Column
    private String errorType;
    
    @Column
    private Integer resultCount;
    
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    @Column(length = 1000)
    private String queryHash; // Hash of the query for deduplication
}

