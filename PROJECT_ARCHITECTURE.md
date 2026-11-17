# CitiCore Banking Platform - Project Architecture Documentation

## Overview

CitiCore is a microservices-based digital banking platform built with Spring Boot 3.2.4, Java 17, and Spring Cloud 2023.0.3. The platform implements core banking functionality including user authentication, KYC management, account operations, fund transfers, and notifications.

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              EXTERNAL CLIENTS                                        │
└──────────────────────────────────────┬──────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                          API GATEWAY (Port 8080)                                    │
│                    Spring Cloud Gateway + Resilience4j + Redis Rate Limiting        │
└──────────────────────────────────────┬──────────────────────────────────────────────┘
                                       │
         ┌─────────────────────────────┼─────────────────────────────┐
         ▼                             ▼                             ▼
┌──────────────────┐         ┌──────────────────┐         ┌──────────────────┐
│   AUTH SERVICE   │         │  USER SERVICE    │         │ ACCOUNT SERVICE  │
│   (Port 8081)    │         │  (Port 8082)     │         │  (Port 8083)     │
│                  │         │                  │         │                  │
│ • Registration   │         │ • Profile Mgmt   │         │ • Account CRUD   │
│ • Login/JWT      │         │ • KYC Documents  │         │ • Deposit/Withdraw│
│ • Refresh Tokens │         │ • KYC Review     │         │ • Balance Query  │
│ • Email Verify   │         │ • S3 Integration │         │ • Statements     │
└────────┬─────────┘         └────────┬─────────┘         └────────┬─────────┘
         │                            │                            │
         └────────────────────────────┼────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                      TRANSACTION SERVICE (Port 8084)                                │
│                    Fund Transfer Saga Orchestration                                 │
│                                                                                     │
│  1. Initiate Transfer → Save Transaction(INITIATED) + Outbox Event                 │
│  2. DebitConsumer → Debit Sender → Save DebitSuccessEvent → Outbox                 │
│  3. CreditConsumer → Credit Receiver → Save CreditSuccessEvent → Outbox            │
│  4. TransactionEventConsumer → Mark CREDIT_SUCCESS                                 │
│  5. On Credit Failure → ReversalConsumer → Refund Sender → Mark REVERSED           │
└──────────────────────────────────────┬──────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                      NOTIFICATION SERVICE (Port 8085)                               │
│                    Email Notifications via Kafka Consumers                          │
└─────────────────────────────────────────────────────────────────────────────────────┘
                                       │
         ┌─────────────────────────────┼─────────────────────────────┐
         ▼                             ▼                             ▼
┌──────────────────┐         ┌──────────────────┐         ┌──────────────────┐
│   MYSQL (Auth)   │         │  MYSQL (Account) │         │  MYSQL (Txn)     │
│  citicore_authdb │         │ citicore_account │         │ citicore_txndb   │
│                  │         │  Primary/Replica │         │                  │
└──────────────────┘         └──────────────────┘         └──────────────────┘
         │                             │                             │
         └─────────────────────────────┼─────────────────────────────┘
                                       │
                         ┌─────────────┴─────────────┐
                         ▼                           ▼
                 ┌─────────────┐             ┌─────────────┐
                 │    REDIS    │             │    KAFKA    │
                 │  (Port 6379)│             │ (Port 29092)│
                 └─────────────┘             └─────────────┘
```

---

## Microservices Detail

### 1. Auth Service (auth-service)
**Port:** 8081 | **Database:** citicore_authdb

**Responsibilities:**
- User registration with email verification
- JWT access token + refresh token authentication
- Refresh token rotation (revoke old, issue new)
- Logout (revoke refresh token)
- Password hashing with BCrypt
- OTP publication to Kafka for email delivery

**Key Entities:**
- `User` - id, email, password, emailVerified, status, roles
- `RefreshToken` - token, user, expiryDate, revoked
- `Role` - name (ROLE_CUSTOMER, ROLE_ADMIN)

**Kafka Events Published:**
- `otp-verification-topic` → VerificationOtpEvent

**Security:**
- Stateless JWT authentication
- Custom authentication entry point
- BCrypt password encoding
- Token validation with signature verification

---

### 2. User Service (user-service)
**Port:** 8082 | **Database:** citicore_userdb (shared with auth)

**Responsibilities:**
- User profile management (CRUD)
- KYC document upload to AWS S3
- KYC document metadata storage
- KYC status management (PENDING → UNDER_REVIEW → APPROVED/REJECTED)
- Admin KYC review workflow
- KYC event publication for notifications

**Key Entities:**
- `UserProfile` - authUserId, firstName, lastName, email, phone, address, kycStatus
- `KycDocument` - authUserId, documentType, s3Key, fileName, contentType, fileSize

**Feign Client Exposes:**
- `GET /api/v1/users/kyc-status/{userId}` → boolean (for account-service)

**Kafka Events Published:**
- `kyc-topic` → KycEvent

**AWS Integration:**
- S3 for document storage
- Google Cloud Vision for OCR (optional)

---

### 3. Account Service (account-service)
**Port:** 8083 | **Database:** citicore_account (Primary/Replica)

**Responsibilities:**
- Account creation (SAVINGS/CURRENT) with KYC validation
- Deposit/Withdraw operations
- Balance queries (Redis cache-aside pattern)
- Account statements & mini-statements
- Transfer validation (pre-flight check for saga)
- Transactional outbox for reliable event publishing
- Pessimistic locking for concurrent debit/credit safety

**Key Entities:**
- `Account` - accountNumber, authUserId, accountType, balance, status, isDefault
- `AccountStatement` - accountNumber, transactionType, amount, balanceAfterTxn, txnRef, description
- `AccountOutboxEvent` - eventId, accountNumber, topic, payload, status
- `AccountSequence` - nextVal for account number generation

**Database Routing:**
- **Primary** - All writes (create, debit, credit, deposit, withdraw)
- **Replica** - Reads (getBalance, getMiniStatement, getStatement, validateTransfer, getMyAccounts)
- **Redis Cache** - Balance queries (5-minute TTL)

**Kafka Consumers (Saga Participants):**
- `transfer-requested-topic` → DebitConsumer
- `debit-success-topic` → CreditConsumer
- `reversal-topic` → ReversalConsumer

**Kafka Events Published (via Outbox):**
- `account-events-topic` → AccountEvent (ACCOUNT_CREATED, ACCOUNT_DEBITED, ACCOUNT_CREDITED)
- `debit-success-topic` → DebitSuccessEvent
- `debit-failed-topic` → DebitFailedEvent
- `credit-success-topic` → CreditSuccessEvent
- `credit-failed-topic` → CreditFailedEvent
- `reversal-success-topic` → ReversalSuccessEvent

**Feign Client:**
- `UserClient` → user-service KYC status check

---

### 4. Transaction Service (transaction-service)
**Port:** 8084 | **Database:** citicore_transactiondb

**Responsibilities:**
- Fund transfer initiation (saga orchestrator)
- Daily transaction limit validation
- Idempotency key management
- Transaction history & status queries
- Transactional outbox for saga initiation

**Key Entities:**
- `Transaction` - txnRef, authUserId, fromAccount, toAccount, amount, type, status
- `OutboxEvent` - eventId, aggregateId, topic, payload, status
- `IdempotencyKey` - idempotencyKey, txnRef, status

**Saga Flow:**
```
1. Client POST /transfer with Idempotency-Key
2. Validate request + daily limit + Feign pre-validation (account-service)
3. Save Transaction(INITIATED) + OutboxEvent(transfer-requested-topic) ATOMIC
4. OutboxPublisher → Kafka: transfer-requested-topic
5. DebitConsumer (account-service) → debit sender
   ├─ Success → debit-success-topic → CreditConsumer
   │              └─ Success → credit-success-topic → TransactionEventConsumer → CREDIT_SUCCESS
   │              └─ Failure → credit-failed-topic → TransactionFailureConsumer → FAILED + reversal-topic
   └─ Failure → debit-failed-topic → TransactionFailureConsumer → FAILED
6. ReversalConsumer → credit sender → reversal-success-topic → REVERSED
```

**Feign Client:**
- `AccountFeignClient` → account-service validateTransfer

**Kafka Consumers:**
- `credit-success-topic` → TransactionEventConsumer (mark CREDIT_SUCCESS)
- `credit-failed-topic` → TransactionFailureConsumer (mark FAILED, trigger reversal)
- `reversal-success-topic` → TransactionEventConsumer (mark REVERSED)

---

### 5. Notification Service (notification-service)
**Port:** 8085 | **No Database**

**Responsibilities:**
- Email delivery via Gmail SMTP / SendGrid
- HTML email templates for transactions, KYC, OTP
- Consumes events from all services

**Kafka Consumers:**
- `credit-success-topic` → Transfer success email
- `credit-failed-topic` → Transfer failed email
- `reversal-success-topic` → Reversal/refund email
- `kyc-topic` → KYC status change email
- `otp-verification-topic` → OTP email

---

### 6. API Gateway (apigateway-service)
**Port:** 8080

**Features:**
- Spring Cloud Gateway reactive routing
- Eureka service discovery integration
- Resilience4j circuit breakers
- Redis-based rate limiting (IP-based KeyResolver)
- JWT authentication propagation
- Path-based routing to microservices

---

### 7. Config Server (config-service)
**Port:** 8888

**Configuration:**
- Native profile (local file system: config-repo-local)
- Spring Cloud Bus Kafka for config refresh
- Shared configuration for all services

---

### 8. Eureka Server (eureka-server)
**Port:** 8761

**Service Registry:** All microservices register with Eureka for discovery.

---

### 9. Kafka Events Library (kafka-events)
**Shared DTOs for inter-service communication:**

**Account Events:**
- `AccountEvent` / `AccountEventType` (ACCOUNT_CREATED, ACCOUNT_DEBITED, ACCOUNT_CREDITED)

**Transaction Events:**
- `TransactionRequestedEvent` - saga initiation
- `DebitSuccessEvent` / `DebitFailedEvent` - step 1 result
- `CreditSuccessEvent` / `CreditFailedEvent` - step 2 result
- `ReversalSuccessEvent` - compensating transaction result

**KYC Events:**
- `KycEvent` / `KycEventType` (KYC_SUBMITTED, KYC_APPROVED, KYC_REJECTED)

**OTP Events:**
- `VerificationOtpEvent` - email verification codes

---

## Data Flow: Fund Transfer (Complete Saga)

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐     ┌──────────────────┐
│   CLIENT    │────▶│ API GATEWAY  │────▶│TXN SERVICE  │────▶│ ACCOUNT SERVICE  │
└─────────────┘     └──────────────┘     └──────┬──────┘     │  (DebitConsumer) │
                                                │            └────────┬─────────┘
                                                │                     │
                                                ▼                     ▼
                                         ┌──────────────┐     ┌─────────────┐
                                         │   KAFKA      │     │  MYSQL      │
                                         │transfer-req  │     │ (Primary)   │
                                         └──────┬───────┘     └──────┬──────┘
                                                │                    │
                                                ▼                    ▼
                                         ┌──────────────┐     ┌─────────────┐
                                         │  ACCOUNT     │     │  DEBIT      │
                                         │  SERVICE     │     │  SUCCESS    │
                                         │(CreditCons)  │     │  EVENT      │
                                         └──────┬───────┘     └──────┬──────┘
                                                │                    │
                                                ▼                    ▼
                                         ┌──────────────┐     ┌─────────────┐
                                         │   MYSQL      │     │   KAFKA     │
                                         │  (Primary)   │     │credit-success│
                                         └──────┬───────┘     └──────┬──────┘
                                                │                    │
                                                ▼                    ▼
                                         ┌──────────────┐     ┌──────────────────┐
                                         │  ACCOUNT     │     │ TXN SERVICE      │
                                         │  OUTBOX      │     │(TxnEventConsumer)│
                                         └──────┬───────┘     │  Mark            │
                                                │            │  CREDIT_SUCCESS  │
                                                ▼            └────────┬─────────┘
                                         ┌──────────────┐            │
                                         │   KAFKA      │            ▼
                                         │credit-success│     ┌──────────────┐
                                         └──────┬───────┘     │ NOTIFICATION │
                                                │            │  SERVICE     │
                                                ▼            │  (Email)     │
                                         ┌──────────────┐     └──────────────┘
```

---

## Database Schema Summary

### citicore_authdb
- `users` - id, email, password, email_verified, status, verification_code, verification_expiration
- `roles` - id, name
- `user_roles` - user_id, role_id
- `refresh_tokens` - id, token, user_id, revoked, expiry_date

### citicore_account (Primary/Replica)
- `accounts` - id, account_number, auth_user_id, account_type, balance, status, nominee_name, is_default, created_at, updated_at
- `account_statements` - id, account_number, transaction_type, amount, balance_after_txn, txn_ref, description, created_at
- `account_outbox` - id, event_id, account_number, topic, payload, status, created_at
- `account_sequences` - id, next_val

### citicore_transactiondb
- `transactions` - id, txn_ref, auth_user_id, from_account, to_account, amount, type, status, created_at
- `outbox_events` - id, event_id, aggregate_id, topic, payload, status, created_at
- `idempotency_keys` - id, idempotency_key, txn_ref, status, created_at

### citicore_userdb
- `user_profiles` - id, auth_user_id, first_name, last_name, email, phone_number, date_of_birth, address, city, state, pincode, kyc_status
- `kyc_documents` - id, auth_user_id, document_type, s3_key, file_name, content_type, file_size, created_at

---

## Security Architecture

### JWT Token Flow
1. **Login** → Auth Service validates credentials → Generates access token (15 min) + refresh token (7 days)
2. **Access Token** → Contains: userId, email, roles | Signed with HS256
3. **Request** → Client sends `Authorization: Bearer <access_token>`
4. **Gateway** → Validates JWT, extracts claims, forwards user context
5. **Services** → JwtAuthenticationFilter validates token, sets SecurityContext

### Refresh Token Rotation
- Refresh tokens stored in DB with expiry and revoked flag
- On refresh: old token revoked, new token issued
- Logout: token marked revoked
- Tokens are single-use (rotation prevents replay attacks)

### Service-to-Service Communication
- Feign clients propagate JWT via request interceptors
- Each service validates JWT independently
- No shared secret between services (each has own JwtUtils)

---

## Resilience Patterns

| Pattern | Implementation |
|---------|----------------|
| Circuit Breaker | Resilience4j on Feign clients (API Gateway) |
| Retry | Spring Retry on Feign (3 attempts, exponential backoff) |
| Timeout | Feign connect: 3s, read: 5s |
| Rate Limiting | Redis Token Bucket (API Gateway) |
| Bulkhead | Not implemented |
| Saga | Choreography-based with Kafka + Outbox |
| Outbox | Transactional outbox with polling publisher (5s) |
| Idempotency | Idempotency keys + txnRef deduplication in statements |

---

## Monitoring & Observability

- **Actuator** endpoints: health, info, metrics, prometheus, refresh
- **Micrometer** + Prometheus metrics export
- **Zipkin** distributed tracing (Brave bridge)
- **Logback** structured logging with correlation IDs (partial)

---

## Deployment Architecture (Docker Compose)

```yaml
Services:
  - mysql:8.0 (port 3306)
  - redis:7-alpine (port 6379)
  - kafka:3.8.0 (port 29092 external, 9092 internal)
  - eureka-server (port 8761)
  - config-server (port 8888)
  - auth-service (port 8081)
  - user-service (port 8082)
  - account-service (port 8083)
  - transaction-service (port 8084)
  - notification-service (port 8085)
  - api-gateway (port 8080)
```

---

## Technology Stack

| Layer | Technology |
|-------|------------|
| Language | Java 17 |
| Framework | Spring Boot 3.2.4 |
| Cloud | Spring Cloud 2023.0.3 |
| Security | Spring Security, JWT (jjwt 0.12.5) |
| Data | Spring Data JPA, Hibernate 6.4.5, MySQL 8.0 |
| Cache | Redis 7, Spring Data Redis |
| Messaging | Apache Kafka 3.8, Spring Kafka |
| Service Discovery | Netflix Eureka |
| Config | Spring Cloud Config |
| Gateway | Spring Cloud Gateway (WebFlux) |
| Resilience | Resilience4j |
| HTTP Client | OpenFeign |
| Observability | Micrometer, Prometheus, Zipkin (Brave) |
| Build | Maven |
| Container | Docker, Docker Compose |
| Email | Spring Mail, SendGrid |
| Cloud Storage | AWS S3 SDK v2 |
| OCR | Google Cloud Vision |

---

## Known Architectural Decisions

### 1. Primary/Replica Routing (Account Service)
- Custom `RoutingDataSource` with AOP-based `@ReadOnly` / `@PrimaryRead` annotations
- Reads default to replica; balance queries force primary for consistency
- Redis cache sits in front of balance queries

### 2. Transactional Outbox Pattern
- Events saved in same DB transaction as business operation
- Polling publisher (5s interval) sends to Kafka
- StringSerializer avoids double-JSON-encoding

### 3. Saga Choreography (Not Orchestration)
- No central saga orchestrator
- Each step publishes event → next consumer reacts
- Compensating transactions for failures

### 4. Pessimistic Locking for Account Balance
- `SELECT ... FOR UPDATE` via `@Lock(PESSIMISTIC_WRITE)`
- Applied in both debit() and credit() for consistency

### 5. Idempotency at Multiple Levels
- Client-provided Idempotency-Key (transaction-service)
- txnRef suffix deduplication in account statements (account-service)
- Kafka consumer group stability + status checks

---

## Configuration Management

All services use Spring Cloud Config with:
- **Local:** `config-repo-local/` (file-based)
- **Production:** Git/AWS backend (config-repo/)

Environment-specific files:
- `application.yml` - shared defaults
- `{service}.yml` - service-specific
- `application-local.yml` / `application-prod.yml` - per-service overrides