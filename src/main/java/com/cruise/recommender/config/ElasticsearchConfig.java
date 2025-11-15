package com.cruise.recommender.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

/**
 * Elasticsearch Configuration for AIS data storage and search
 * Enables fast search and analytics on ship tracking data
 */
@Configuration
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
    
    @Override
    public ClientConfiguration clientConfiguration() {
        ClientConfiguration.MaybeSecureClientConfigurationBuilder builder = 
                ClientConfiguration.builder()
                        .connectedTo(host + ":" + port);
        
        if (!username.isEmpty() && !password.isEmpty()) {
            builder.withBasicAuth(username, password);
        }
        
        return builder.build();
    }
}
