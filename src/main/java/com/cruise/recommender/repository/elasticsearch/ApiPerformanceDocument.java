package com.cruise.recommender.repository.elasticsearch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

/**
 * Elasticsearch document for API performance metrics
 * Tracks API response times, error rates, and request details
 */
@Document(indexName = "api-performance")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiPerformanceDocument {
    
    @Id
    private String id; // Auto-generated or composite key
    
    @Field(type = FieldType.Keyword)
    private String endpoint; // e.g., "/api/v1/recommendations"
    
    @Field(type = FieldType.Keyword)
    private String method; // GET, POST, PUT, DELETE, etc.
    
    @Field(type = FieldType.Keyword)
    private String httpStatus; // 200, 404, 500, etc.
    
    @Field(type = FieldType.Long)
    private Long responseTimeMs; // Response time in milliseconds
    
    @Field(type = FieldType.Boolean)
    private Boolean success; // true if status < 400
    
    @Field(type = FieldType.Keyword)
    private String errorType; // Exception class name if error occurred
    
    @Field(type = FieldType.Text)
    private String errorMessage; // Error message (truncated)
    
    @Field(type = FieldType.Keyword)
    private String userEmail; // User email if authenticated
    
    @Field(type = FieldType.Keyword)
    private String userRole; // User role if authenticated
    
    @Field(type = FieldType.Keyword)
    private String clientIp; // Client IP address
    
    @Field(type = FieldType.Keyword)
    private String userAgent; // User agent string
    
    @Field(type = FieldType.Long)
    private Long requestSizeBytes; // Request body size
    
    @Field(type = FieldType.Long)
    private Long responseSizeBytes; // Response body size
    
    @Field(type = FieldType.Date, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp; // Request timestamp
    
    @Field(type = FieldType.Keyword)
    private String controller; // Controller class name
    
    @Field(type = FieldType.Keyword)
    private String action; // Method name
    
    @Field(type = FieldType.Integer)
    private Integer statusCode; // HTTP status code as integer
}

