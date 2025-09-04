package com.trademaster.brokerauth.service.broker;

import com.trademaster.brokerauth.enums.BrokerType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Broker Service Factory - Factory pattern for broker services
 * 
 * MANDATORY: Factory Pattern - Rule #4
 * MANDATORY: Single Responsibility - Rule #2
 * MANDATORY: Functional Programming - Rule #3
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BrokerServiceFactory {
    
    private final List<BrokerApiService> brokerServices;
    
    /**
     * Get broker service for given broker type
     * 
     * MANDATORY: Optional pattern - Rule #11
     * MANDATORY: Stream API - Rule #13
     */
    public Optional<BrokerApiService> getBrokerService(BrokerType brokerType) {
        log.debug("Finding broker service for: {}", brokerType);
        
        return brokerServices.stream()
            .filter(service -> service.supports(brokerType))
            .findFirst()
            .map(service -> {
                log.debug("Found broker service: {} for broker: {}", 
                    service.getBrokerName(), brokerType);
                return service;
            });
    }
    
    /**
     * Check if broker type is supported
     */
    public boolean isSupported(BrokerType brokerType) {
        return getBrokerService(brokerType).isPresent();
    }
    
    /**
     * Get all supported broker types
     * 
     * MANDATORY: Stream API - Rule #13
     */
    public List<BrokerType> getSupportedBrokerTypes() {
        return brokerServices.stream()
            .flatMap(service -> java.util.stream.Stream.of(BrokerType.values())
                .filter(service::supports))
            .distinct()
            .toList();
    }
}