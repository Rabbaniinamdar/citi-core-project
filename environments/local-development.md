# CitiCore Banking Platform - Local Development Guide

## Document Information

| Field | Value |
|-------|-------|
| **Document Title** | CitiCore Local Development Guide |
| **Version** | 1.0 |
| **Status** | Draft for Review |
| **Classification** | Internal - Confidential |
| **Author** | Architecture Team |
| **Reviewers** | Engineering Lead, DevOps Lead |
| **Last Updated** | 2026-11-19 |

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Quick Start](#2-quick-start)
3. [Environment Configuration](#3-environment-configuration)
4. [Infrastructure Services](#4-infrastructure-services)
5. [Running Microservices](#5-running-microservices)
6. [Development Workflows](#6-development-workflows)
7. [Testing](#7-testing)
8. [Debugging](#8-debugging)
9. [Troubleshooting](#9-troubleshooting)
10. [Reset Procedures](#10-reset-procedures)
11. [IDE Integration](#11-ide-integration)
12. [Common Commands Reference](#12-common-commands-reference)

---

## 1. Prerequisites

### 1.1 Required Software

| Tool | Minimum Version | Installation |
|------|-----------------|--------------|
| **Docker Desktop** | 4.25+ | <https://www.docker.com/products/docker-desktop/> |
| **Docker Compose** | v2.23+ | Included with Docker Desktop |
| **Java** | 17 (LTS) | <https://adoptium.net/temurin/releases/> |
| **Maven** | 3.9+ | <https://maven.apache.org/download.cgi> |
| **Git** | 2.40+ | <https://git-scm.com/downloads> |
| **IDE** | IntelliJ IDEA 2023.3+ / VS Code 1.85+ | <https://www.jetbrains.com/idea/> / <https://code.visualstudio.com/> |

### 1.2 System Requirements

| Resource | Minimum | Recommended |
|----------|---------|-------------|
| **RAM** | 8 GB | 16 GB+ |
| **CPU** | 4 cores | 8+ cores |
| **Disk** | 20 GB free | 50 GB+ free |
| **OS** | Windows 10/11, macOS 13+, Linux | Any |

### 1.3 Verify Installation

```bash
docker --version
docker compose version
java -version
mvn -version
git --version
```

---

## 2. Quick Start

### 2.1 Clone Repository

```bash
git clone <repository-url>
cd CitiCoreProject
```

### 2.2 Configure Environment

```bash
# Copy template
cp .env.example .env.local

# Edit with your values (at minimum: JWT_SECRET, DB passwords)
# See Section 3 for details
```

### 2.3 Start Infrastructure

```bash
# Start core infrastructure first
docker compose up -d mysql redis kafka minio clamav mailpit eureka-server config-server

# Wait for health checks (30-60 seconds)
docker compose ps

# Verify all infrastructure healthy
docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}"
```

### 2.4 Start Microservices

```bash
# Option A: Start all services via Docker Compose
docker compose up -d auth-service user-service kyc-workflow-service account-service transaction-service notification-service api-gateway

# Option B: Run from IDE (recommended for development)
# See Section 11 for IDE configuration
```

### 2.5 Verify Deployment

```bash
# Check API Gateway health
curl http://localhost:8080/actuator/health

# Check individual services
curl http://localhost:8081/actuator/health  # Auth
curl http://localhost:8082/actuator/health  # User
curl http://localhost:8087/actuator/health  # KYC
curl http://localhost:8083/actuator/health  # Account
curl http://localhost:8084/actuator/health  # Transaction
curl http://localhost:8085/actuator/health  # Notification
curl http://localhost:8080/actuator/health  # Gateway

# Check Eureka dashboard
open http://localhost:8761

# Check Mailpit UI
open http://localhost:8025

# Check MinIO console
open http://localhost:9001
```

### 2.6 Run a Complete Workflow

```bash
# 1. Register a customer
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"dev@test.com","password":"SecurePass123!","phone":"+15551234567"}'

# 2. Verify email (check Mailpit at http://localhost:8025 for OTP)
curl -X POST http://localhost:8080/api/v1/auth/verify \
  -H "Content-Type: application/json" \
  -d '{"email":"dev@test.com","code":"123456"}'

# 3. Login
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"dev@test.com","password":"SecurePass123!"}' | jq -r .accessToken)

# 4. Complete profile
curl -X POST http://localhost:8080/api/v1/users/profile \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Dev","lastName":"User","phoneNumber":"+15551234567","address":"123 Dev St"}'

# 5. Upload KYC documents (use sample files in ./test-data/kyc/)
# 6. Submit KYC for review
# 7. As KYC Officer: review and recommend
# 8. As KYC Manager: approve
# 9. Create account
# 10. Transfer funds
```

---

## 3. Environment Configuration

### 3.1 .env.local Structure

Create `.env.local` from `.env.example`:

```bash
# ============================================================
# CitiCore Local Development Environment Configuration
# ============================================================
# THIS FILE IS GITIGNORED - NEVER COMMIT ACTUAL CREDENTIALS
# ============================================================

# ------------------------------------------------------------
# Database Configuration
# ------------------------------------------------------------
MYSQL_ROOT_PASSWORD=root
DB_USERNAME=citicore_user
DB_PASSWORD=changeme

# ------------------------------------------------------------
# Kafka Configuration
# ------------------------------------------------------------
KAFKA_BOOTSTRAP_SERVERS=kafka:9092

# ------------------------------------------------------------
# Redis Configuration
# ------------------------------------------------------------
REDIS_HOST=redis
REDIS_PORT=6379

# ------------------------------------------------------------
# MinIO (S3-Compatible) Configuration
# ------------------------------------------------------------
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=changeme
MINIO_ENDPOINT=http://minio:9000

# ------------------------------------------------------------
# JWT Configuration
# ------------------------------------------------------------
JWT_SECRET=local-dev-secret-minimum-32-characters-long-for-hs256

# ------------------------------------------------------------
# Email (Mailpit) Configuration
# ------------------------------------------------------------
MAIL_USERNAME=test
MAIL_PASSWORD=test
MAIL_HOST=mailpit
MAIL_PORT=1025

# ------------------------------------------------------------
# ClamAV Configuration
# ------------------------------------------------------------
CLAMAV_HOST=clamav
CLAMAV_PORT=3310

# ------------------------------------------------------------
# KYC Provider Configuration
# ------------------------------------------------------------
KYC_PROVIDER=mock

# ------------------------------------------------------------
# Observability (Optional)
# ------------------------------------------------------------
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
PROMETHEUS_ENDPOINT=http://prometheus:9090

# ------------------------------------------------------------
# Feature Flags
# ------------------------------------------------------------
FEATURE_KYC_MOCK_PROVIDER=true
FEATURE_MALWARE_SCANNING=true
FEATURE_DUAL_BALANCE=true
```

### 3.2 Docker Compose Override (Optional)

Create `docker-compose.override.yml` for personal customizations:

```yaml
version: '3.8'

services:
  # Override ports if conflicts exist
  mysql:
    ports:
      - "3307:3306"  # Use 3307 on host if 3306 occupied
  
  # Add resource limits for your machine
  auth-service:
    deploy:
      resources:
        limits:
          memory: 1G
        reservations:
          memory: 512M
  
  # Enable remote debugging
  auth-service:
    environment:
      JAVA_TOOL_OPTIONS: "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
    ports:
      - "5005:5005"
```

### 3.3 Profile-Specific Configuration

The application uses Spring profiles to select configuration:

| Profile | Config File | Active When |
|---------|-------------|-------------|
| `local` | `application-local.yml` | Docker Compose |
| `test` | `application-test.yml` | CI / Testcontainers |
| `staging` | `application-staging.yml` | AWS Staging |
| `prod` | `application-prod.yml` | AWS Production |

**Key Local Overrides (`application-local.yml`):**

```yaml
spring:
  profiles:
    active: local
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

# Local Kafka (PLAINTEXT)
spring:
  kafka:
    bootstrap-servers: kafka:9092
    security:
      protocol: PLAINTEXT
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

# Local MinIO
aws:
  s3:
    endpoint: http://minio:9000
    region: us-east-1
    path-style-access: true
    credentials:
      access-key: ${MINIO_ROOT_USER}
      secret-key: ${MINIO_ROOT_PASSWORD}

# Local Email (Mailpit)
spring:
  mail:
    host: mailpit
    port: 1025
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail:
        smtp:
          auth: false
          starttls:
            enable: false

# Local Database (no TLS)
spring:
  datasource:
    url: jdbc:mysql://mysql:3306/${spring.application.name}?useSSL=false&allowPublicKeyRetrieval=true
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

# Local Redis (no TLS)
spring:
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}
      ssl:
        enabled: false

# KYC Mock Provider
kyc:
  provider: mock
  mock:
    default-verdict: VERIFIED
    latency-ms: 100
```

---

## 4. Infrastructure Services

### 4.1 Service Endpoints

| Service | Internal URL | External URL | Purpose |
|---------|--------------|--------------|---------|
| **MySQL** | `mysql:3306` | `localhost:3306` | Primary database |
| **Redis** | `redis:6379` | `localhost:6379` | Caching & sessions |
| **Kafka** | `kafka:9092` | `localhost:29092` | Event streaming |
| **MinIO** | `minio:9000` | `localhost:9000` | Object storage |
| **MinIO Console** | `minio:9001` | `localhost:9001` | S3 management UI |
| **ClamAV** | `clamav:3310` | `localhost:3310` | Malware scanning |
| **Mailpit SMTP** | `mailpit:1025` | `localhost:1025` | Email capture |
| **Mailpit UI** | `mailpit:8025` | `localhost:8025` | Email viewing |
| **Eureka** | `eureka-server:8761` | `localhost:8761` | Service registry |
| **Config Server** | `config-server:8888` | `localhost:8888` | Centralized config |
| **Prometheus** | `prometheus:9090` | `localhost:9090` | Metrics |
| **Grafana** | `grafana:3000` | `localhost:3000` | Dashboards |
| **Jaeger** | `jaeger:16686` | `localhost:16686` | Tracing |
| **OTel Collector** | `otel-collector:4317` | `localhost:4317` | OTLP receiver |

### 4.2 Service Dependencies

```mermaid
graph TD
    MYSQL[(MySQL)] --> AUTH
    MYSQL --> USER
    MYSQL --> KYC
    MYSQL --> ACCOUNT
    MYSQL --> TXN
    
    REDIS[(Redis)] --> ACCOUNT
    REDIS --> GATEWAY
    
    KAFKA[Kafka] --> AUTH
    KAFKA --> USER
    KAFKA --> KYC
    KAFKA --> ACCOUNT
    KAFKA --> TXN
    KAFKA --> NOTIF
    
    MINIO[(MinIO)] --> USER
    MINIO --> KYC
    
    CLAMAV[ClamAV] --> USER
    
    MAILPIT[Mailpit] --> AUTH
    MAILPIT --> NOTIF
    
    EUREKA[Eureka] --> ALL_SERVICES[All Services]
    CONFIG[Config Server] --> ALL_SERVICES
    
    GATEWAY[API Gateway] --> AUTH
    GATEWAY --> USER
    GATEWAY --> KYC
    GATEWAY --> ACCOUNT
    GATEWAY --> TXN
    GATEWAY --> NOTIF
```

### 4.3 Health Check Commands

```bash
# Check all services
docker compose ps

# Check specific service health
docker compose exec mysql mysqladmin ping -h localhost -u root -p
docker compose exec redis redis-cli ping
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
docker compose exec minio mc alias list
curl -f http://localhost:8761/actuator/health
curl -f http://localhost:8888/actuator/health
curl -f http://localhost:8080/actuator/health
curl -f http://localhost:8081/actuator/health
curl -f http://localhost:8082/actuator/health
curl -f http://localhost:8087/actuator/health
curl -f http://localhost:8083/actuator/health
curl -f http://localhost:8084/actuator/health
curl -f http://localhost:8084/actuator/health
curl -f http://localhost:8085/actuator/health
```

---

## 5. Running Microservices

### 5.1 Via Docker Compose (All Services)

```bash
# Start all services
docker compose up -d

# Start specific services
docker compose up -d auth-service user-service kyc-workflow-service

# View logs
docker compose logs -f auth-service
docker compose logs -f --tail=100 user-service

# Restart a service
docker compose restart auth-service

# Rebuild and restart
docker compose up -d --build auth-service
```

### 5.2 Via IDE (Recommended for Development)

#### IntelliJ IDEA Setup

1. **Import Project**: File → Open → Select `pom.xml` → Open as Project
2. **Configure SDK**: File → Project Structure → SDK → Java 17
3. **Run Configurations**: Create Spring Boot run configurations for each service:

**Auth Service:**
```
Main class: com.citicore.auth.AuthApplication
VM options: -Dspring.profiles.active=local -Dspring.config.import=optional:configserver:http://localhost:8888
Environment variables: (from .env.local)
Working directory: $MODULE_WORKING_DIR$
Use classpath of module: auth-service
```

**Common VM Options for All Services:**
```
-Dspring.profiles.active=local
-Dspring.config.import=optional:configserver:http://localhost:8888
-Dspring.cloud.kubernetes.discovery.enabled=false
-Deureka.client.service-url.defaultZone=http://localhost:8761/eureka
```

**Module Dependencies:** Ensure each service module depends on `kafka-events` module.

#### VS Code Setup

1. **Extensions**: Extension Pack for Java, Spring Boot Extension Pack, Docker
2. **Launch Config** (`.vscode/launch.json`):

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Auth Service (Local)",
      "request": "launch",
      "mainClass": "com.citicore.auth.AuthApplication",
      "projectName": "auth-service",
      "envFile": "${workspaceFolder}/.env.local",
      "vmArgs": "-Dspring.profiles.active=local -Dspring.config.import=optional:configserver:http://localhost:8888"
    },
    {
      "type": "java",
      "name": "User Service (Local)",
      "request": "launch",
      "mainClass": "com.citicore.user.UserApplication",
      "projectName": "user-service",
      "envFile": "${workspaceFolder}/.env.local",
      "vmArgs": "-Dspring.profiles.active=local -Dspring.config.import=optional:configserver:http://localhost:8888"
    }
  ]
}
```

### 5.3 Hot Reloading

For faster development cycles:

```bash
# Option 1: Spring Boot DevTools (add to each service's pom.xml)
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <optional>true</optional>
</dependency>

# Option 2: JRebel (commercial)
# Option 3: Manual rebuild (fastest for Docker)
docker compose up -d --build auth-service
```

---

## 6. Development Workflows

### 6.1 Feature Branch Workflow

```bash
# 1. Create feature branch
git checkout main
git pull origin main
git checkout -b feature/KYC-123-add-document-validation

# 2. Make changes
# ... edit code ...

# 3. Run local tests
./mvnw test -pl auth-service -Dspring.profiles.active=local

# 4. Commit with conventional commits
git add .
git commit -m "feat(kyc): add document validation for PAN cards"

# 5. Push and create PR
git push origin feature/KYC-123-add-document-validation
# Create PR on GitHub
```

### 6.2 Database Changes

```bash
# 1. Create Flyway migration
# File: auth-service/src/main/resources/db/migration/V5__add_login_attempts.sql

# 2. Test locally
docker compose restart mysql
# Verify migration applied
docker compose exec mysql mysql -u root -p -e "SELECT * FROM flyway_schema_history;"

# 3. Reset database if needed
docker compose down -v
docker compose up -d mysql
```

### 6.3 Kafka Topic Management

```bash
# List topics
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

# Create topic manually
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --create --topic custom-topic \
  --partitions 3 --replication-factor 1

# View messages
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic kyc-case-topic \
  --from-beginning \
  --max-messages 10

# Produce test message
echo '{"eventId":"test","caseId":"KYC-001"}' | \
  docker compose exec -i kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic kyc-case-topic
```

### 6.4 Working with MinIO

```bash
# Access MinIO console
open http://localhost:9001
# Login: minioadmin / changeme

# Using mc CLI (if installed)
docker run --rm --network citicore-network \
  -e MC_HOST_local=http://minioadmin:changeme@minio:9000 \
  minio/mc ls local/citicore-kyc-quarantine

# Generate presigned URL for testing
docker run --rm --network citicore-network \
  -e MC_HOST_local=http://minioadmin:changeme@minio:9000 \
  minio/mc share download local/citicore-kyc-clean/test.pdf
```

---

## 7. Testing

### 7.1 Unit Tests

```bash
# Run all unit tests
./mvnw test

# Run for specific module
./mvnw test -pl auth-service

# Run with coverage
./mvnw test jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

### 7.2 Integration Tests

```bash
# Run integration tests (requires infrastructure running)
./mvnw verify -Dspring.profiles.active=local

# Run specific integration test
./mvnw test -pl auth-service -Dtest=AuthIntegrationTest
```

### 7.3 Contract Tests

```bash
# Run consumer-driven contract tests
./mvnw test -Dtest=*ContractTest
```

### 7.4 End-to-End Tests

```bash
# Run E2E tests against local stack
./mvnw test -Dtest=*E2ETest -Dspring.profiles.active=local

# Or use Postman/Newman
newman run postman/CitiCore-Local.postman_collection.json \
  -e postman/CitiCore-Local.postman_environment.json
```

---

## 8. Debugging

### 8.1 Remote Debugging

Each service exposes debug port when configured:

| Service | Debug Port |
|---------|------------|
| Auth Service | 5005 |
| User Service | 5006 |
| KYC Workflow | 5007 |
| Account Service | 5008 |
| Transaction Service | 5009 |
| Notification Service | 5010 |
| API Gateway | 5011 |

**IntelliJ Remote Debug Config:**
- Type: Remote JVM Debug
- Host: localhost
- Port: 5005 (for Auth Service)
- Use module classpath: auth-service

### 8.2 Log Analysis

```bash
# View service logs
docker compose logs -f auth-service --tail=100

# Search logs
docker compose logs auth-service 2>&1 | grep -i error

# Follow logs with timestamps
docker compose logs -f -t auth-service

# Export logs
docker compose logs auth-service > auth-service.log
```

### 8.3 Database Debugging

```bash
# Connect to MySQL
docker compose exec mysql mysql -u citicore_user -p citicore_authdb

# Common queries
SELECT * FROM users WHERE email = 'dev@test.com';
SELECT * FROM kyc_cases WHERE auth_user_id = 1;
SELECT * FROM accounts WHERE auth_user_id = 1;
SELECT * FROM transactions WHERE auth_user_id = 1 ORDER BY created_at DESC;

# Check Flyway migrations
SELECT * FROM flyway_schema_history ORDER BY installed_rank;
```

### 8.4 Kafka Debugging

```bash
# List consumer groups
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --list

# Describe consumer group
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group citicore-transaction-service --describe

# Check topic lag
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group citicore-transaction-service --describe \
  | grep -E "topic|lag"
```

### 8.5 Distributed Tracing

1. Open Jaeger UI: `http://localhost:16686`
2. Select service from dropdown
3. Click "Find Traces"
3. Click trace to see span details
4. Look for error spans (red)

### 8.6 Metrics & Dashboards

1. Open Grafana: `http://localhost:3000` (admin/admin)
2. Import CitiCore dashboards (JSON in `grafana/dashboards/`)
3. Key dashboards:
   - **System Overview**: Request rate, error rate, latency
   - **Service Deep Dive**: Per-service RED metrics
   - **KYC Workflow**: Case throughput, SLA compliance
   - **Saga Monitor**: Transfer success/failure rates
   - **JVM Metrics**: Memory, GC, threads

---

## 9. Troubleshooting

### 9.1 Common Issues

| Issue | Symptoms | Resolution |
|-------|----------|------------|
| **Service won't start** | Container exits immediately | Check logs: `docker compose logs <service>` |
| **Database connection failed** | `Communications link failure` | Verify MySQL healthy; check credentials in `.env.local` |
| **Kafka connection refused** | `Connection to localhost:9092 failed` | Use `kafka:9092` internally; `localhost:29092` externally |
| **Eureka registration fails** | Service not in Eureka dashboard | Check `eureka.client.service-url.defaultZone` |
| **Config Server not loading** | Properties not found | Verify `SPRING_CONFIG_IMPORT` and Config Server health |
| **Port already in use** | `BindException: Address already in use` | Change host port in `docker-compose.override.yml` |
| **Out of memory** | Container killed (OOM) | Increase Docker Desktop memory limit |
| **Kafka topics not created** | Topic not found | Enable `KAFKA_AUTO_CREATE_TOPICS_ENABLE=true` |
| **MinIO access denied** | 403 Forbidden | Check credentials in `.env.local` |
| **ClamAV scan fails** | Connection refused | Verify ClamAV healthy: `docker compose logs clamav` |

### 9.2 Service-Specific Issues

#### Auth Service
- **JWT_SECRET too short**: Must be ≥ 32 characters
- **BCrypt cost too high**: Reduce for local testing
- **MFA not working**: Check TOTP time sync

#### User Service
- **S3 upload fails**: Verify MinIO credentials and bucket existence
- **KYC submit fails**: Check Kafka connectivity and KYC Workflow Service health

#### KYC Workflow Service
- **Case assignment fails**: Verify Eureka registration and User Service connectivity
- **SoD violation**: Check `reviewingOfficerId` vs `decidingManagerId`

#### Account Service
- **Balance query slow**: Check Redis connectivity and cache hit rate
- **Transfer fails**: Verify KYC status via Feign call to KYC Workflow Service

#### Transaction Service
- **Saga stuck**: Check Kafka consumer lag; verify outbox publisher
- **Idempotency conflict**: Check `idempotency_keys` table

### 9.3 Reset Procedures

```bash
# Soft reset (keep volumes)
docker compose down
docker compose up -d

# Hard reset (destroy all data)
docker compose down -v
docker compose up -d

# Reset single service
docker compose rm -f auth-service
docker compose up -d auth-service

# Reset database only
docker compose down -v mysql
docker compose up -d mysql

# Reset Kafka topics
docker compose down -v kafka
docker compose up -d kafka

# Full nuclear reset
docker compose down -v
docker system prune -f
docker compose up -d
```

---

## 10. Reset Procedures

### 10.1 Complete Environment Reset

```bash
# 1. Stop all containers and remove volumes
docker compose down -v

# 2. Remove any orphaned networks
docker network prune -f

# 3. Start fresh
docker compose up -d

# 4. Verify all healthy
docker compose ps
```

### 10.2 Database-Only Reset

```bash
# Option 1: Full DB reset
docker compose down -v mysql
docker compose up -d mysql

# Option 2: Selective schema reset
docker compose exec mysql mysql -u root -p -e "
  DROP DATABASE IF EXISTS citicore_authdb;
  DROP DATABASE IF EXISTS citicore_kycdb;
  -- ... other schemas
  CREATE DATABASE citicore_authdb;
  CREATE DATABASE citicore_kycdb;
  -- ... other schemas
"

# Option 3: Run Flyway repair
docker compose exec auth-service java -jar /app/app.jar flyway:repair
```

### 10.3 Kafka Reset

```bash
# Full Kafka reset
docker compose down -v kafka
docker compose up -d kafka

# Wait for health
docker compose logs -f kafka
```

### 10.4 MinIO Reset

```bash
# Full MinIO reset (loses all uploaded files)
docker compose down -v minio
docker compose up -d minio
```

### 10.5 Observability Stack Reset

```bash
# Reset Prometheus/Grafana/Jaeger data
docker compose down -v prometheus grafana jaeger otel-collector loki
docker compose up -d prometheus grafana jaeger otel-collector loki
```

---

## 11. IDE Integration

### 11.1 IntelliJ IDEA - Recommended Plugins

| Plugin | Purpose |
|--------|---------|
| **Spring Boot** | Run/debug Spring Boot apps |
| **Docker** | Manage Docker containers |
| **Kubernetes** | View K8s resources (for staging/prod) |
| **Database Navigator** | Browse MySQL |
| **Kafka** | View topics/consumers |
| **GitToolBox** | Enhanced Git info |
| **Rainbow Brackets** | Bracket matching |
| **Key Promoter X** | Shortcut learning |

### 11.2 IntelliJ Run Configuration Template

Create a **Run Configuration Template** for Spring Boot services:

1. Run → Edit Configurations → + → Spring Boot
2. Name: `Local - <Service Name>`
3. Main class: `<Service>MainApplication`
4. Active profiles: `local`
4. VM options:
```
-Dspring.config.import=optional:configserver:http://localhost:8888
-Dspring.profiles.active=local
-Deureka.client.service-url.defaultZone=http://localhost:8761/eureka
-Dspring.cloud.kubernetes.discovery.enabled=false
```
5. Environment variables: Load from `.env.local`
6. Working directory: `$MODULE_WORKING_DIR$`
7. Use classpath of module: `<service-module>`
8. **Before launch**: Build module + Build dependent modules

### 11.3 Database Navigator Setup

1. View → Tool Windows → Database
2. + → Data Source → MySQL
6. Host: `localhost`, Port: `3306`
7. User: `citicore_user`, Password: from `.env.local`
8. Database: `citicore_authdb` (or any schema)
9. Test Connection → OK

### 11.4 Docker Tool Window

1. View → Tool Windows → Docker
2. Connect to Docker Desktop
3. Manage containers, images, volumes, networks
3. View logs, exec into containers

---

## 12. Common Commands Reference

### 12.1 Docker Compose

```bash
# Start/stop
docker compose up -d                    # Start all
docker compose down                     # Stop all
docker compose down -v                  # Stop + remove volumes
docker compose restart <service>        # Restart service
docker compose up -d --build <service>  # Rebuild and start

# Logs
docker compose logs -f                  # Follow all
docker compose logs -f <service>        # Follow specific
docker compose logs --tail=100 <service> # Last 100 lines
docker compose logs -t <service>        # With timestamps

# Status
docker compose ps                       # List containers
docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}"

# Exec
docker compose exec <service> bash      # Shell into container
docker compose exec mysql mysql -u root -p  # MySQL shell
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

# Build
docker compose build                    # Build all
docker compose build --no-cache <service> # Rebuild without cache
docker compose pull                     # Pull base images
```

### 12.2 Maven

```bash
# Build
./mvnw clean install                    # Full build
./mvnw clean install -DskipTests        # Skip tests
./mvnw clean install -pl auth-service   # Single module

# Test
./mvnw test                             # All unit tests
./mvnw test -pl auth-service            # Module tests
./mvnw test -Dtest=AuthControllerTest   # Specific test
./mvnw verify                           # Integration tests

# Coverage
./mvnw jacoco:report                    # Generate report
./mvnw jacoco:check                     # Enforce thresholds

# Dependency
./mvnw dependency:tree                  # Show tree
./mvnw dependency:analyze               # Unused/undeclared
./mvnw versions:display-plugin-updates  # Check updates

# Flyway
./mvnw flyway:migrate                   # Run migrations
./mvnw flyway:info                      # Migration status
./mvnw flyway:repair                    # Repair checksums
```

### 12.3 Kafka

```bash
# Topics
kafka-topics.sh --bootstrap-server localhost:9092 --list
kafka-topics.sh --bootstrap-server localhost:9092 --create --topic test --partitions 3 --replication-factor 1
kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic kyc-case-topic

# Producer
echo '{"key":"value"}' | kafka-console-producer.sh --bootstrap-server localhost:9092 --topic test-topic

# Consumer
kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic test-topic --from-beginning

# Consumer Groups
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group my-group --describe
```

### 12.4 Database

```bash
# Connect
mysql -h localhost -P 3306 -u citicore_user -p citicore_authdb

# Common queries
SHOW DATABASES;
USE citicore_authdb;
SHOW TABLES;
SELECT * FROM users WHERE email = 'test@test.com';
SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 10;
```

### 12.5 MinIO

```bash
# mc CLI (if installed locally)
mc alias set local http://localhost:9000 minioadmin changeme
mc ls local/citicore-kyc-quarantine
mc cp local/citicore-kyc-quarantine/test.pdf ~/Downloads/
mc share download local/citicore-kyc-clean/test.pdf
```

### 12.6 Observability

```bash
# Prometheus queries
# http://localhost:9090/graph
# up{job=~".*-service"}
# rate(http_server_requests_seconds_count[5m])
# histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))

# Grafana
# http://localhost:3000 (admin/admin)
# Dashboards: Import from grafana/dashboards/

# Jaeger
# http://localhost:16686
```

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-11-19 | Architecture Team | Initial Local Development Guide |

---

**END OF DOCUMENT**