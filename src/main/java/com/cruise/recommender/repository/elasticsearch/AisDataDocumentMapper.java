package com.cruise.recommender.repository.elasticsearch;

import com.cruise.recommender.entity.AisData;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.stereotype.Component;

/**
 * Mapper to convert between JPA AisData entity and Elasticsearch AisDataDocument
 */
@Component
public class AisDataDocumentMapper {
    
    /**
     * Convert JPA entity to Elasticsearch document
     */
    public AisDataDocument toDocument(AisData aisData) {
        if (aisData == null) {
            return null;
        }
        
        AisDataDocument document = AisDataDocument.builder()
                .id(aisData.getId() != null ? aisData.getId().toString() : null)
                .mmsi(aisData.getMmsi())
                .shipName(aisData.getShipName())
                .latitude(aisData.getLatitude())
                .longitude(aisData.getLongitude())
                .timestamp(aisData.getTimestamp())
                .speed(aisData.getSpeed())
                .course(aisData.getCourse())
                .heading(aisData.getHeading())
                .shipType(aisData.getShipType())
                .length(aisData.getLength())
                .width(aisData.getWidth())
                .destination(aisData.getDestination())
                .eta(aisData.getEta())
                .imo(aisData.getImo())
                .callSign(aisData.getCallSign())
                .stationRange(aisData.getStationRange())
                .signalQuality(aisData.getSignalQuality())
                .dataSource(aisData.getDataSource())
                .metadata(aisData.getMetadata())
                .cruiseShipId(aisData.getCruiseShip() != null ? aisData.getCruiseShip().getId() : null)
                .createdAt(aisData.getCreatedAt())
                .updatedAt(aisData.getUpdatedAt())
                .build();
        
        // Set geo point for geospatial queries
        if (aisData.getLatitude() != null && aisData.getLongitude() != null) {
            document.setLocation(new GeoPoint(aisData.getLatitude(), aisData.getLongitude()));
        }
        
        return document;
    }
    
    /**
     * Convert Elasticsearch document to JPA entity (for read operations)
     * Note: This is a simplified conversion - relationships are not restored
     */
    public AisData toEntity(AisDataDocument document) {
        if (document == null) {
            return null;
        }
        
        return AisData.builder()
                .id(document.getId() != null ? Long.parseLong(document.getId()) : null)
                .mmsi(document.getMmsi())
                .shipName(document.getShipName())
                .latitude(document.getLatitude())
                .longitude(document.getLongitude())
                .timestamp(document.getTimestamp())
                .speed(document.getSpeed())
                .course(document.getCourse())
                .heading(document.getHeading())
                .shipType(document.getShipType())
                .length(document.getLength())
                .width(document.getWidth())
                .destination(document.getDestination())
                .eta(document.getEta())
                .imo(document.getImo())
                .callSign(document.getCallSign())
                .stationRange(document.getStationRange())
                .signalQuality(document.getSignalQuality())
                .dataSource(document.getDataSource())
                .metadata(document.getMetadata())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }
}

