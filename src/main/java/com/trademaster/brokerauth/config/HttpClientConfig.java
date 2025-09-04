package com.trademaster.brokerauth.config;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * HTTP Client Configuration for Broker APIs
 * 
 * MANDATORY: Virtual Thread Compatible - Rule #12
 * MANDATORY: Configuration Externalization - Rule #16
 * MANDATORY: Zero Placeholders - Rule #7
 */
@Configuration
public class HttpClientConfig {
    
    private static final int CONNECTION_TIMEOUT_SECONDS = 30;
    private static final int READ_TIMEOUT_SECONDS = 60;
    private static final int WRITE_TIMEOUT_SECONDS = 30;
    private static final int MAX_IDLE_CONNECTIONS = 5;
    private static final int KEEP_ALIVE_DURATION_MINUTES = 5;
    
    /**
     * OkHttp Client optimized for Virtual Threads
     * 
     * MANDATORY: Virtual Thread Compatible - Rule #12
     */
    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
            .connectTimeout(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectionPool(new ConnectionPool(
                MAX_IDLE_CONNECTIONS, 
                KEEP_ALIVE_DURATION_MINUTES, 
                TimeUnit.MINUTES))
            .retryOnConnectionFailure(true)
            .followRedirects(false)
            .followSslRedirects(false)
            .build();
    }
}