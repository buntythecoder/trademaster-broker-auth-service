package com.trademaster.brokerauth.metrics;

import com.trademaster.brokerauth.enums.BrokerType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Authentication Metrics Service
 *
 * Prometheus metrics for authentication operations and broker health.
 *
 * MANDATORY: Rule #12 - Virtual Threads compatibility
 * MANDATORY: Rule #9 - Immutable patterns where applicable
 * MANDATORY: Task 2.3 - Monitoring & Observability
 *
 * Metrics Exposed:
 * - auth_login_total: Counter for login attempts by broker and status
 * - auth_token_refresh_total: Counter for token refresh by broker
 * - auth_duration_seconds: Timer for authentication duration
 * - auth_circuit_breaker_state: Gauge for circuit breaker state (0=closed, 1=open, 0.5=half-open)
 * - auth_rate_limit_hits_total: Counter for rate limit hits by broker
 * - auth_active_users: Gauge for currently authenticated users per broker
 *
 * @author TradeMaster Development Team
 * @version 1.0.0
 */
@Service
@Slf4j
public class AuthenticationMetricsService {

    private final MeterRegistry registry;

    // Active user counts per broker
    private final Map<BrokerType, AtomicLong> activeUserCounts;

    // Circuit breaker states per broker (0=closed, 1=open, 0.5=half-open)
    private final Map<BrokerType, AtomicLong> circuitBreakerStates;

    // Metric counters
    private final Map<String, Counter> loginCounters;
    private final Map<String, Counter> tokenRefreshCounters;
    private final Map<String, Counter> rateLimitCounters;
    private final Map<String, Timer> authTimers;

    private static final String METRIC_PREFIX = "broker_auth";
    private static final long CB_CLOSED = 0L;
    private static final long CB_OPEN = 100L;
    private static final long CB_HALF_OPEN = 50L;

    /**
     * Constructor with Micrometer registry injection
     */
    public AuthenticationMetricsService(MeterRegistry registry) {
        this.registry = registry;
        this.activeUserCounts = new ConcurrentHashMap<>();
        this.circuitBreakerStates = new ConcurrentHashMap<>();
        this.loginCounters = new ConcurrentHashMap<>();
        this.tokenRefreshCounters = new ConcurrentHashMap<>();
        this.rateLimitCounters = new ConcurrentHashMap<>();
        this.authTimers = new ConcurrentHashMap<>();

        initializeMetrics();
    }

    /**
     * Initialize metrics for all broker types
     */
    private void initializeMetrics() {
        Map.of(
            BrokerType.ZERODHA, CB_CLOSED,
            BrokerType.UPSTOX, CB_CLOSED,
            BrokerType.ANGEL_ONE, CB_CLOSED,
            BrokerType.ICICI_DIRECT, CB_CLOSED
        ).forEach((broker, state) -> {
            activeUserCounts.put(broker, new AtomicLong(0));
            circuitBreakerStates.put(broker, new AtomicLong(state));

            registerActiveUsersGauge(broker);
            registerCircuitBreakerGauge(broker);
        });

        log.info("Authentication metrics initialized for all broker types");
    }

    /**
     * Register gauge for active users per broker
     */
    private void registerActiveUsersGauge(BrokerType broker) {
        Gauge.builder(METRIC_PREFIX + ".active.users",
                activeUserCounts.get(broker), AtomicLong::get)
            .tag("broker", broker.name().toLowerCase())
            .description("Number of currently authenticated users per broker")
            .register(registry);
    }

    /**
     * Register gauge for circuit breaker state
     */
    private void registerCircuitBreakerGauge(BrokerType broker) {
        Gauge.builder(METRIC_PREFIX + ".circuit.breaker.state",
                circuitBreakerStates.get(broker), value -> value.get() / 100.0)
            .tag("broker", broker.name().toLowerCase())
            .description("Circuit breaker state (0=closed, 1=open, 0.5=half-open)")
            .register(registry);
    }

    /**
     * Record successful login
     */
    public void recordLoginSuccess(BrokerType broker, long durationMs) {
        getOrCreateLoginCounter(broker, "success").increment();
        getOrCreateAuthTimer(broker, "login").record(
            java.time.Duration.ofMillis(durationMs)
        );
        incrementActiveUsers(broker);

        log.debug("Recorded successful login for broker: {} duration: {}ms", broker, durationMs);
    }

    /**
     * Record failed login
     */
    public void recordLoginFailure(BrokerType broker, String errorType, long durationMs) {
        getOrCreateLoginCounter(broker, "failure").increment();

        Counter.builder(METRIC_PREFIX + ".login.failure.bytype")
            .tag("broker", broker.name().toLowerCase())
            .tag("error_type", errorType)
            .description("Login failures by error type")
            .register(registry)
            .increment();

        getOrCreateAuthTimer(broker, "login").record(
            java.time.Duration.ofMillis(durationMs)
        );

        log.debug("Recorded failed login for broker: {} error: {} duration: {}ms",
            broker, errorType, durationMs);
    }

    /**
     * Record successful token refresh
     */
    public void recordTokenRefreshSuccess(BrokerType broker, long durationMs) {
        getOrCreateTokenRefreshCounter(broker, "success").increment();
        getOrCreateAuthTimer(broker, "refresh").record(
            java.time.Duration.ofMillis(durationMs)
        );

        log.debug("Recorded successful token refresh for broker: {} duration: {}ms",
            broker, durationMs);
    }

    /**
     * Record failed token refresh
     */
    public void recordTokenRefreshFailure(BrokerType broker, String errorType, long durationMs) {
        getOrCreateTokenRefreshCounter(broker, "failure").increment();

        Counter.builder(METRIC_PREFIX + ".token.refresh.failure.bytype")
            .tag("broker", broker.name().toLowerCase())
            .tag("error_type", errorType)
            .description("Token refresh failures by error type")
            .register(registry)
            .increment();

        getOrCreateAuthTimer(broker, "refresh").record(
            java.time.Duration.ofMillis(durationMs)
        );

        log.debug("Recorded failed token refresh for broker: {} error: {} duration: {}ms",
            broker, errorType, durationMs);
    }

    /**
     * Record logout
     */
    public void recordLogout(BrokerType broker) {
        Counter.builder(METRIC_PREFIX + ".logout.total")
            .tag("broker", broker.name().toLowerCase())
            .description("Total logout operations by broker")
            .register(registry)
            .increment();

        decrementActiveUsers(broker);

        log.debug("Recorded logout for broker: {}", broker);
    }

    /**
     * Record rate limit hit
     */
    public void recordRateLimitHit(BrokerType broker) {
        getOrCreateRateLimitCounter(broker).increment();
        log.debug("Recorded rate limit hit for broker: {}", broker);
    }

    /**
     * Update circuit breaker state
     */
    public void updateCircuitBreakerState(BrokerType broker, CircuitBreakerState state) {
        long stateValue = switch (state) {
            case CLOSED -> CB_CLOSED;
            case OPEN -> CB_OPEN;
            case HALF_OPEN -> CB_HALF_OPEN;
        };

        circuitBreakerStates.get(broker).set(stateValue);

        log.info("Updated circuit breaker state for broker: {} to {}", broker, state);
    }

    /**
     * Increment active users count
     */
    private void incrementActiveUsers(BrokerType broker) {
        activeUserCounts.computeIfAbsent(broker, k -> new AtomicLong(0))
            .incrementAndGet();
    }

    /**
     * Decrement active users count
     */
    private void decrementActiveUsers(BrokerType broker) {
        activeUserCounts.computeIfAbsent(broker, k -> new AtomicLong(0))
            .updateAndGet(current -> Math.max(0, current - 1));
    }

    /**
     * Get or create login counter
     */
    private Counter getOrCreateLoginCounter(BrokerType broker, String status) {
        String key = broker.name() + "_" + status;
        return loginCounters.computeIfAbsent(key, k ->
            Counter.builder(METRIC_PREFIX + ".login.total")
                .tag("broker", broker.name().toLowerCase())
                .tag("status", status)
                .description("Total login attempts by broker and status")
                .register(registry)
        );
    }

    /**
     * Get or create token refresh counter
     */
    private Counter getOrCreateTokenRefreshCounter(BrokerType broker, String status) {
        String key = broker.name() + "_" + status;
        return tokenRefreshCounters.computeIfAbsent(key, k ->
            Counter.builder(METRIC_PREFIX + ".token.refresh.total")
                .tag("broker", broker.name().toLowerCase())
                .tag("status", status)
                .description("Total token refresh attempts by broker and status")
                .register(registry)
        );
    }

    /**
     * Get or create rate limit counter
     */
    private Counter getOrCreateRateLimitCounter(BrokerType broker) {
        return rateLimitCounters.computeIfAbsent(broker.name(), k ->
            Counter.builder(METRIC_PREFIX + ".rate.limit.hits.total")
                .tag("broker", broker.name().toLowerCase())
                .description("Total rate limit hits by broker")
                .register(registry)
        );
    }

    /**
     * Get or create authentication timer
     */
    private Timer getOrCreateAuthTimer(BrokerType broker, String operation) {
        String key = broker.name() + "_" + operation;
        return authTimers.computeIfAbsent(key, k ->
            Timer.builder(METRIC_PREFIX + ".duration.seconds")
                .tag("broker", broker.name().toLowerCase())
                .tag("operation", operation)
                .description("Authentication operation duration by broker and operation type")
                .register(registry)
        );
    }

    /**
     * Circuit breaker state enum
     */
    public enum CircuitBreakerState {
        CLOSED,
        OPEN,
        HALF_OPEN
    }
}
