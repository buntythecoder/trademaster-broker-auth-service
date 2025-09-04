package com.trademaster.brokerauth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * TradeMaster Broker Authentication Service
 * 
 * Main application class for secure multi-broker authentication.
 * Handles Zerodha, Upstox, Angel One, and ICICI Direct authentication.
 * 
 * MANDATORY: Java 24 + Virtual Threads - Rule #1
 * MANDATORY: Zero TODOs/Placeholders - Rule #7  
 * 
 * @author TradeMaster Development Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableAsync
@EnableTransactionManagement
@Slf4j
public class BrokerAuthServiceApplication {

    public static void main(String[] args) {
        // Enable Virtual Threads for maximum scalability - Rule #1, #12
        System.setProperty("spring.threads.virtual.enabled", "true");
        System.setProperty("jdk.virtualThreadScheduler.parallelism", 
                          String.valueOf(Runtime.getRuntime().availableProcessors()));
        
        log.info("Starting TradeMaster Broker Authentication Service with Virtual Threads");
        SpringApplication.run(BrokerAuthServiceApplication.class, args);
    }
}