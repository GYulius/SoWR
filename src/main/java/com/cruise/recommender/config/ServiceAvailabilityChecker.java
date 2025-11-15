package com.cruise.recommender.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.Socket;
import java.util.concurrent.TimeUnit;

/**
 * Checks availability of required services (Redis, RabbitMQ) before application starts
 */
@Component
@Order(1) // Run before other CommandLineRunner beans
@Slf4j
public class ServiceAvailabilityChecker implements CommandLineRunner {

    private static final int MAX_RETRIES = 30;
    private static final int RETRY_DELAY_SECONDS = 2;
    private static final int CONNECTION_TIMEOUT_MS = 1000;

    @Override
    public void run(String... args) {
        log.info("Checking availability of required services...");
        
        boolean redisAvailable = checkService("Redis", "localhost", 6379);
        boolean rabbitmqAvailable = checkService("RabbitMQ", "localhost", 5672);
        
        if (!redisAvailable) {
            log.error("❌ Redis is not available on localhost:6379");
            log.error("   Please start Redis: docker-compose up -d redis");
            throw new RuntimeException("Redis service is not available. Application cannot start.");
        }
        
        if (!rabbitmqAvailable) {
            log.error("❌ RabbitMQ is not available on localhost:5672");
            log.error("   Please start RabbitMQ: docker-compose up -d rabbitmq");
            throw new RuntimeException("RabbitMQ service is not available. Application cannot start.");
        }
        
        log.info("✅ All required services are available");
    }

    private boolean checkService(String serviceName, String host, int port) {
        log.info("Checking {} availability on {}:{}...", serviceName, host, port);
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try (Socket socket = new Socket()) {
                socket.connect(new java.net.InetSocketAddress(host, port), CONNECTION_TIMEOUT_MS);
                log.info("✅ {} is available on {}:{}", serviceName, host, port);
                return true;
            } catch (Exception e) {
                if (attempt < MAX_RETRIES) {
                    log.debug("{} not available yet (attempt {}/{}), retrying in {} seconds...", 
                            serviceName, attempt, MAX_RETRIES, RETRY_DELAY_SECONDS);
                    try {
                        TimeUnit.SECONDS.sleep(RETRY_DELAY_SECONDS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                } else {
                    log.warn("⚠️ {} is not available after {} attempts", serviceName, MAX_RETRIES);
                }
            }
        }
        
        return false;
    }
}

