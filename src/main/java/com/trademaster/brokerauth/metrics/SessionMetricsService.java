package com.trademaster.brokerauth.metrics;

import com.trademaster.brokerauth.entity.BrokerSession;
import com.trademaster.brokerauth.enums.BrokerType;
import com.trademaster.brokerauth.enums.SessionStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Session Metrics Service
 *
 * Prometheus metrics for session lifecycle and health monitoring.
 *
 * MANDATORY: Rule #12 - Virtual Threads compatibility
 * MANDATORY: Rule #9 - Immutable patterns where applicable
 * MANDATORY: Task 2.2 - Session Management Enhancement
 *
 * Metrics Exposed:
 * - session_refresh_total: Counter for refresh attempts by broker and status
 * - session_active_count: Gauge for active sessions by broker
 * - session_lifetime_seconds: Timer for session duration distribution
 * - session_expired_total: Counter for expired sessions by broker
 * - session_created_total: Counter for session creation by broker
 *
 * @author TradeMaster Development Team
 * @version 1.0.0
 */
@Service
@Slf4j
public class SessionMetricsService {

    private final MeterRegistry registry;

    // Active session counts per broker type
    private final Map<BrokerType, AtomicInteger> activeSessionCounts;

    // Metric counters
    private final Map<String, Counter> refreshCounters;
    private final Map<String, Counter> expiredCounters;
    private final Map<String, Counter> createdCounters;
    private final Map<String, Timer> lifetimeTimers;

    private static final String METRIC_PREFIX = "broker_auth.session";

    /**
     * Constructor with Micrometer registry injection
     *
     * MANDATORY: Rule #9 - Immutable collections initialization
     */
    public SessionMetricsService(MeterRegistry registry) {
        this.registry = registry;
        this.activeSessionCounts = new ConcurrentHashMap<>();
        this.refreshCounters = new ConcurrentHashMap<>();
        this.expiredCounters = new ConcurrentHashMap<>();
        this.createdCounters = new ConcurrentHashMap<>();
        this.lifetimeTimers = new ConcurrentHashMap<>();

        initializeMetrics();
    }

    /**
     * Initialize metrics for all broker types
     *
     * MANDATORY: Rule #13 - Stream API for collection processing
     */
    private void initializeMetrics() {
        Map.of(
            BrokerType.ZERODHA, new AtomicInteger(0),
            BrokerType.UPSTOX, new AtomicInteger(0),
            BrokerType.ANGEL_ONE, new AtomicInteger(0),
            BrokerType.ICICI_DIRECT, new AtomicInteger(0)
        ).forEach((broker, count) -> {
            activeSessionCounts.put(broker, count);
            registerGaugeForBroker(broker, count);
        });

        log.info("Session metrics initialized for all broker types");
    }

    /**
     * Register gauge for active sessions per broker
     */
    private void registerGaugeForBroker(BrokerType broker, AtomicInteger count) {
        Gauge.builder(METRIC_PREFIX + ".active.count", count, AtomicInteger::get)
            .tag("broker", broker.name().toLowerCase())
            .description("Number of active sessions for broker")
            .register(registry);
    }

    /**
     * Record successful session refresh
     *
     * MANDATORY: Rule #3 - Functional Programming
     */
    public void recordRefreshSuccess(BrokerType broker) {
        getOrCreateRefreshCounter(broker, "success").increment();
        log.debug("Recorded successful refresh for broker: {}", broker);
    }

    /**
     * Record failed session refresh
     *
     * MANDATORY: Rule #3 - Functional Programming
     */
    public void recordRefreshFailure(BrokerType broker, String errorType) {
        getOrCreateRefreshCounter(broker, "failure").increment();

        Counter.builder(METRIC_PREFIX + ".refresh.failure.bytype")
            .tag("broker", broker.name().toLowerCase())
            .tag("error_type", errorType)
            .description("Session refresh failures by error type")
            .register(registry)
            .increment();

        log.debug("Recorded refresh failure for broker: {} error: {}", broker, errorType);
    }

    /**
     * Record session creation
     */
    public void recordSessionCreated(BrokerType broker) {
        getOrCreateCreatedCounter(broker).increment();
        incrementActiveCount(broker);
        log.debug("Recorded session creation for broker: {}", broker);
    }

    /**
     * Record session expiration
     */
    public void recordSessionExpired(BrokerType broker) {
        getOrCreateExpiredCounter(broker).increment();
        decrementActiveCount(broker);
        log.debug("Recorded session expiration for broker: {}", broker);
    }

    /**
     * Record session revocation
     */
    public void recordSessionRevoked(BrokerType broker) {
        Counter.builder(METRIC_PREFIX + ".revoked.total")
            .tag("broker", broker.name().toLowerCase())
            .description("Total revoked sessions by broker")
            .register(registry)
            .increment();

        decrementActiveCount(broker);
        log.debug("Recorded session revocation for broker: {}", broker);
    }

    /**
     * Record session lifetime when session ends
     */
    public void recordSessionLifetime(BrokerSession session) {
        if (session.getCreatedAt() == null) {
            return;
        }

        long lifetimeSeconds = Duration.between(
            session.getCreatedAt(),
            LocalDateTime.now()
        ).getSeconds();

        getOrCreateLifetimeTimer(session.getBrokerType())
            .record(Duration.ofSeconds(lifetimeSeconds));

        log.debug("Recorded session lifetime: {} seconds for broker: {}",
            lifetimeSeconds, session.getBrokerType());
    }

    /**
     * Update active session count for broker
     */
    public void updateActiveSessionCount(BrokerType broker, int count) {
        activeSessionCounts.computeIfAbsent(broker, k -> new AtomicInteger(0))
            .set(count);
        log.debug("Updated active session count for broker: {} to {}", broker, count);
    }

    /**
     * Increment active session count
     */
    private void incrementActiveCount(BrokerType broker) {
        activeSessionCounts.computeIfAbsent(broker, k -> new AtomicInteger(0))
            .incrementAndGet();
    }

    /**
     * Decrement active session count
     */
    private void decrementActiveCount(BrokerType broker) {
        activeSessionCounts.computeIfAbsent(broker, k -> new AtomicInteger(0))
            .updateAndGet(current -> Math.max(0, current - 1));
    }

    /**
     * Get or create refresh counter for broker and status
     *
     * MANDATORY: Rule #9 - Functional patterns with computeIfAbsent
     */
    private Counter getOrCreateRefreshCounter(BrokerType broker, String status) {
        String key = broker.name() + "_" + status;
        return refreshCounters.computeIfAbsent(key, k ->
            Counter.builder(METRIC_PREFIX + ".refresh.total")
                .tag("broker", broker.name().toLowerCase())
                .tag("status", status)
                .description("Total session refresh attempts by broker and status")
                .register(registry)
        );
    }

    /**
     * Get or create expired counter for broker
     */
    private Counter getOrCreateExpiredCounter(BrokerType broker) {
        return expiredCounters.computeIfAbsent(broker.name(), k ->
            Counter.builder(METRIC_PREFIX + ".expired.total")
                .tag("broker", broker.name().toLowerCase())
                .description("Total expired sessions by broker")
                .register(registry)
        );
    }

    /**
     * Get or create created counter for broker
     */
    private Counter getOrCreateCreatedCounter(BrokerType broker) {
        return createdCounters.computeIfAbsent(broker.name(), k ->
            Counter.builder(METRIC_PREFIX + ".created.total")
                .tag("broker", broker.name().toLowerCase())
                .description("Total created sessions by broker")
                .register(registry)
        );
    }

    /**
     * Get or create lifetime timer for broker
     */
    private Timer getOrCreateLifetimeTimer(BrokerType broker) {
        return lifetimeTimers.computeIfAbsent(broker.name(), k ->
            Timer.builder(METRIC_PREFIX + ".lifetime.seconds")
                .tag("broker", broker.name().toLowerCase())
                .description("Session lifetime distribution by broker")
                .register(registry)
        );
    }

    /**
     * Get current active session count for broker
     */
    public int getActiveSessionCount(BrokerType broker) {
        return activeSessionCounts.getOrDefault(broker, new AtomicInteger(0)).get();
    }
}
