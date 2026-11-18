# ADR-014: Service Discovery — Kubernetes DNS (Production) / Eureka (Local)

## Status
✅ **APPROVED** (2026-11-18)

## Context

CitiCore currently uses Netflix Eureka for service discovery in all environments (Docker Compose, local development). Production deployment targets AWS EKS, which provides native service discovery via Kubernetes DNS (CoreDNS). Running both Eureka and Kubernetes DNS in production creates operational complexity, potential conflicts, and dual maintenance burden.

**Requirements:**
- Production: Use Kubernetes-native service discovery
- Local Development: Continue using Eureka (simple, no Kubernetes needed)
- Clear separation between local and production configurations
- No immediate removal of Eureka code (documentation-only task)
- Document transition strategy

## Decision

**Use Kubernetes DNS (CoreDNS) for service discovery in AWS EKS production; retain Netflix Eureka for local Docker Compose development only.**

### Production (EKS): Kubernetes DNS

```yaml
# Kubernetes Service (Headless for direct pod access)
apiVersion: v1
kind: Service
metadata:
  name: auth-service
  namespace: citicore-prod
spec:
  clusterIP: None  # Headless service
  ports:
    - port: 8081
      targetPort: 8081
      name: http
  selector:
    app: auth-service
---
# Standard Service (for load balancing)
apiVersion: v1
kind: Service
metadata:
  name: auth-service-lb
  namespace: citicore-prod
spec:
  type: ClusterIP
  ports:
    - port: 8081
      targetPort: 8081
  selector:
    app: auth-service
```

**Service Resolution:**
- **Internal DNS**: `auth-service.citicore-prod.svc.cluster.local` → resolves to pod IPs (headless) or ClusterIP (LB)
- **Short Name**: `auth-service` → resolves within same namespace
- **Cross-Namespace**: `auth-service.citicore-prod` → resolves across namespaces

### Local Development (Docker Compose): Netflix Eureka

```yaml
# docker-compose.yml
services:
  eureka-server:
    image: citicore/eureka-server:latest
    ports:
      - "8761:8761"
    environment:
      - EUREKA_CLIENT_REGISTER_WITH_EUREKA=false
      - EUREKA_CLIENT_FETCH_REGISTRY=false
  
  auth-service:
    image: citicore/auth-service:latest
    environment:
      - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://eureka-server:8761/eureka
      - EUREKA_INSTANCE_PREFER_IP_ADDRESS=true
    depends_on:
      - eureka-server
```

### Spring Boot Configuration

```yaml
# Production (application-prod.yml)
spring:
  cloud:
    kubernetes:
      discovery:
        enabled: true
        all-namespaces: false
    loadbalancer:
      kubernetes:
        enabled: true
  application:
    name: auth-service

# Local (application-local.yml)
spring:
  cloud:
    kubernetes:
      discovery:
        enabled: false
  eureka:
    client:
      service-url:
        defaultZone: http://eureka-server:8761/eureka
      register-with-eureka: true
      fetch-registry: true
    instance:
      prefer-ip-address: true
      lease-renewal-interval-in-seconds: 10
      lease-expiration-duration-in-seconds: 30
```

### Service Registry Comparison

| Aspect | Kubernetes DNS (Prod) | Eureka (Local) |
|--------|----------------------|----------------|
| **Mechanism** | CoreDNS + EndpointSlices | Netflix Eureka Server |
| **Registration** | Automatic (kubelet) | Client-side (Spring Cloud Netflix) |
| **Health Checks** | Kubernetes Probes (liveness/readiness) | Eureka Heartbeat |
| **Load Balancing** | kube-proxy (IPVS) + Spring Cloud LoadBalancer | Ribbon (deprecated) / Spring Cloud LoadBalancer |
| **Multi-Namespace** | Native DNS | Requires Eureka zones |
| **TLS/mTLS** | Istio/Linkerd (future) | Manual config |
| **Operational Overhead** | None (built-in) | Separate Eureka cluster |

### Transition Strategy

| Phase | Action | Timeline |
|-------|--------|----------|
| **Phase 1 (Current)** | Dual registration: Services register with both Eureka and Kubernetes | Immediate |
| **Phase 2** | Validate Kubernetes DNS in staging; deprecate Eureka in staging configs | Sprint 1 |
| **Phase 3** | Remove Eureka dependencies from production Helm charts | Sprint 2 |
| **Phase 4** | Remove Eureka client code from services (after production stability) | Sprint 3+ |
| **Phase 5** | Decommission Eureka Docker image; local-only Eureka in docker-compose | Sprint 4 |

**Do not remove existing Eureka code or configurations as part of this documentation-only task.**

### Client-Side Load Balancing

```java
@Configuration
public class LoadBalancerConfig {
    
    @Bean
    public ReactorServiceInstanceLoadBalancer serviceInstanceLoadBalancer(
            DiscoveryClient discoveryClient) {
        return new ZoneAwareLoadBalancer(discoveryClient);
    }
    
    @Bean
    public ServiceInstanceListSupplier discoveryClientSupplier(
            DiscoveryClient discoveryClient) {
        return new DiscoveryClientServiceInstanceListSupplier(discoveryClient);
    }
}
```

### Feign Client Configuration

```yaml
# Feign with Kubernetes discovery
feign:
  client:
    config:
      default:
        connectTimeout: 3000
        readTimeout: 5000
        loggerLevel: BASIC
  okhttp:
    enabled: true

# Spring Cloud LoadBalancer
spring:
  cloud:
    loadbalancer:
      kubernetes:
        enabled: true
      retry:
        enabled: true
        max-attempts: 3
```

### Network Policies (Zero Trust)

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: auth-service-egress
  namespace: citicore-prod
spec:
  podSelector:
    matchLabels:
      app: auth-service
  policyTypes:
    - Egress
  egress:
    - to:
        - namespaceSelector:
            matchLabels:
              name: citicore-prod
      ports:
        - protocol: TCP
          port: 8082  # user-service
        - protocol: TCP
          port: 8083  # account-service
        - protocol: TCP
          port: 8084  # transaction-service
        - protocol: TCP
          port: 8087  # kyc-workflow-service
        - protocol: TCP
          port: 3306  # RDS MySQL
        - protocol: TCP
          port: 6379  # ElastiCache Redis
        - protocol: TCP
          port: 9092  # MSK Kafka
```

## Alternatives Considered

| Option | Description | Pros | Cons | Verdict |
|--------|-------------|------|------|---------|
| **A) K8s DNS Only (Production) + Eureka (Local)** | Native K8s discovery; Eureka for local | No dual system in prod; local simplicity | Two configs to maintain | ✅ **Selected** |
| **B) Eureka Everywhere** | Eureka in EKS via Helm | Single discovery everywhere | Extra infra; dual health checks; legacy | ❌ Rejected |
| **C) Consul** | HashiCorp Consul on EKS | Advanced features; multi-cluster | Operational overhead; extra cost | Future consideration |
| **C) Istio Service Mesh** | Istio SDS + Pilot | mTLS; traffic management; observability | Complexity; resource overhead | Phase 3+ |

## Consequences & Trade-offs

### Positive
- **Native Integration**: No additional discovery infrastructure in production
- **Automatic Registration**: Pods registered via kubelet; no client-side code
- **Health Alignment**: Kubernetes probes = discovery health
- **Cost**: No Eureka cluster to run in production
- **Local Simplicity**: Eureka in docker-compose works without Kubernetes

### Negative
- **Configuration Divergence**: Separate `application-prod.yml` vs `application-local.yml`
- **Local-Prod Parity**: Local doesn't perfectly mirror production discovery
- **Eureka Maintenance**: Still need Eureka image for local development
- **Migration Effort**: Dual-write period required

## Security Implications

- **Zero Trust**: NetworkPolicies enforce service-to-service communication
- **No Discovery Spoofing**: DNS controlled by Kubernetes; no rogue registration
- **mTLS Ready**: Foundation for Istio/Linkerd service mesh
- **Least Privilege**: Services only resolve namespaces they need

## Implementation Considerations

1. **Profile-Specific Config**: Use Spring profiles (`prod`, `local`) for discovery config
2. **Health Endpoint Alignment**: Kubernetes probes must match Eureka health semantics
3. **Graceful Shutdown**: PreStop hook to deregister from Eureka (local) / wait for EndpointSlice removal (K8s)
4. **Testing**: Verify service resolution in both environments
5. **Documentation**: Clear runbooks for local vs production debugging

## Dependencies

- **DEC-001**: Database Topology (services need to resolve DB hostnames too)
- **AWS**: EKS, CoreDNS, VPC, Route53 (for external DNS)
- **Spring Cloud**: Spring Cloud Kubernetes, Spring Cloud LoadBalancer

## Future Migration Considerations

| Phase | Trigger | Action |
|-------|---------|--------|
| **Phase 2** | mTLS requirement | Deploy Istio/Linkerd; offload discovery to service mesh |
| **Phase 3** | Multi-cluster | ClusterSet / Multi-cluster DNS (CoreDNS Federation) |
| **Phase 4** | Local Kubernetes | Kind/k3d for local; retire Eureka entirely |

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-11-18 | Architecture Team | Initial ADR |

---

**END OF ADR**