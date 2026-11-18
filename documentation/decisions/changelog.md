# CitiCore Changelog

All notable changes to the CitiCore Banking Platform architecture and implementation.

## [3.0.0] - 2026-11-18 - Architecture Approved

### Major Architecture Updates

#### Approved Architecture Decision Records (8 new ADRs)
All 8 previously pending ADRs have been approved:

- **DEC-001**: Database Topology - Single AWS RDS with separate schemas per microservice
- **DEC-002**: Saga Pattern - Hybrid orchestration (transfers) / choreography (notifications)
- **DEC-010**: Document Malware Scanning - ClamAV via S3 Object Lambda with abstraction
- **DEC-011**: MySQL Encryption - RDS KMS storage encryption + selective app-level envelope encryption
- **DEC-012**: Secrets Management - AWS Secrets Manager with IRSA
- **DEC-013**: Database TLS - Mandatory TLS 1.2+ for RDS with certificate validation
- **DEC-014**: Service Discovery - Kubernetes DNS (production) / Eureka (local only)
- **DEC-015**: KYC Vendor Integration - Internal workflow with mock provider initially

#### Previously Approved (Preserved)
- **DEC-003**: KYC Service Ownership → Dedicated KYC Workflow Service ✅
- **DEC-004**: Case Assignment → Hybrid auto-assign with manager override ✅
- **DEC-005**: SoD Enforcement Location → KYC Workflow Service + Gateway ✅
- **DEC-006**: JWT Claims Size → Permissions in JWT (15min expiry) ✅
- **DEC-007**: Ledger Service → Separate microservice ✅
- **DEC-008**: Compliance Service → Build abstraction, integrate vendor APIs ✅
- **DEC-009**: MFA Methods → TOTP primary, Email OTP fallback ✅

### Architecture Updates (v3.0)

#### Core Architecture
- **Schema per Service**: Each microservice owns its schema within a single RDS MySQL deployment
- **Hybrid Saga Pattern**: Orchestration for fund transfers; Choreography for notifications
- **KYC Workflow Service**: New dedicated microservice (Port 8087) for maker-checker workflow
- **Hybrid Saga**: Transaction Service orchestrates fund transfers; Notifications use choreography

#### Security Enhancements
- **Comprehensive RBAC**: 9 roles, 28 granular permissions
- **Separation of Duties**: Enforced at KYC Workflow Service + Gateway
- **JWT Claims**: Include permissions, case assignments (15min expiry)
- **Encryption**: RDS KMS storage + selective app-level envelope encryption for PII
- **TLS**: Mandatory TLS 1.2+ for all production RDS connections
- **Secrets**: AWS Secrets Manager with IRSA (no hardcoded secrets)

#### KYC Workflow
- **13-State Lifecycle**: DRAFT → SUBMITTED → PENDING_REVIEW → UNDER_REVIEW → MORE_INFO_REQUIRED → RESUBMITTED → RECOMMENDED_APPROVAL/REJECTION → PENDING_MANAGER_APPROVAL → APPROVED/REJECTED/ESCALATED
- **Maker-Checker**: Officer reviews/recommends; Manager approves/rejects; Officer ≠ Manager enforced
- **Mock KYC Provider**: Deterministic mock for testing; pluggable adapter for future vendors
- **Malware Scanning**: ClamAV via S3 Object Lambda; quarantine → scan → clean bucket; fail-closed

#### Infrastructure
- **Database**: Single AWS RDS MySQL 8.0 with 7 schemas, separate credentials per service
- **Service Discovery**: K8s DNS (CoreDNS) for EKS production; Eureka for local Docker Compose only
- **Messaging**: MSK Kafka (3 brokers, 3 AZ, RF=3, IAM auth)
- **Caching**: ElastiCache Redis Cluster (3 shards, Multi-AZ)
- **Storage**: S3 with SSE-KMS, Object Lock (Compliance mode, 7-year retention)
- **Secrets**: AWS Secrets Manager with IRSA, automatic rotation (90-day DB, 180-day JWT)

### Resolved Conflicts (10)

| Conflict | Resolution |
|----------|------------|
| User Service SecurityConfig references `/accounts/**` | Fixed to `/users/**` patterns |
| Account Service permits all requests | Fixed: require authentication + RBAC |
| User Service permits all requests | Fixed: require authentication + RBAC |
| Saga Choreography vs Orchestration | Clarified: Hybrid - Orchestration for transfers, Choreography for notifications |
| Cross-DB Referential Integrity | Documented: App-level validation + reconciliation jobs |
| Idempotency Key Not User-Scoped | Fixed: composite unique (auth_user_id, idempotency_key) |
| Account Statement txn_ref Global Unique | Fixed: composite unique (account_number, txn_ref) |
| KYC Review in User Service | Moved to dedicated KYC Workflow Service |
| Single KYC Status Enum | Replaced with full 13-state KYC Case state machine |
| No Separation of Duties | Enforced: Officer ≠ Manager for same case |

---

## [2.0.0] - 2026-11-18 - RBAC & KYC Workflow Architecture

### Major Architecture Additions

#### RBAC Architecture (v2.0)
- **9 System Roles**: CUSTOMER, KYC_OFFICER, KYC_MANAGER, SUPPORT_AGENT, OPERATIONS_OFFICER, COMPLIANCE_OFFICER, ADMIN, AUDITOR, SERVICE
- **28 Granular Permissions**: Resource-action-scope model
- **Complete Role-Permission Matrix**: 9×28 explicit mapping
- **5-Layer Enforcement**: Gateway → Filter → @PreAuthorize → Service Logic → Data Layer
- **JWT Claims**: Roles + permissions + case assignments in 15min token
- **Revocation Strategy**: ≤15min latency (JWT expiry + Redis eviction + refresh revocation)
- **Admin Controls**: MFA required, dual approval for critical actions, break-glass procedure
- **Audit Logging**: Immutable hash-chained audit trail

#### KYC Maker-Checker Workflow (v2.0)
- **13-State Lifecycle**: DRAFT → SUBMITTED → PENDING_REVIEW → UNDER_REVIEW → MORE_INFO_REQUIRED → RESUBMITTED → RECOMMENDED_APPROVAL/REJECTION → PENDING_MANAGER_APPROVAL → APPROVED/REJECTED/ESCALATED
- **Maker-Checker Enforcement**: Officer reviews/recommends; Manager approves/rejects; Officer ≠ Manager enforced
- **KYC Workflow Service**: New microservice (Port 8087) owning case lifecycle
- **Case Assignment**: Hybrid auto-assign (round-robin) with manager override
- **SLA Monitoring**: Per-stage deadlines (1hr assign, 24hr review, 48hr decision)
- **Audit Trail**: Immutable hash-chained per transition
- **Document Security**: Pre-signed URLs (15min), SSE-KMS, ClamAV scanning

### Documentation Updates
- **CITICORE_TARGET_STATE_ARCHITECTURE.md**: v2.0 - Major rewrite with RBAC + KYC
- **RBAC_ARCHITECTURE.md**: v2.0 - Complete RBAC specification
- **KYC_ARCHITECTURE.md**: v2.0 - Complete KYC specification
- **ARCHITECTURE_DIAGRAMS.md**: v2.0 - 13 new/updated Mermaid diagrams
- **ARCHITECTURE_REVIEW_OBSERVATIONS.md**: Updated with resolved conflicts
- **CHANGE_SUMMARY.md**: Created for stakeholder review

---

## [1.0.0] - 2026-11-17 - Initial Architecture Documentation

### Initial Documentation Set Created

#### Core Architecture Documents
- **CITICORE_TARGET_STATE_ARCHITECTURE.md** v1.0 - Target state architecture
- **PROJECT_ARCHITECTURE.md** v1.0 - Original system architecture
- **ARCHITECTURE_REVIEW_OBSERVATIONS.md** v1.0 - 35 observations for review
- **ARCHITECTURE_DIAGRAMS.md** v1.0 - 13 Mermaid diagrams
- **DELIVERABLES_INDEX.md** v1.0 - Documentation index

#### Audit & Analysis Documents
- **SECURITY_AUDIT.md** - 32 security findings (6 Critical, 13 High, 9 Medium, 4 Low)
- **DATABASE_AUDIT.md** - Database design & consistency audit
- **BANKING_FUNCTIONALITY_GAP_ANALYSIS.md** - ~35% functional coverage
- **PRODUCTION_READINESS.md** - 45% production ready, 45 gaps
- **BUG_AUDIT_REPORT.md** - 47 confirmed bugs
- **BANKING_FUNCTIONALITY_GAP_ANALYSIS.md** - Functional coverage matrix

#### Implementation Planning
- **IMPLEMENTATION_ROADMAP.md** - 20-week phased plan (5-7 engineers)
- **TESTING_STRATEGY.md** - Testing approach
- **PRODUCTION_READINESS.md** - Production gaps assessment

#### Approved ADRs (Initial 7)
- **DEC-003**: KYC Service Ownership → Dedicated KYC Workflow Service ✅
- **DEC-004**: Case Assignment → Hybrid auto-assign with manager override ✅
- **DEC-005**: SoD Enforcement Location → KYC Workflow Service + Gateway ✅
- **DEC-006**: JWT Claims Size → Permissions in JWT (15min expiry) ✅
- **DEC-007**: Ledger Service → Separate microservice ✅
- **DEC-008**: Compliance Service → Build abstraction, integrate vendor APIs ✅
- **DEC-009**: MFA Methods → TOTP primary, Email OTP fallback ✅

### Architecture Baseline (v1.0)

#### System Architecture
- **7 Microservices**: Auth, User, Account, Transaction, Notification, Config, Eureka
- **API Gateway**: Spring Cloud Gateway + Resilience4j + Redis Rate Limiting
- **Service Discovery**: Netflix Eureka (all environments)
- **Config**: Spring Cloud Config (native profile)
- **Messaging**: Apache Kafka 3.8 (choreography-based saga)
- **Database**: MySQL 8.0 per service (separate databases)
- **Cache**: Redis 7 (balance cache, 5min TTL)
- **Storage**: AWS S3 for KYC documents
- **Observability**: Micrometer, Prometheus, Zipkin, Actuator

#### Security (v1.0)
- JWT Authentication (HS256, 15min access, 7day refresh)
- Refresh Token Rotation
- BCrypt Password Hashing
- Roles: CUSTOMER, ADMIN, SUPPORT, AUDITOR, SERVICE

#### Data Flow
- **Fund Transfer Saga**: Choreography-based with Kafka + Outbox
- **KYC Flow**: User Service → Admin review → Approval
- **Notifications**: Event-driven via Kafka

---

## Version History Summary

| Version | Date | Major Changes |
|---------|------|---------------|
| 3.0.0 | 2026-11-18 | All 8 pending ADRs approved; Architecture v3.0 approved; Hybrid saga; KYC vendor mock; RDS KMS + app-level encryption; Mandatory TLS; K8s DNS prod; ClamAV malware scanning |
| 2.0.0 | 2026-11-18 | RBAC (9 roles, 28 perms); KYC Workflow Service; 13-state KYC; Maker-Checker; SoD enforcement; ADRs 3-9 approved |
| 1.0.0 | 2026-11-17 | Initial architecture documentation; 7 ADRs approved; 35 observations documented |

---

*Last Updated: 2026-11-18 | Current Version: 3.0.0 (Approved)*