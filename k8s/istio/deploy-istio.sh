#!/bin/bash

# TradeMaster Broker Auth Service - Istio Service Mesh Deployment Script
# This script deploys the service to Istio service mesh with full observability

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
NAMESPACE="${NAMESPACE:-trademaster}"
CLUSTER_NAME="${CLUSTER_NAME:-trademaster-prod}"
ISTIO_NAMESPACE="${ISTIO_NAMESPACE:-istio-system}"
IMAGE_TAG="${IMAGE_TAG:-1.0.0}"
ENVIRONMENT="${ENVIRONMENT:-production}"

echo -e "${BLUE}🚀 Deploying TradeMaster Broker Auth Service to Istio Service Mesh${NC}"
echo "=================================================================="

# Function to check prerequisites
check_prerequisites() {
    echo -e "${YELLOW}📋 Checking prerequisites...${NC}"
    
    # Check kubectl
    if ! command -v kubectl &> /dev/null; then
        echo -e "${RED}❌ kubectl is not installed${NC}"
        exit 1
    fi
    
    # Check istioctl
    if ! command -v istioctl &> /dev/null; then
        echo -e "${RED}❌ istioctl is not installed${NC}"
        echo "Install from: https://istio.io/latest/docs/setup/getting-started/"
        exit 1
    fi
    
    # Check cluster connectivity
    if ! kubectl cluster-info &> /dev/null; then
        echo -e "${RED}❌ Cannot connect to Kubernetes cluster${NC}"
        exit 1
    fi
    
    # Check if Istio is installed
    if ! kubectl get namespace $ISTIO_NAMESPACE &> /dev/null; then
        echo -e "${RED}❌ Istio is not installed in cluster${NC}"
        echo "Install Istio first: istioctl install"
        exit 1
    fi
    
    echo -e "${GREEN}✅ Prerequisites check passed${NC}"
}

# Function to validate Istio installation
validate_istio() {
    echo -e "${YELLOW}🔍 Validating Istio installation...${NC}"
    
    # Check Istio version
    ISTIO_VERSION=$(istioctl version --short 2>/dev/null | grep "client version" | awk '{print $3}' || echo "unknown")
    echo "Istio Version: $ISTIO_VERSION"
    
    # Check Istio components
    echo "Checking Istio components..."
    kubectl get pods -n $ISTIO_NAMESPACE
    
    # Validate Istio configuration
    echo "Validating Istio configuration..."
    istioctl analyze -n $NAMESPACE || echo "Analysis completed with warnings"
    
    echo -e "${GREEN}✅ Istio validation completed${NC}"
}

# Function to setup namespace
setup_namespace() {
    echo -e "${YELLOW}📁 Setting up namespace...${NC}"
    
    # Create namespace if it doesn't exist
    kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -
    
    # Enable Istio injection
    kubectl label namespace $NAMESPACE istio-injection=enabled --overwrite
    kubectl label namespace $NAMESPACE name=$NAMESPACE --overwrite
    kubectl label namespace $NAMESPACE environment=$ENVIRONMENT --overwrite
    kubectl label namespace $NAMESPACE team=platform --overwrite
    
    echo -e "${GREEN}✅ Namespace $NAMESPACE configured for Istio${NC}"
}

# Function to create secrets
create_secrets() {
    echo -e "${YELLOW}🔐 Creating secrets...${NC}"
    
    # Create Vault configuration secret
    kubectl create secret generic vault-config \
        --namespace=$NAMESPACE \
        --from-literal=vault-uri="${VAULT_URI:-https://vault.trademaster.com:8200}" \
        --from-literal=vault-role-id="${VAULT_ROLE_ID}" \
        --from-literal=vault-secret-id="${VAULT_SECRET_ID}" \
        --dry-run=client -o yaml | kubectl apply -f -
    
    # Create database configuration secret  
    kubectl create secret generic database-config \
        --namespace=$NAMESPACE \
        --from-literal=database-url="${DATABASE_URL}" \
        --from-literal=database-username="${DATABASE_USERNAME}" \
        --dry-run=client -o yaml | kubectl apply -f -
    
    # Create TLS secret for Gateway
    if [ ! -z "$TLS_CERT_PATH" ] && [ ! -z "$TLS_KEY_PATH" ]; then
        kubectl create secret tls trademaster-tls-secret \
            --namespace=$ISTIO_NAMESPACE \
            --cert="$TLS_CERT_PATH" \
            --key="$TLS_KEY_PATH" \
            --dry-run=client -o yaml | kubectl apply -f -
    else
        echo -e "${YELLOW}⚠️  TLS certificate not provided, using default${NC}"
    fi
    
    echo -e "${GREEN}✅ Secrets created${NC}"
}

# Function to deploy application
deploy_application() {
    echo -e "${YELLOW}🚀 Deploying application...${NC}"
    
    # Update image tag in deployment
    sed -i "s|image: trademaster/broker-auth-service:.*|image: trademaster/broker-auth-service:$IMAGE_TAG|g" service-mesh-config.yaml
    
    # Apply service mesh configuration
    kubectl apply -f service-mesh-config.yaml -n $NAMESPACE
    
    # Apply traffic policies
    kubectl apply -f traffic-policies.yaml -n $NAMESPACE
    
    # Apply observability configuration
    kubectl apply -f observability.yaml
    
    echo -e "${GREEN}✅ Application deployed to service mesh${NC}"
}

# Function to wait for deployment
wait_for_deployment() {
    echo -e "${YELLOW}⏳ Waiting for deployment to be ready...${NC}"
    
    # Wait for deployment rollout
    kubectl rollout status deployment/broker-auth-service -n $NAMESPACE --timeout=300s
    
    # Wait for pods to be ready
    kubectl wait --for=condition=Ready pods -l app=broker-auth-service -n $NAMESPACE --timeout=300s
    
    echo -e "${GREEN}✅ Deployment is ready${NC}"
}

# Function to verify deployment
verify_deployment() {
    echo -e "${YELLOW}🔍 Verifying deployment...${NC}"
    
    # Check pod status
    echo "Pod Status:"
    kubectl get pods -l app=broker-auth-service -n $NAMESPACE -o wide
    
    # Check service status
    echo "Service Status:"
    kubectl get services -l app=broker-auth-service -n $NAMESPACE
    
    # Check Istio proxy injection
    echo "Checking Istio proxy injection..."
    POD_NAME=$(kubectl get pods -l app=broker-auth-service -n $NAMESPACE -o jsonpath='{.items[0].metadata.name}')
    CONTAINER_COUNT=$(kubectl get pod $POD_NAME -n $NAMESPACE -o jsonpath='{.spec.containers[*].name}' | wc -w)
    
    if [ "$CONTAINER_COUNT" -ge 2 ]; then
        echo -e "${GREEN}✅ Istio sidecar injected successfully${NC}"
    else
        echo -e "${RED}❌ Istio sidecar not found${NC}"
        exit 1
    fi
    
    # Check application health
    echo "Checking application health..."
    kubectl exec $POD_NAME -n $NAMESPACE -c broker-auth-service -- curl -f http://localhost:8081/actuator/health || echo "Health check failed"
    
    # Check Istio configuration
    echo "Checking Istio configuration..."
    istioctl proxy-config cluster $POD_NAME -n $NAMESPACE
    
    echo -e "${GREEN}✅ Deployment verification completed${NC}"
}

# Function to setup monitoring
setup_monitoring() {
    echo -e "${YELLOW}📊 Setting up monitoring...${NC}"
    
    # Apply ServiceMonitor for Prometheus
    kubectl apply -f - <<EOF
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: broker-auth-service-istio
  namespace: $NAMESPACE
  labels:
    app: broker-auth-service
spec:
  selector:
    matchLabels:
      app: broker-auth-service
  endpoints:
  - port: management
    interval: 30s
    path: /actuator/prometheus
  - port: http-monitoring
    interval: 15s
    path: /stats/prometheus
EOF
    
    echo -e "${GREEN}✅ Monitoring configured${NC}"
}

# Function to run smoke tests
run_smoke_tests() {
    echo -e "${YELLOW}🧪 Running smoke tests...${NC}"
    
    # Get service endpoint
    SERVICE_IP=$(kubectl get service broker-auth-service -n $NAMESPACE -o jsonpath='{.spec.clusterIP}')
    
    # Test health endpoint
    kubectl run test-pod --rm -i --tty --image=curlimages/curl --restart=Never -- \
        curl -f http://$SERVICE_IP:8081/actuator/health
    
    # Test metrics endpoint
    kubectl run test-pod --rm -i --tty --image=curlimages/curl --restart=Never -- \
        curl -f http://$SERVICE_IP:8081/actuator/prometheus | head -10
    
    echo -e "${GREEN}✅ Smoke tests completed${NC}"
}

# Function to show access information
show_access_info() {
    echo -e "${BLUE}📋 Access Information${NC}"
    echo "================================"
    
    # Get Gateway IP
    GATEWAY_IP=$(kubectl get service istio-ingressgateway -n $ISTIO_NAMESPACE -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "pending")
    
    echo -e "${YELLOW}Service Endpoints:${NC}"
    echo "  Internal Service: broker-auth-service.$NAMESPACE.svc.cluster.local:8080"
    echo "  Management Port:  broker-auth-service.$NAMESPACE.svc.cluster.local:8081"
    echo "  Gateway IP:       $GATEWAY_IP"
    echo "  External URL:     https://auth.trademaster.com (if DNS configured)"
    echo ""
    
    echo -e "${YELLOW}Observability:${NC}"
    echo "  Grafana:     https://grafana.trademaster.com"
    echo "  Jaeger:      https://jaeger.trademaster.com"
    echo "  Kiali:       https://kiali.trademaster.com"
    echo "  Prometheus:  https://prometheus.trademaster.com"
    echo ""
    
    echo -e "${YELLOW}Commands:${NC}"
    echo "  View pods:       kubectl get pods -n $NAMESPACE"
    echo "  View logs:       kubectl logs -f deployment/broker-auth-service -n $NAMESPACE"
    echo "  Port forward:    kubectl port-forward service/broker-auth-service 8080:8080 -n $NAMESPACE"
    echo "  Istio proxy:     istioctl proxy-config cluster <pod-name> -n $NAMESPACE"
    echo ""
}

# Function to cleanup deployment
cleanup() {
    echo -e "${YELLOW}🧹 Cleaning up deployment...${NC}"
    
    # Delete application resources
    kubectl delete -f service-mesh-config.yaml -n $NAMESPACE || true
    kubectl delete -f traffic-policies.yaml -n $NAMESPACE || true
    
    # Delete secrets
    kubectl delete secret vault-config database-config -n $NAMESPACE || true
    kubectl delete secret trademaster-tls-secret -n $ISTIO_NAMESPACE || true
    
    echo -e "${GREEN}✅ Cleanup completed${NC}"
}

# Main execution based on command
case "${1:-deploy}" in
    "deploy")
        check_prerequisites
        validate_istio
        setup_namespace
        create_secrets
        deploy_application
        wait_for_deployment
        verify_deployment
        setup_monitoring
        run_smoke_tests
        show_access_info
        ;;
    "cleanup")
        cleanup
        ;;
    "verify")
        verify_deployment
        ;;
    "test")
        run_smoke_tests
        ;;
    "info")
        show_access_info
        ;;
    "help")
        echo "Usage: $0 [deploy|cleanup|verify|test|info|help]"
        echo ""
        echo "Commands:"
        echo "  deploy   - Deploy service to Istio mesh (default)"
        echo "  cleanup  - Remove service from mesh"
        echo "  verify   - Verify deployment status"
        echo "  test     - Run smoke tests"
        echo "  info     - Show access information"
        echo "  help     - Show this help"
        echo ""
        echo "Environment Variables:"
        echo "  NAMESPACE        - Target namespace (default: trademaster)"
        echo "  IMAGE_TAG        - Docker image tag (default: 1.0.0)"
        echo "  ENVIRONMENT      - Environment name (default: production)"
        echo "  VAULT_URI        - Vault server URL"
        echo "  VAULT_ROLE_ID    - Vault AppRole role ID"
        echo "  VAULT_SECRET_ID  - Vault AppRole secret ID"
        echo "  DATABASE_URL     - Database connection URL"
        echo "  TLS_CERT_PATH    - TLS certificate file path"
        echo "  TLS_KEY_PATH     - TLS private key file path"
        ;;
    *)
        echo -e "${RED}Unknown command: $1${NC}"
        echo "Use '$0 help' for usage information"
        exit 1
        ;;
esac

echo -e "${GREEN}✅ Operation completed successfully!${NC}"