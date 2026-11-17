# CitiCore Banking Platform - Production Readiness Assessment

## Executive Summary

This assessment evaluates the CitiCore platform against **production deployment requirements** for a financial services application. The platform demonstrates a solid microservices foundation but has **significant gaps** in observability, resilience, deployment automation, and operational maturity.

**Overall Production Readiness: 45%** - Suitable for staging/sandbox only.

---

## 1. Configuration Management

### Current State
| Aspect | Status | Details |
|--------|--------|---------|
| Centralized Config | ✅ Implemented | Spring Cloud Config Server (native profile) |
| Environment Separation | ✅ Implemented | `application-local.yml`, `application-prod.yml` |
| Secrets Externalization | ❌ **Missing** | JWT secret, DB passwords in env vars only |
| Config Refresh | ⚠️ Partial | Spring Cloud Bus Kafka configured but not tested |
| Feature Flags | ❌ Missing | No toggle framework |

### Gaps & Fixes

**Secrets Management - CRITICAL**
```yaml
# Current: docker-compose.yml
environment:
  JWT_SECRET: ${JWT_SECRET}  # Plain env var
  DB_PASSWORD: ${DB_PASSWORD}

# Required: HashiCorp Vault integration
spring:
  cloud:
    vault:
      uri: https://vault.company.com
      authentication: KUBERNETES
      kv:
        enabled: true
        backend: secret
        profile-separator: '/'
```

**Config Refresh Verification**
```bash
# Test config refresh
curl -X POST http://localhost:8083/actuator/refresh
# Verify all @RefreshScope beans update
```

---

## 2. Observability Stack

### 2.1 Logging

| Requirement | Status | Gap |
|-------------|--------|-----|
| Structured JSON Logging | ❌ | Logback outputs plain text |
| Correlation IDs (traceId/spanId) | ❌ | MDC not populated from tracing |
| Log Levels Per Package | ✅ | Configured in application.yml |
| Centralized Log Aggregation | ❌ | No ELK/Loki/Fluentd |
| PII Masking in Logs | ❌ | Account numbers, emails logged |
| Audit Logging | ❌ | No separate audit log |

**Required Logback Configuration:**
```xml
<!-- logback-spring.xml -->
<configuration>
    <springProperty scope="context" name="appName" source="spring.application.name"/>
    
    <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"service":"${appName}","environment":"${ENV}"}</customFields>
            <fieldNames>
                <timestamp>@timestamp</timestamp>
                <message>message</message>
                <logger>logger</logger>
                <level>level</level>
                <thread>thread</thread>
                <traceId>traceId</traceId>
                <spanId>spanId</spanId>
            </fieldNames>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="JSON_CONSOLE"/>
    </root>
</configuration>
```

**MDC Filter for Trace Context:**
```java
@Component
public class TraceMDCFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
        String traceId = request.getHeader("X-B3-TraceId");
        String spanId = request.getHeader("X-B3-SpanId");
        try {
            if (traceId != null) MDC.put("traceId", traceId);
            if (spanId != null) MDC.put("spanId", spanId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

### 2.2 Metrics

| Requirement | Status | Gap |
|-------------|--------|-----|
| Prometheus Metrics | ✅ | `micrometer-registry-prometheus` |
| Custom Business Metrics | ❌ | Only JVM/system metrics |
| RED Metrics (Rate/Errors/Duration) | ⚠️ Partial | HTTP server metrics only |
| JVM Metrics | ✅ | Standard |
| Kafka Consumer Lag | ❌ | Not exposed |
| Database Pool Metrics | ✅ | HikariCP via Micrometer |
| Cache Hit/Miss Ratios | ❌ | Not exposed |

**Required Custom Metrics:**
```java
@Component
public class BankingMetrics {
    private final Counter transfersInitiated;
    private final Counter transfersCompleted;
    private final Counter transfersFailed;
    private final Counter transfersReversed;
    private final Timer transferDuration;
    private final Gauge activeAccounts;
    private final Gauge totalBalance;

    public BankingMetrics(MeterRegistry registry, AccountRepository accountRepo) {
        this.transfersInitiated = Counter.builder("banking.transfers.initiated").register(registry);
        this.transfersCompleted = Counter.builder("banking.transfers.completed").register(registry);
        this.transfersFailed = Counter.builder("banking.transfers.failed").register(registry);
        this.transfersReversed = Counter.builder("banking.transfers.reversed").register(registry);
        this.transferDuration = Timer.builder("banking.transfer.duration").register(registry);
        this.activeAccounts = Gauge.builder("banking.accounts.active", accountRepo, r -> r.countByStatus(AccountStatus.ACTIVE)).register(registry);
        this.totalBalance = Gauge.builder("banking.balance.total", accountRepo, r -> r.sumBalances()).register(registry);
    }
}
```

### 2.3 Distributed Tracing

| Requirement | Status | Gap |
|-------------|--------|-----|
| Trace Context Propagation | ⚠️ Partial | Micrometer Tracing bridge configured |
| Feign Trace Propagation | ❌ | No interceptor adding headers |
| Kafka Trace Propagation | ❌ | No producer/consumer interceptors |
| Zipkin Integration | ✅ | Dependencies present |
| Trace Sampling | ✅ | 100% in local config |

**Required Feign Interceptor:**
```java
@Configuration
public class FeignTracingConfig {
    @Bean
    public RequestInterceptor tracingInterceptor(Tracer tracer) {
        return template -> {
            Span span = tracer.currentSpan();
            if (span != null) {
                template.header("X-B3-TraceId", span.context().traceId());
                template.header("X-B3-SpanId", span.context().spanId());
                template.header("X-B3-Sampled", "1");
            }
        };
    }
}
```

**Required Kafka Interceptors:**
```yaml
spring:
  kafka:
    producer:
      interceptor-classes: io.micrometer.tracing.kafka.TracingProducerInterceptor
    consumer:
      interceptor-classes: io.micrometer.tracing.kafka.TracingConsumerInterceptor
```

### 2.4 Health Checks

| Requirement | Status | Gap |
|-------------|--------|-----|
| Liveness Probe | ✅ | `/actuator/health/liveness` |
| Readiness Probe | ✅ | `/actuator/health/readiness` |
| Downstream Dependency Checks | ❌ | Only DB checked |
| Kafka Health | ❌ | Not implemented |
| Redis Health | ✅ | Auto-configured |
| Feign Target Health | ❌ | Not implemented |
| Custom Business Health | ❌ | No "can process transfers" check |

**Required Health Indicators:**
```java
@Component
public class KafkaHealthIndicator implements HealthIndicator {
    private final KafkaTemplate<String, String> kafkaTemplate;
    
    @Override
    public Health health() {
        try {
            // Check broker connectivity
            kafkaTemplate.send("health-check", "ping").get(5, TimeUnit.SECONDS);
            return Health.up().withDetail("broker", "reachable").build();
        } catch (Exception e) {
            return Health.down().withDetail("error", e.getMessage()).build();
        }
    }
}

@Component
public class FeignTargetHealthIndicator implements HealthIndicator {
    private final AccountFeignClient accountClient;
    
    @Override
    public Health health() {
        try {
            accountClient.validateTransfer("HEALTH_CHECK", BigDecimal.ONE);
            return Health.up().build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}

@Component
public class BusinessHealthIndicator implements HealthIndicator {
    private final AccountRepository accountRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;
    
    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        
        // Check if we can process transfers
        long pendingOutbox = accountOutboxRepo.countByStatus(OutboxStatus.PENDING);
        if (pendingOutbox > 10000) {
            builder.down().withDetail("outbox_lag", pendingOutbox);
        }
        
        // Check replica lag
        long replicaLag = getReplicaLag();
        if (replicaLag > 30) {
            builder.down().withDetail("replica_lag_seconds", replicaLag);
        }
        
        return builder.build();
    }
}
```

---

## 3. Resilience & Fault Tolerance

### 3.1 Circuit Breakers

| Service | Client | Circuit Breaker | Status |
|---------|--------|-----------------|--------|
| Transaction → Account | AccountFeignClient | ❌ None | **CRITICAL** |
| Account → User | UserClient | ❌ None | **CRITICAL** |
| API Gateway → All | Spring Cloud Gateway | ✅ Resilience4j | Configured |

**Required Circuit Breaker Config:**
```yaml
# transaction-service application.yml
resilience4j:
  circuitbreaker:
    instances:
      account-service:
        registerHealthIndicator: true
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
        waitDurationInOpenState: 10s
        failureRateThreshold: 50
        recordExceptions:
          - feign.FeignException
          - java.util.concurrent.TimeoutException
  timelimiter:
    instances:
      account-service:
        timeoutDuration: 3s
  retry:
    instances:
      account-service:
        maxAttempts: 3
        waitDuration: 500ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
```

**Feign Client with Resilience4j:**
```java
@FeignClient(name = "user-service", configuration = FeignClientConfig.class)
public interface UserClient {
    @GetMapping("/api/v1/users/kyc-status/{userId}")
    @CircuitBreaker(name = "user-service", fallbackMethod = "kycStatusFallback")
    boolean getKycStatus(@PathVariable Long userId);

    default boolean kycStatusFallback(Long userId, Exception ex) {
        log.warn("KYC check failed for user {}, defaulting to false", userId, ex);
        return false; // Fail closed - no account creation without KYC
    }
}
```

### 3.2 Rate Limiting

| Layer | Status | Gap |
|-------|--------|-----|
| API Gateway | ✅ | Redis token bucket (IP-based) |
| Auth Endpoints | ❌ | **CRITICAL** - No rate limiting |
| Business Endpoints | ❌ | No per-user limits |
| Kafka Consumers | ❌ | No backpressure |

**Required Auth Rate Limiting:**
```java
@Configuration
public class RateLimitConfig {
    @Bean
    public RateLimiter loginRateLimiter() {
        return RateLimiter.of("login", RateLimiterConfig.custom()
            .limitForPeriod(5)
            .limitRefreshPeriod(Duration.ofMinutes(1))
            .timeoutDuration(Duration.ZERO)
            .build());
    }
    
    @Bean
    public RateLimiter registerRateLimiter() {
        return RateLimiter.of("register", RateLimiterConfig.custom()
            .limitForPeriod(3)
            .limitRefreshPeriod(Duration.ofMinutes(5))
            .build());
    }
}
```

### 3.3 Timeouts

| Communication | Configured | Value | Assessment |
|---------------|------------|-------|------------|
| Feign Connect | ✅ | 3s | Good |
| Feign Read | ✅ | 5s | Good |
| Kafka Producer | ❌ | Default | **Missing** |
| Kafka Consumer | ❌ | Default | **Missing** |
| Database Query | ❌ | Default | **Missing** |
| HTTP Client | ❌ | Default | **Missing** |

**Required Kafka Timeouts:**
```yaml
spring:
  kafka:
    producer:
      properties:
        delivery.timeout.ms: 30000
        request.timeout.ms: 10000
        max.block.ms: 5000
    consumer:
      properties:
        max.poll.interval.ms: 300000
        session.timeout.ms: 10000
        heartbeat.interval.ms: 3000
```

---

## 4. Deployment & Infrastructure

### 4.1 Container Security

| Check | Status | Finding |
|-------|--------|---------|
| Non-root User | ❌ | All Dockerfiles run as root |
| Read-only Root FS | ❌ | Not configured |
| Capability Dropping | ❌ | All capabilities retained |
| Security Scanning | ❌ | No Trivy/Grype in CI |
| Base Image Updates | ❌ | No automated updates |
| Multi-stage Build | ⚠️ Partial | Some services have local/prod Dockerfiles |

**Required Dockerfile Hardening:**
```dockerfile
# Base stage
FROM eclipse-temurin:17-jre-alpine AS base
RUN addgroup -g 1000 -S appgroup && adduser -u 1000 -S appuser -G appgroup

# Build stage
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY --chown=appuser:appgroup . .
RUN ./mvnw -B package -DskipTests

# Runtime stage
FROM base AS runtime
WORKDIR /app
COPY --from=build --chown=appuser:appgroup /app/target/*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
```

### 4.2 Kubernetes Readiness

| Resource | Status | Gap |
|----------|--------|-----|
| Deployment Manifests | ❌ | Not created |
| Service Manifests | ❌ | Not created |
| Ingress | ❌ | Not created |
| ConfigMaps/Secrets | ❌ | Not created |
| HPA (Horizontal Pod Autoscaler) | ❌ | Not configured |
| PDB (Pod Disruption Budget) | ❌ | Not configured |
| Network Policies | ❌ | Not configured |
| Resource Limits | ❌ | Not defined |
| Service Mesh | ❌ | Not evaluated |

**Required Deployment Template:**
```yaml
# k8s/account-service-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: account-service
  labels:
    app: account-service
    version: v1
spec:
  replicas: 3
  selector:
    matchLabels:
      app: account-service
  template:
    metadata:
      labels:
        app: account-service
        version: v1
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8083"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        fsGroup: 1000
      containers:
      - name: account-service
        image: citicore/account-service:latest
        ports:
        - containerPort: 8083
        envFrom:
        - configMapRef:
            name: account-service-config
        - secretRef:
            name: account-service-secrets
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8083
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8083
          initialDelaySeconds: 30
          periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: account-service
spec:
  selector:
    app: account-service
  ports:
  - port: 8083
    targetPort: 8083
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: account-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: account-service
  minReplicas: 3
  maxReplicas: 20
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

---

## 5. CI/CD Pipeline

### 5.1 Current State
**No CI/CD pipeline visible** - Only Maven build locally.

### 5.2 Required Pipeline Stages

```yaml
# .github/workflows/ci-cd.yaml
name: CI/CD Pipeline

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}

jobs:
  # Stage 1: Code Quality
  code-quality:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Cache Maven
        uses: actions/cache@v3
        with:
          path: ~/.m2/repository
          key: maven-${{ hashFiles('**/pom.xml') }}
      - name: Run Checkstyle
        run: ./mvnw checkstyle:check
      - name: Run SpotBugs
        run: ./mvnw spotbugs:check
      - name: Run PMD
        run: ./mvnw pmd:check

  # Stage 2: Unit & Integration Tests
  test:
    runs-on: ubuntu-latest
    services:
      mysql:
        image: mysql:8.0
        env:
          MYSQL_ROOT_PASSWORD: test
          MYSQL_DATABASE: test
        ports: [3306:3306]
        options: --health-cmd="mysqladmin ping" --health-interval=10s --health-timeout=5s --health-retries=3
      kafka:
        image: apache/kafka:3.8.0
        ports: [29092:29092]
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Run Tests
        run: ./mvnw verify
      - name: Generate Coverage
        run: ./mvnw jacoco:report
      - name: Upload Coverage
        uses: codecov/codecov-action@v3

  # Stage 3: Security Scanning
  security:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Run Dependency Check
        uses: dependency-check/Dependency-Check_Action@main
      - name: Run CodeQL
        uses: github/codeql-action/init@v2
        with:
          languages: java
      - name: Perform CodeQL Analysis
        uses: github/codeql-action/analyze@v2

  # Stage 4: Container Build & Scan
  build:
    needs: [code-quality, test, security]
    runs-on: ubuntu-latest
    if: github.event_name == 'push'
    steps:
      - uses: actions/checkout@v4
      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3
      - name: Build Images
        run: |
          for service in auth-service user-service account-service transaction-service notification-service apigateway-service config-service eureka-server; do
            docker build -t $REGISTRY/$IMAGE_NAME/$service:${{ github.sha }} ./$service
          done
      - name: Scan Images
        uses: aquasecurity/trivy-action@master
        with:
          scan-type: 'fs'
          scan-ref: '.'
          format: 'sarif'
          output: 'trivy-results.sarif'
      - name: Upload Trivy Results
        uses: github/codeql-action/upload-sarif@v2
        with:
          sarif_file: 'trivy-results.sarif'
      - name: Push Images
        run: |
          echo ${{ secrets.GITHUB_TOKEN }} | docker login $REGISTRY -u ${{ github.actor }} --password-stdin
          for service in auth-service user-service account-service transaction-service notification-service apigateway-service config-service eureka-server; do
            docker push $REGISTRY/$IMAGE_NAME/$service:${{ github.sha }}
          done

  # Stage 5: Deploy to Staging
  deploy-staging:
    needs: build
    runs-on: ubuntu-latest
    environment: staging
    steps:
      - name: Deploy to Staging
        run: |
          # Helm upgrade or kubectl apply
          helm upgrade --install citicore ./helm -n staging --set image.tag=${{ github.sha }}

  # Stage 6: Integration Tests
  integration-test:
    needs: deploy-staging
    runs-on: ubuntu-latest
    steps:
      - name: Run Postman/Newman Tests
        run: newman run integration-tests.postman_collection.json -e staging.postman_environment.json

  # Stage 7: Deploy to Production
  deploy-production:
    needs: integration-test
    runs-on: ubuntu-latest
    environment: production
    steps:
      - name: Deploy to Production
        run: |
          helm upgrade --install citicore ./helm -n production --set image.tag=${{ github.sha }}
      - name: Smoke Tests
        run: newman run smoke-tests.postman_collection.json -e production.postman_environment.json
      - name: Notify Slack
        uses: 8398a7/action-slack@v3
        with:
          status: ${{ job.status }}
          webhook_url: ${{ secrets.SLACK_WEBHOOK }}
```

---

## 6. Database Operations

### 6.1 Migration Strategy
```yaml
# Required: Flyway configuration
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    validate-on-migrate: true
    out-of-order: false
  jpa:
    hibernate:
      ddl-auto: validate  # NEVER update in production!
```

### 6.2 Backup & Recovery
```bash
#!/bin/bash
# Daily backup script (run via cron)
BACKUP_DIR="/backups/mysql/$(date +%Y%m%d)"
mkdir -p $BACKUP_DIR

# Full backup with binlog position
xtrabackup --backup --target-dir=$BACKUP_DIR --user=backup_user --password=$BACKUP_PASSWORD

# Compress and encrypt
tar -czf $BACKUP_DIR.tar.gz -C $BACKUP_DIR .
gpg --encrypt --recipient backup@company.com $BACKUP_DIR.tar.gz

# Upload to S3
aws s3 cp $BACKUP_DIR.tar.gz.gpg s3://citicore-backups/mysql/

# Cleanup local (keep 7 days)
find /backups/mysql -type f -mtime +7 -delete
```

### 6.3 Point-in-Time Recovery
```bash
# Restore to specific timestamp
xtrabackup --prepare --target-dir=/restore/base
xtrabackup --prepare --target-dir=/restore/base --incremental-dir=/restore/inc1
# ... apply all incrementals to desired point
xtrabackup --copy-back --target-dir=/restore/base --datadir=/var/lib/mysql
```

---

## 7. Disaster Recovery

### 7.1 RPO/RTO Targets

| Component | RPO | RTO | Strategy |
|-----------|-----|-----|----------|
| MySQL (Primary) | 15 min | 2 hours | Binlog replication + PITR |
| MySQL (Replica) | 0 | 30 min | Promote replica |
| Kafka | Near-zero | 1 hour | MirrorMaker to DR cluster |
| Redis | 6 hours | 30 min | RDB snapshots |
| S3 (KYC) | Near-zero | Immediate | Cross-region replication |
| Config Server | N/A | N/A | Git backup |

### 7.2 DR Runbook (Sample)
```markdown
## DR Scenario: Primary MySQL Failure

### Detection
- Alert: "MySQL Primary Unreachable" (Prometheus)
- Health check: `/actuator/health/readiness` failing on account-service

### Response (Target: < 15 min)
1. Verify replica is healthy: `kubectl exec replica-mysql -- mysqladmin ping`
2. Promote replica: `kubectl patch mysql replica --type=merge -p '{"spec":{"primary":true}}'`
3. Update DNS/Config: Point services to new primary
4. Verify application health: `curl account-service/actuator/health`
5. Notify team: Slack #incidents channel

### Recovery
1. Provision new replica
2. Configure replication from new primary
3. Validate data consistency
4. Update runbook with lessons learned
```

---

## 8. Graceful Shutdown

### 8.1 Current State
**No graceful shutdown handling** - SIGTERM kills process immediately.

### 8.2 Required Implementation
```java
@SpringBootApplication
public class AccountServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(AccountServiceApplication.class);
        app.addListeners(new GracefulShutdownListener());
        app.run(args);
    }
}

@Component
class GracefulShutdownListener implements ApplicationListener<ContextClosedEvent> {
    private final AccountOutboxPublisher outboxPublisher;
    private final KafkaTemplate<String, String> kafkaTemplate;
    
    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        log.info("Graceful shutdown initiated");
        
        // 1. Stop accepting new requests (readiness false)
        // 2. Wait for in-flight requests (max 30s)
        // 3. Flush outbox
        outboxPublisher.publish(); // One final poll
        // 4. Flush Kafka producer
        kafkaTemplate.flush(Duration.ofSeconds(10));
        // 5. Close connections
        log.info("Graceful shutdown complete");
    }
}
```

**Kubernetes PreStop Hook:**
```yaml
lifecycle:
  preStop:
    exec:
      command: ["/bin/sh", "-c", "sleep 15"]  # Wait for endpoint removal
```

---

## 9. API Documentation

### 9.1 Current State
**No OpenAPI/Swagger documentation** - No springdoc-openapi dependency.

### 9.2 Required Setup
```xml
<!-- Add to all service pom.xml -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

```java
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("CitiCore Account Service API")
                .version("v1")
                .description("Account management APIs")
                .contact(new Contact().name("Platform Team").email("platform@citicore.com"))
                .license(new License().name("Proprietary").url("https://citicore.com/license")))
            .servers(List.of(new Server().url("/api/v1").description("Current version")))
            .components(new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")));
    }
}
```

---

## 10. Production Readiness Checklist

| Category | Item | Status | Priority |
|----------|------|--------|----------|
| **Config** | Secrets in Vault | ❌ | P0 |
| **Config** | Config refresh tested | ❌ | P1 |
| **Observability** | JSON structured logs | ❌ | P0 |
| **Observability** | Correlation IDs | ❌ | P0 |
| **Observability** | Centralized logging (ELK/Loki) | ❌ | P1 |
| **Observability** | Custom business metrics | ❌ | P1 |
| **Observability** | Distributed tracing (Feign/Kafka) | ❌ | P1 |
| **Observability** | Kafka lag monitoring | ❌ | P1 |
| **Observability** | Business health indicators | ❌ | P1 |
| **Resilience** | Circuit breakers on Feign | ❌ | P0 |
| **Resilience** | Rate limiting on auth | ❌ | P0 |
| **Resilience** | Rate limiting on business APIs | ❌ | P1 |
| **Resilience** | Kafka timeouts configured | ❌ | P1 |
| **Resilience** | Dead letter queues | ❌ | P0 |
| **Resilience** | Consumer retry with backoff | ❌ | P1 |
| **Deployment** | Non-root Docker images | ❌ | P0 |
| **Deployment** | Kubernetes manifests | ❌ | P1 |
| **Deployment** | Resource limits/requests | ❌ | P1 |
| **Deployment** | HPA configured | ❌ | P2 |
| **Deployment** | Network policies | ❌ | P2 |
| **Deployment** | Service mesh evaluated | ❌ | P3 |
| **CI/CD** | Automated pipeline | ❌ | P0 |
| **CI/CD** | Security scanning (SAST/DAST/SCA) | ❌ | P0 |
| **CI/CD** | Container scanning | ❌ | P0 |
| **CI/CD** | Automated deploy to staging | ❌ | P1 |
| **CI/CD** | Integration tests in pipeline | ❌ | P1 |
| **CI/CD** | Blue-green/canary deploy | ❌ | P2 |
| **Database** | Flyway migrations | ❌ | P0 |
| **Database** | ddl-auto: validate | ❌ | P0 |
| **Database** | Automated backups | ❌ | P0 |
| **Database** | PITR tested | ❌ | P1 |
| **Database** | Connection pool tuned | ❌ | P1 |
| **DR** | DR runbook documented | ❌ | P1 |
| **DR** | Quarterly DR drills | ❌ | P2 |
| **DR** | Cross-region replication | ❌ | P1 |
| **Operations** | Graceful shutdown | ❌ | P1 |
| **Operations** | API documentation (OpenAPI) | ❌ | P1 |
| **Operations** | Runbooks for common incidents | ❌ | P1 |
| **Operations** | On-call rotation defined | ❌ | P1 |
| **Operations** | SLO/SLI defined | ❌ | P2 |

---

## 11. Estimated Effort to Production Ready

| Phase | Duration | Team | Focus |
|-------|----------|------|-------|
| **Phase 1: Critical Fixes** | 3 weeks | 3 engineers | Secrets, auth rate limiting, circuit breakers, DLQ, Docker hardening |
| **Phase 2: Observability** | 4 weeks | 2 engineers | Logging, metrics, tracing, health checks, dashboards |
| **Phase 3: CI/CD & Deployment** | 4 weeks | 2 engineers | Pipeline, K8s manifests, security scanning |
| **Phase 4: Database & DR** | 3 weeks | 2 engineers | Flyway, backups, DR runbooks, connection pooling |
| **Phase 5: Operational Excellence** | 2 weeks | 1 engineer | Runbooks, API docs, graceful shutdown, SLOs |
| **Total** | **16 weeks** | **5-6 engineers** | **Production Ready** |

---

## 12. Recommended Tooling

| Category | Recommended Tools |
|----------|-------------------|
| Secrets | HashiCorp Vault, AWS Secrets Manager |
| Logging | ELK Stack (Elasticsearch, Logstash, Kibana) or Loki + Grafana |
| Metrics | Prometheus + Grafana |
| Tracing | Zipkin or Jaeger |
| CI/CD | GitHub Actions / GitLab CI / Jenkins |
| Container Scan | Trivy, Grype, Snyk |
| SAST | SonarQube, CodeQL, SpotBugs |
| DAST | OWASP ZAP |
| K8s | Helm, ArgoCD/Flux, Kustomize |
| Service Mesh | Istio or Linkerd (evaluate) |
| Database | Percona XtraBackup, Orchestrator |
| Chaos Engineering | Chaos Mesh, LitmusChaos |

---

## 13. Go/No-Go Criteria for Production Launch

**Must Have (No Launch Without):**
- [ ] All P0 security issues fixed (SEC-001, SEC-002, SEC-009, SEC-010, SEC-015)
- [ ] Double-entry ledger implemented (DATABASE_AUDIT)
- [ ] Secrets in Vault, not env vars
- [ ] Auth rate limiting active
- [ ] Circuit breakers on all Feign clients
- [ ] DLQ configured for all Kafka consumers
- [ ] Flyway migrations with `ddl-auto: validate`
- [ ] Automated backups with tested restore
- [ ] Non-root Docker images
- [ ] CI/CD pipeline with security scanning
- [ ] Graceful shutdown implemented
- [ ] Health checks cover all dependencies

**Should Have (Launch With Plan):**
- [ ] Centralized logging (ELK/Loki)
- [ ] Distributed tracing (Feign + Kafka)
- [ ] Custom business metrics
- [ ] Kubernetes manifests with HPA
- [ ] DR runbook documented
- [ ] OpenAPI documentation published

**Nice to Have (Post-Launch):**
- [ ] Service mesh
- [ ] Chaos engineering
- [ ] Advanced fraud detection
- [ ] Multi-region deployment