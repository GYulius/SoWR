package com.cruise.recommender.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

/**
 * Elasticsearch Configuration for AIS data storage and search
 * Enables fast search and analytics on ship tracking data
 * 
 * This configuration is optional - set elasticsearch.enabled=true to enable
 */
@Configuration
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
@EnableElasticsearchRepositories(
    basePackages = "com.cruise.recommender.repository.elasticsearch",
    considerNestedRepositories = false
)
public class ElasticsearchConfig extends ElasticsearchConfiguration {
    
    @Value("${elasticsearch.host:localhost}")
    private String host;
    
    @Value("${elasticsearch.port:9200}")
    private int port;
    
    @Value("${elasticsearch.username:}")
    private String username;
    
    @Value("${elasticsearch.password:}")
    private String password;
    
    @Value("${elasticsearch.connection-timeout:5000}")
    private int connectionTimeout;
    
    @Value("${elasticsearch.socket-timeout:60000}")
    private int socketTimeout;
    
    @Override
    public ClientConfiguration clientConfiguration() {
        ClientConfiguration.MaybeSecureClientConfigurationBuilder builder = 
                ClientConfiguration.builder()
                        .connectedTo(host + ":" + port);
        
        if (!username.isEmpty() && !password.isEmpty()) {
            builder.withBasicAuth(username, password);
        }
        
        return builder
                .withConnectTimeout(java.time.Duration.ofMillis(connectionTimeout))
                .withSocketTimeout(java.time.Duration.ofMillis(socketTimeout))
                .build();
    }
}
