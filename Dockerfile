# Multi-stage Docker build for TradeMaster Broker Auth Service
# Optimized for Java 24 + Virtual Threads with production security

# Build stage
FROM openjdk:24-jdk-slim as builder

LABEL maintainer="TradeMaster Development Team"
LABEL description="TradeMaster Broker Authentication Service"
LABEL version="1.0.0"

# Install build dependencies
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradlew gradlew.bat ./
COPY gradle gradle/
COPY build.gradle settings.gradle ./

# Make gradlew executable
RUN chmod +x ./gradlew

# Download dependencies (for layer caching)
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY src src/

# Build application
RUN ./gradlew build --no-daemon -x test

# Runtime stage
FROM openjdk:24-jre-slim

# Create non-root user for security
RUN groupadd -r brokerauth && useradd -r -g brokerauth brokerauth

# Install runtime dependencies
RUN apt-get update && apt-get install -y \
    curl \
    dumb-init \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy built JAR from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Create directories for logs and temp files
RUN mkdir -p /app/logs /app/tmp && \
    chown -R brokerauth:brokerauth /app

# Copy health check script
COPY <<EOF /app/health-check.sh
#!/bin/bash
curl -f http://localhost:8087/actuator/health || exit 1
EOF

RUN chmod +x /app/health-check.sh && \
    chown brokerauth:brokerauth /app/health-check.sh

# Switch to non-root user
USER brokerauth

# Expose port
EXPOSE 8087

# Environment variables
ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_OPTS="-Xmx1g -Xms512m --enable-preview"
ENV TZ=UTC

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD /app/health-check.sh

# Use dumb-init for proper signal handling
ENTRYPOINT ["dumb-init", "--"]

# Start application with optimized JVM settings
CMD exec java $JAVA_OPTS \
    -Djava.security.egd=file:/dev/./urandom \
    -Djava.awt.headless=true \
    -Dspring.profiles.active=$SPRING_PROFILES_ACTIVE \
    -Dlogging.file.path=/app/logs \
    -Djava.io.tmpdir=/app/tmp \
    -jar app.jar