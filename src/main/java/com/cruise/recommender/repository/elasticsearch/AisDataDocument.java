package com.cruise.recommender.repository.elasticsearch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.GeoPointField;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;

import java.time.LocalDateTime;

/**
 * Elasticsearch document for AIS (Automatic Identification System) data
 * Used for fast search, geospatial queries, and analytics on ship tracking data
 */
@Document(indexName = "ais_data")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AisDataDocument {
    
    @Id
    private String id; // Elasticsearch document ID (can be same as MySQL ID or composite key)
    
    @Field(type = FieldType.Keyword)
    private String mmsi; // Maritime Mobile Service Identity
    
    @Field(type = FieldType.Text, analyzer = "standard")
    private String shipName;
    
    @Field(type = FieldType.Double)
    private Double latitude;
    
    @Field(type = FieldType.Double)
    private Double longitude;
    
    @GeoPointField
    private GeoPoint location; // Geospatial point for location queries
    
    @Field(type = FieldType.Date, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
    
    @Field(type = FieldType.Double)
    private Double speed; // Speed over ground in knots
    
    @Field(type = FieldType.Double)
    private Double course; // Course over ground in degrees
    
    @Field(type = FieldType.Integer)
    private Integer heading; // Heading in degrees
    
    @Field(type = FieldType.Keyword)
    private String shipType;
    
    @Field(type = FieldType.Integer)
    private Integer length; // Length in meters
    
    @Field(type = FieldType.Integer)
    private Integer width; // Width in meters
    
    @Field(type = FieldType.Text)
    private String destination;
    
    @Field(type = FieldType.Keyword)
    private String eta; // Estimated Time of Arrival
    
    @Field(type = FieldType.Keyword)
    private String imo; // International Maritime Organization number
    
    @Field(type = FieldType.Keyword)
    private String callSign;
    
    @Field(type = FieldType.Double)
    private Double stationRange; // Distance to nearest AIS station in nautical miles
    
    @Field(type = FieldType.Keyword)
    private String signalQuality; // GOOD, FAIR, POOR, NONE
    
    @Field(type = FieldType.Keyword)
    private String dataSource; // SATELLITE, TERRESTRIAL, BOTH
    
    @Field(type = FieldType.Object, enabled = false)
    private String metadata; // Additional AIS data (stored but not indexed)
    
    @Field(type = FieldType.Long)
    private Long cruiseShipId; // Reference to cruise ship (denormalized)
    
    @Field(type = FieldType.Date, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    
    @Field(type = FieldType.Date, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
    
    /**
     * Helper method to create GeoPoint from latitude/longitude
     */
    public void setLocationFromCoordinates() {
        if (latitude != null && longitude != null) {
            this.location = new GeoPoint(latitude, longitude);
        }
    }
}

