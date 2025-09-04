#!/bin/bash

# TradeMaster Broker Auth Service - Monitoring Stack Setup
# This script sets up the complete monitoring infrastructure

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
MONITORING_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$MONITORING_DIR")"

echo -e "${BLUE}🚀 Setting up TradeMaster Broker Auth Service Monitoring Stack${NC}"
echo "=================================================="

# Function to check if Docker is running
check_docker() {
    echo -e "${YELLOW}📋 Checking Docker...${NC}"
    if ! docker --version >/dev/null 2>&1; then
        echo -e "${RED}❌ Docker is not installed or not in PATH${NC}"
        exit 1
    fi

    if ! docker compose version >/dev/null 2>&1; then
        echo -e "${RED}❌ Docker Compose is not installed${NC}"
        exit 1
    fi

    if ! docker info >/dev/null 2>&1; then
        echo -e "${RED}❌ Docker daemon is not running${NC}"
        exit 1
    fi

    echo -e "${GREEN}✅ Docker is ready${NC}"
}

# Function to create necessary directories
create_directories() {
    echo -e "${YELLOW}📁 Creating monitoring directories...${NC}"
    
    directories=(
        "$MONITORING_DIR/grafana/dashboards/broker-auth"
        "$MONITORING_DIR/grafana/dashboards/system"
        "$MONITORING_DIR/grafana/dashboards/applications"
        "$MONITORING_DIR/grafana/dashboards/infrastructure"
        "$MONITORING_DIR/loki"
        "$MONITORING_DIR/promtail"
        "$MONITORING_DIR/data/prometheus"
        "$MONITORING_DIR/data/grafana"
        "$MONITORING_DIR/data/alertmanager"
        "$MONITORING_DIR/data/loki"
    )
    
    for dir in "${directories[@]}"; do
        if [ ! -d "$dir" ]; then
            mkdir -p "$dir"
            echo "  Created: $dir"
        fi
    done
    
    echo -e "${GREEN}✅ Directories created${NC}"
}

# Function to set up environment variables
setup_environment() {
    echo -e "${YELLOW}🔧 Setting up environment variables...${NC}"
    
    if [ ! -f "$MONITORING_DIR/.env" ]; then
        cat > "$MONITORING_DIR/.env" << EOF
# SMTP Configuration
SMTP_PASSWORD=your_smtp_password_here
GRAFANA_SMTP_PASSWORD=your_grafana_smtp_password_here

# Slack Configuration
SLACK_API_URL=https://hooks.slack.com/services/YOUR/SLACK/WEBHOOK

# PagerDuty Configuration
PAGERDUTY_SERVICE_KEY=your_pagerduty_service_key_here

# Grafana Configuration
GF_SECURITY_ADMIN_PASSWORD=admin123
GF_INSTALL_PLUGINS=grafana-piechart-panel,grafana-clock-panel,grafana-worldmap-panel

# Prometheus Configuration
PROMETHEUS_RETENTION_TIME=30d
PROMETHEUS_RETENTION_SIZE=50GB

# Alert Configuration
ALERT_WEBHOOK_URL=https://your-webhook-url.com/alerts
EOF
        echo -e "${YELLOW}⚠️  Created .env file with default values${NC}"
        echo -e "${YELLOW}⚠️  Please update the values in $MONITORING_DIR/.env${NC}"
    else
        echo -e "${GREEN}✅ Environment file exists${NC}"
    fi
}

# Function to create Loki configuration
create_loki_config() {
    echo -e "${YELLOW}📝 Creating Loki configuration...${NC}"
    
    cat > "$MONITORING_DIR/loki/loki-config.yml" << 'EOF'
auth_enabled: false

server:
  http_listen_port: 3100
  grpc_listen_port: 9096

common:
  path_prefix: /loki
  storage:
    filesystem:
      chunks_directory: /loki/chunks
      rules_directory: /loki/rules
  replication_factor: 1
  ring:
    instance_addr: 127.0.0.1
    kvstore:
      store: inmemory

query_scheduler:
  max_outstanding_requests_per_tenant: 32768

schema_config:
  configs:
    - from: 2020-10-24
      store: boltdb-shipper
      object_store: filesystem
      schema: v11
      index:
        prefix: index_
        period: 24h

ruler:
  alertmanager_url: http://localhost:9093

analytics:
  reporting_enabled: false
EOF
    
    echo -e "${GREEN}✅ Loki configuration created${NC}"
}

# Function to create Promtail configuration
create_promtail_config() {
    echo -e "${YELLOW}📝 Creating Promtail configuration...${NC}"
    
    cat > "$MONITORING_DIR/promtail/promtail-config.yml" << 'EOF'
server:
  http_listen_port: 9080
  grpc_listen_port: 0

positions:
  filename: /tmp/positions.yaml

clients:
  - url: http://loki:3100/loki/api/v1/push

scrape_configs:
  - job_name: containers
    static_configs:
      - targets:
          - localhost
        labels:
          job: containerlogs
          __path__: /var/lib/docker/containers/*/*log

    pipeline_stages:
      - json:
          expressions:
            output: log
            stream: stream
            attrs:
      - json:
          expressions:
            tag:
          source: attrs
      - regex:
          expression: (?P<container_name>(?:[^|]*))\|
          source: tag
      - timestamp:
          format: RFC3339Nano
          source: time
      - labels:
          stream:
          container_name:
      - output:
          source: output

  - job_name: syslog
    static_configs:
      - targets:
          - localhost
        labels:
          job: syslog
          __path__: /var/log/syslog
EOF
    
    echo -e "${GREEN}✅ Promtail configuration created${NC}"
}

# Function to copy dashboard files
copy_dashboards() {
    echo -e "${YELLOW}📊 Setting up Grafana dashboards...${NC}"
    
    # Copy the main overview dashboard
    if [ -f "$MONITORING_DIR/grafana/dashboards/broker-auth-overview.json" ]; then
        cp "$MONITORING_DIR/grafana/dashboards/broker-auth-overview.json" \
           "$MONITORING_DIR/grafana/dashboards/broker-auth/"
        echo "  Copied broker-auth-overview.json"
    fi
    
    echo -e "${GREEN}✅ Dashboards ready${NC}"
}

# Function to validate configurations
validate_configs() {
    echo -e "${YELLOW}🔍 Validating configurations...${NC}"
    
    # Check if required files exist
    required_files=(
        "$MONITORING_DIR/docker-compose.monitoring.yml"
        "$MONITORING_DIR/prometheus/prometheus.yml"
        "$MONITORING_DIR/alertmanager/alertmanager.yml"
        "$MONITORING_DIR/grafana/provisioning/datasources/datasources.yml"
        "$MONITORING_DIR/grafana/provisioning/dashboards/dashboards.yml"
    )
    
    for file in "${required_files[@]}"; do
        if [ ! -f "$file" ]; then
            echo -e "${RED}❌ Missing required file: $file${NC}"
            exit 1
        fi
    done
    
    echo -e "${GREEN}✅ All configurations valid${NC}"
}

# Function to start monitoring stack
start_monitoring() {
    echo -e "${YELLOW}🚀 Starting monitoring stack...${NC}"
    
    cd "$MONITORING_DIR"
    
    # Load environment variables
    if [ -f ".env" ]; then
        export $(cat .env | grep -v '^#' | xargs)
    fi
    
    # Start services
    docker compose -f docker-compose.monitoring.yml up -d
    
    echo -e "${GREEN}✅ Monitoring stack started${NC}"
    echo ""
    echo -e "${BLUE}📊 Access URLs:${NC}"
    echo "  Grafana:      http://localhost:3000 (admin/admin123)"
    echo "  Prometheus:   http://localhost:9090"
    echo "  Alertmanager: http://localhost:9093"
    echo "  Jaeger:       http://localhost:16686"
    echo ""
}

# Function to show status
show_status() {
    echo -e "${YELLOW}📊 Monitoring stack status:${NC}"
    cd "$MONITORING_DIR"
    docker compose -f docker-compose.monitoring.yml ps
}

# Function to show logs
show_logs() {
    echo -e "${YELLOW}📋 Recent logs:${NC}"
    cd "$MONITORING_DIR"
    docker compose -f docker-compose.monitoring.yml logs --tail=50
}

# Function to stop monitoring stack
stop_monitoring() {
    echo -e "${YELLOW}🛑 Stopping monitoring stack...${NC}"
    cd "$MONITORING_DIR"
    docker compose -f docker-compose.monitoring.yml down
    echo -e "${GREEN}✅ Monitoring stack stopped${NC}"
}

# Function to restart monitoring stack
restart_monitoring() {
    echo -e "${YELLOW}🔄 Restarting monitoring stack...${NC}"
    stop_monitoring
    start_monitoring
}

# Function to show help
show_help() {
    echo "TradeMaster Broker Auth Service - Monitoring Setup"
    echo ""
    echo "Usage: $0 [COMMAND]"
    echo ""
    echo "Commands:"
    echo "  setup     - Set up and start the monitoring stack"
    echo "  start     - Start the monitoring stack"
    echo "  stop      - Stop the monitoring stack"
    echo "  restart   - Restart the monitoring stack"
    echo "  status    - Show status of monitoring services"
    echo "  logs      - Show recent logs from all services"
    echo "  help      - Show this help message"
    echo ""
}

# Main execution logic
case "${1:-setup}" in
    "setup")
        check_docker
        create_directories
        setup_environment
        create_loki_config
        create_promtail_config
        copy_dashboards
        validate_configs
        start_monitoring
        ;;
    "start")
        start_monitoring
        ;;
    "stop")
        stop_monitoring
        ;;
    "restart")
        restart_monitoring
        ;;
    "status")
        show_status
        ;;
    "logs")
        show_logs
        ;;
    "help")
        show_help
        ;;
    *)
        echo -e "${RED}Unknown command: $1${NC}"
        show_help
        exit 1
        ;;
esac

echo -e "${GREEN}✅ Operation completed successfully!${NC}"