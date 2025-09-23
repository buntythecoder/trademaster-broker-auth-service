# TradeMaster Broker Auth Service - Production Dockerfile
# Java 24 + Virtual Threads + Spring Boot 3.5.3 + Consul Integration

# Runtime stage - Java 24 Application Runtime
FROM amazoncorretto:24-alpine-jdk as runtime

# Metadata
LABEL maintainer="TradeMaster Platform Team <platform@trademaster.com>" \
      version="1.0.0" \
      description="TradeMaster Broker Authentication Service with Java 24 Virtual Threads and Consul Integration" \
      java.version="24" \
      spring.boot.version="3.5.3" \
      consul.enabled="true"

# Install runtime dependencies
RUN apk update && apk add --no-cache \
    curl \
    jq \
    netcat-openbsd \
    dumb-init \
    && rm -rf /var/cache/apk/*

# Create application user
RUN addgroup -g 1001 -S trademaster && \
    adduser -S trademaster -u 1001 -G trademaster

# Create application directories
RUN mkdir -p /app/logs /app/config /app/tmp && \
    chown -R trademaster:trademaster /app

# Set working directory
WORKDIR /app

# Copy pre-built JAR from host (built with Java 24)
COPY --chown=trademaster:trademaster build/libs/broker-auth-service-1.0.0.jar app.jar

# Copy configuration files  
COPY --chown=trademaster:trademaster src/main/resources/application*.yml config/

# Create entrypoint script with proper signal handling
RUN printf '#!/bin/sh\nset -e\n\n# Function to handle shutdown gracefully\nshutdown() {\n    echo "Received shutdown signal, gracefully shutting down..."\n    if [ -n "$APP_PID" ]; then\n        kill -TERM "$APP_PID" 2>/dev/null || true\n        wait "$APP_PID" 2>/dev/null || true\n    fi\n    exit 0\n}\n\n# Trap signals\ntrap shutdown TERM INT\n\n# Wait for dependencies if specified\nif [ -n "$WAIT_FOR_CONSUL" ]; then\n    echo "Waiting for Consul at ${CONSUL_HOST:-consul}:${CONSUL_PORT:-8500}..."\n    while ! nc -z "${CONSUL_HOST:-consul}" "${CONSUL_PORT:-8500}"; do\n        echo "Consul not ready, waiting..."\n        sleep 2\n    done\n    echo "Consul is ready!"\nfi\n\nif [ -n "$WAIT_FOR_POSTGRES" ]; then\n    echo "Waiting for PostgreSQL at ${DATABASE_HOST:-postgres}..."\n    while ! nc -z "${DATABASE_HOST:-postgres}" 5432; do\n        echo "PostgreSQL not ready, waiting..."\n        sleep 2\n    done\n    echo "PostgreSQL is ready!"\nfi\n\nif [ -n "$WAIT_FOR_REDIS" ]; then\n    echo "Waiting for Redis at ${REDIS_HOST:-redis}..."\n    while ! nc -z "${REDIS_HOST:-redis}" "${REDIS_PORT:-6379}"; do\n        echo "Redis not ready, waiting..."\n        sleep 2\n    done\n    echo "Redis is ready!"\nfi\n\n# Combine all JVM options\nFULL_JAVA_OPTS="$JVM_OPTS $MEMORY_OPTS $VIRTUAL_THREADS_OPTS $APP_OPTS $CONSUL_OPTS $SECURITY_OPTS $JAVA_OPTS"\n\necho "Starting Broker Auth Service with Java options:"\necho "$FULL_JAVA_OPTS"\n\n# Start the application in background\njava $FULL_JAVA_OPTS -jar app.jar &\nAPP_PID=$!\n\n# Wait for the application to finish\nwait "$APP_PID"\n' > /app/entrypoint.sh && \
    chmod +x /app/entrypoint.sh && \
    chown trademaster:trademaster /app/entrypoint.sh

# Switch to non-root user
USER trademaster

# Expose ports
EXPOSE 8084

# Environment variables
ENV SPRING_PROFILES_ACTIVE="docker" \
    SERVER_PORT=8084 \
    LOGGING_LEVEL_ROOT=INFO \
    LOGGING_LEVEL_COM_TRADEMASTER=DEBUG

# JVM Configuration for Production with Java 24 Virtual Threads
ENV JVM_OPTS="--enable-preview \
-XX:+UseZGC \
-XX:+UnlockExperimentalVMOptions \
-XX:+UseTransparentHugePages \
-XX:MaxRAMPercentage=75.0 \
-XX:MaxMetaspaceSize=256m \
-XX:MetaspaceSize=128m \
-XX:MaxDirectMemorySize=512m \
-XX:+UseCompressedOops \
-XX:+UseCompressedClassPointers \
-XX:+OptimizeStringConcat \
-XX:+UseStringDeduplication \
-Xlog:gc*:logs/gc.log:time,tags \
-XX:+HeapDumpOnOutOfMemoryError \
-XX:HeapDumpPath=logs/heapdump.hprof \
-Djava.security.egd=file:/dev/./urandom \
-Djava.awt.headless=true \
-Dfile.encoding=UTF-8 \
-Duser.timezone=UTC \
-Djava.net.preferIPv4Stack=true"

# Memory Configuration (defaults - can be overridden)
ENV MEMORY_OPTS="-Xms512m -Xmx2g"

# Virtual Threads Configuration
ENV VIRTUAL_THREADS_OPTS="-Djdk.virtualThreadScheduler.parallelism=200 \
-Djdk.virtualThreadScheduler.maxPoolSize=1000 \
-Dspring.threads.virtual.enabled=true"

# Application Configuration - Fixed to match trading service pattern
ENV APP_OPTS="-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE} \
-Dserver.port=${SERVER_PORT} \
-Dlogging.level.root=${LOGGING_LEVEL_ROOT} \
-Dlogging.level.com.trademaster=${LOGGING_LEVEL_COM_TRADEMASTER} \
-Dmanagement.endpoints.web.exposure.include=health,info,metrics,prometheus,env,threaddump,heapdump,configprops,refresh,beans,readiness,liveness \
-Dmanagement.endpoint.health.enabled=true \
-Dmanagement.endpoint.health.show-details=always \
-Dmanagement.endpoint.health.probes.enabled=true \
-Dmanagement.server.port=9084 \
-Dspring.mvc.throw-exception-if-no-handler-found=true"

# Consul Configuration - Use environment variables from docker-compose
ENV CONSUL_OPTS=""

# Security Configuration
ENV SECURITY_OPTS="-Dnetworkaddress.cache.ttl=60 \
-Dnetworkaddress.cache.negative.ttl=10"

# Health check - Management port actuator endpoint
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:9084/actuator/health || exit 1

# Use dumb-init for proper signal handling
ENTRYPOINT ["/usr/bin/dumb-init", "--"]
CMD ["/app/entrypoint.sh"]