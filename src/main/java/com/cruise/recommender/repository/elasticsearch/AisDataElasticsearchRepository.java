package com.cruise.recommender.repository.elasticsearch;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Elasticsearch repository for AIS data
 * Provides fast search and geospatial queries on ship tracking data
 */
@Repository
public interface AisDataElasticsearchRepository extends ElasticsearchRepository<AisDataDocument, String> {
    
    /**
     * Find AIS data by MMSI, ordered by timestamp descending
     */
    List<AisDataDocument> findByMmsiOrderByTimestampDesc(String mmsi);
    
    /**
     * Find AIS data within a time range
     */
    List<AisDataDocument> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
    
    /**
     * Find AIS data within a time range (paginated)
     */
    Page<AisDataDocument> findByTimestampBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
    
    /**
     * Find AIS data by ship type
     */
    List<AisDataDocument> findByShipType(String shipType);
    
    /**
     * Find AIS data by signal quality
     */
    List<AisDataDocument> findBySignalQuality(String signalQuality);
    
    /**
     * Find AIS data by data source
     */
    List<AisDataDocument> findByDataSource(String dataSource);
    
    /**
     * Find AIS data by cruise ship ID
     */
    List<AisDataDocument> findByCruiseShipIdOrderByTimestampDesc(Long cruiseShipId);
    
    /**
     * Find AIS data by ship name (full-text search)
     */
    List<AisDataDocument> findByShipNameContaining(String shipName);
    
    /**
     * Find AIS data within geographic bounding box
     * Uses range queries on latitude/longitude fields
     */
    List<AisDataDocument> findByLatitudeBetweenAndLongitudeBetweenAndTimestampGreaterThanEqual(
            Double minLat,
            Double maxLat,
            Double minLng,
            Double maxLng,
            LocalDateTime since);
    
    /**
     * Find ships by destination (full-text search)
     */
    List<AisDataDocument> findByDestinationContaining(String destination);
    
    /**
     * Find recent AIS data (last N minutes)
     */
    List<AisDataDocument> findByTimestampGreaterThanEqual(LocalDateTime since);
    
    /**
     * Find AIS data by multiple MMSI values
     */
    List<AisDataDocument> findByMmsiIn(List<String> mmsiList);
    
    /**
     * Find AIS data with speed above threshold
     */
    List<AisDataDocument> findBySpeedGreaterThan(Double minSpeed);
    
    /**
     * Find AIS data by ship type and signal quality
     */
    List<AisDataDocument> findByShipTypeAndSignalQuality(String shipType, String signalQuality);
}

