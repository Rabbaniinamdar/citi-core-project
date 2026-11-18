# CitiCore Implementation Inventory

## Feature Status Inventory

This document tracks the implementation status of all major features across the CitiCore Banking Platform.

### Status Definitions

| Status | Description |
|--------|-------------|
| **COMPLETE_AND_VERIFIED** | Fully implemented, tested, and verified in production-like environment |
| **IMPLEMENTED_BUT_NEEDS_IMPROVEMENT** | Basic implementation exists but needs hardening, optimization, or additional features |
| **PARTIALLY_IMPLEMENTED** | Core structure exists but significant functionality missing |
| **NOT_IMPLEMENTED** | Not yet started |
| **ARCHITECTURALLY_INCORRECT** | Implementation exists but violates architectural principles |
| **BLOCKED_BY_DEPENDENCY** | Cannot proceed until dependency is resolved |

---

## Feature Inventory by Domain

### 1. Authentication & Authorization

| Feature | Status | Service | Notes |
|---------|--------|---------|-------|
| User Registration | COMPLETE_AND_VERIFIED | auth-service | Email verification via OTP |
| User Login (JWT) | COMPLETE_AND_VERIFIED | auth-service | HS256, 15min access / 7d refresh |
| Refresh Token Rotation | COMPLETE_AND_VERIFIED | auth-service | Revoke old, issue new |
| Email Verification (OTP) | COMPLETE_AND_VERIFIED | auth-service | 8-char alphanumeric, 10min expiry |
| Password Reset | NOT_IMPLEMENTED | auth-service | P1-03 - Sprint 1 |
| Password Change | NOT_IMPLEMENTED | auth-service | P1-10 - Sprint 1 |
| Account Lockout | NOT_IMPLEMENTED | auth-service | P1-01 - Sprint 1 |
| Password Policy | NOT_IMPLEMENTED | auth-service | P1-02 - Sprint 1 |
| TOTP MFA | NOT_IMPLEMENTED | auth-service | P1-04 - Sprint 1 |
| JWT Claims (roles, permissions, cases) | PARTIALLY_IMPLEMENTED | auth-service | Claims structure defined, needs implementation |
| JWT Secret Validation | NOT_IMPLEMENTED | auth-service | P0-05, P0-07 |
| Refresh Token Revocation (logout) | COMPLETE_AND_VERIFIED | auth-service | Single-use rotation |

### 2. User Profile & KYC

| Feature | Status | Service | Notes |
|---------|--------|---------|-------|
| User Profile CRUD | COMPLETE_AND_VERIFIED | user-service | Profile management |
| KYC Document Upload (S3) | COMPLETE_AND_VERIFIED | user-service | S3 integration, metadata storage |
| KYC Status Workflow | PARTIALLY_IMPLEMENTED | user-service | Simple enum, needs migration to KYC Workflow Service |
| KYC Document OCR | NOT_IMPLEMENTED | user-service | Google Cloud Vision integration planned |
| KYC Document Malware Scanning | NOT_IMPLEMENTED | user-service | DEC-010 - Sprint 3/5 |
| KYC Workflow Service | NOT_IMPLEMENTED | kyc-workflow-service | DEC-003 - Sprint 2+ |
| KYC Maker-Checker Workflow | NOT_IMPLEMENTED | kyc-workflow-service | DEC-003, DEC-005 - Sprint 2+ |
| KYC Case Assignment | NOT_IMPLEMENTED | kyc-workflow-service | DEC-004 - Sprint 3 |
| KYC Case State Machine (13 states) | NOT_IMPLEMENTED | kyc-workflow-service | DEC-003 - Sprint 2+ |
| KYC SoD Enforcement | NOT_IMPLEMENTED | kyc-workflow-service | DEC-005 - Sprint 1/2 |
| KYC SLA Monitoring | NOT_IMPLEMENTED | kyc-workflow-service | Sprint 2+ |
| KYC Mock Provider | NOT_IMPLEMENTED | kyc-workflow-service | DEC-015 - Sprint 2+ |
| KYC Vendor Abstraction | NOT_IMPLEMENTED | kyc-workflow-service | DEC-015 - Phase 5 |

### 3. Account Management

| Feature | Status | Service | Notes |
|---------|--------|---------|-------|
| Account Creation (SAVINGS/CURRENT) | COMPLETE_AND_VERIFIED | account-service | KYC validation via Feign |
| Account CRUD | COMPLETE_AND_VERIFIED | account-service | Full CRUD |
| Deposit/Withdraw | COMPLETE_AND_VERIFIED | account-service | Pessimistic locking |
| Balance Queries (Redis cache) | COMPLETE_AND_VERIFIED | account-service | 5min TTL, cache-aside |
| Account Statements | COMPLETE_AND_VERIFIED | account-service | Pagination, mini-statement |
| Transfer Validation (Feign) | COMPLETE_AND_VERIFIED | account-service | Pre-flight for saga |
| Dual Balance (Available/Ledger) | NOT_IMPLEMENTED | account-service | P2-12 - Sprint 4 |
| Auth Holds | PARTIALLY_IMPLEMENTED | account-service | Placeholder entity exists |
| Interest Accrual | NOT_IMPLEMENTED | account-service | P2-13 - Sprint 4 |
| PDF Statements | NOT_IMPLEMENTED | account-service | P2-14 - Sprint 4 |
| Account Closure | NOT_IMPLEMENTED | account-service | P2-11 - Sprint 4 |
| Nominee Management | NOT_IMPLEMENTED | account-service | P2-15 - Sprint 4 |
| Account Tiers/Pricing | NOT_IMPLEMENTED | account-service | P2-16 - Sprint 4 |
| Account Number Generation | PARTIALLY_IMPLEMENTED | account-service | P2-17 - Sprint 4 |

### 4. Fund Transfers & Saga

| Feature | Status | Service | Notes |
|---------|--------|---------|-------|
| Transfer Initiation | COMPLETE_AND_VERIFIED | transaction-service | Saga initiation |
| Daily Limits | COMPLETE_AND_VERIFIED | transaction-service | Per user/type |
| Idempotency Keys | PARTIALLY_IMPLEMENTED | transaction-service | P0-08 fixes user-scoping |
| Transaction History | COMPLETE_AND_VERIFIED | transaction-service | Pagination, status |
| Saga Orchestration | COMPLETE_AND_VERIFIED | transaction-service | Orchestration pattern (DEC-002) |
| Debit Processing | COMPLETE_AND_VERIFIED | account-service | DebitConsumer |
| Credit Processing | COMPLETE_AND_VERIFIED | account-service | CreditConsumer |
| Reversal Processing | COMPLETE_AND_VERIFIED | account-service | ReversalConsumer |
| Pessimistic Locking | COMPLETE_AND_VERIFIED | account-service | SELECT FOR UPDATE |
| Outbox Pattern | COMPLETE_AND_VERIFIED | All services | Transactional outbox |
| Idempotency (multi-level) | PARTIALLY_IMPLEMENTED | All services | P0-08 fixes user-scoping |
| DLQ Configuration | NOT_IMPLEMENTED | All services | P0-13 - Sprint 0 |
| Retry/Backoff | NOT_IMPLEMENTED | All services | P2-08 - Sprint 3 |
| Beneficiary Management | NOT_IMPLEMENTED | transaction-service | P2-04 - Sprint 3 |
| Penny Drop Verification | NOT_IMPLEMENTED | transaction-service | P2-05 - Sprint 3 |
| Beneficiary Limits | NOT_IMPLEMENTED | transaction-service | P2-06 - Sprint 3 |
| Scheduled Transfers | NOT_IMPLEMENTED | transaction-service | Phase 5 |
| Standing Instructions | NOT_IMPLEMENTED | transaction-service | Phase 5 |

### 5. Notifications

| Feature | Status | Service | Notes |
|---------|--------|---------|-------|
| Email (SMTP/SendGrid) | COMPLETE_AND_VERIFIED | notification-service | Gmail SMTP + SendGrid |
| HTML Templates | COMPLETE_AND_VERIFIED | notification-service | Transaction, KYC, OTP |
| KYC Notifications | PARTIALLY_IMPLEMENTED | notification-service | Consumes kyc-topic |
| Transaction Notifications | COMPLETE_AND_VERIFIED | notification-service | Success/failure/reversal |
| OTP Email | COMPLETE_AND_VERIFIED | notification-service | Consumes otp-verification-topic |
| SMS Notifications | NOT_IMPLEMENTED | notification-service | Planned |
| In-App Notifications | NOT_IMPLEMENTED | notification-service | Planned |
| Notification Preferences | NOT_IMPLEMENTED | notification-service | Planned |
| Retry Mechanisms | NOT_IMPLEMENTED | notification-service | Planned |

### 6. Ledger (Planned)

| Feature | Status | Service | Notes |
|---------|--------|---------|-------|
| Chart of Accounts | NOT_IMPLEMENTED | ledger-service | DEC-007 - Phase 5 |
| Journal Entry Engine | NOT_IMPLEMENTED | ledger-service | DEC-007 - Phase 5 |
| Balanced Posting Validation | NOT_IMPLEMENTED | ledger-service | DEC-007 - Phase 5 |
| Trial Balance | NOT_IMPLEMENTED | ledger-service | DEC-007 - Phase 5 |
| Period-End Close | NOT_IMPLEMENTED | ledger-service | DEC-007 - Phase 5 |
| GL Reconciliation | NOT_IMPLEMENTED | ledger-service | DEC-007 - Phase 5 |
| Regulatory Reporting | NOT_IMPLEMENTED | ledger-service | DEC-007 - Phase 5 |
| Immutable Audit Trail | NOT_IMPLEMENTED | ledger-service | DEC-007 - Phase 5 |

### 7. Compliance (Planned)

| Feature | Status | Service | Notes |
|---------|--------|---------|-------|
| Sanctions/PEP Screening | NOT_IMPLEMENTED | compliance-service | DEC-008 - Phase 5 |
| Transaction Monitoring (AML) | NOT_IMPLEMENTED | compliance-service | DEC-008 - Phase 5 |
| SAR Filing | NOT_IMPLEMENTED | compliance-service | DEC-008 - Phase 5 |
| Regulatory Reporting | NOT_IMPLEMENTED | compliance-service | DEC-008 - Phase 5 |

### 7. Infrastructure & Platform

| Feature | Status | Component | Notes |
|---------|--------|-----------|-------|
| API Gateway (Spring Cloud Gateway) | COMPLETE_AND_VERIFIED | apigateway-service | Routing, JWT, rate limiting |
| Config Server (Native/Git) | COMPLETE_AND_VERIFIED | config-service | Native for local, Git for prod |
| Eureka Discovery | COMPLETE_AND_VERIFIED | eureka-server | Local only (DEC-014) |
| Config Server Git Backend | NOT_IMPLEMENTED | config-service | Staging/Prod - Sprint 2+ |
| Spring Cloud Bus | PARTIALLY_IMPLEMENTED | config-service | Kafka for config refresh |
| Docker Compose (Local) | COMPLETE_AND_VERIFIED | docker-compose.yml | All services + infra |
| Kubernetes Manifests | NOT_IMPLEMENTED | K8s | Sprint 7-8 |
| Helm Charts | NOT_IMPLEMENTED | Helm | Sprint 7-8 |
| EKS Cluster | NOT_IMPLEMENTED | AWS | Sprint 7-8 |
| RDS MySQL (Prod) | NOT_IMPLEMENTED | AWS RDS | DEC-001, DEC-011, DEC-013 |
| MSK Kafka | NOT_IMPLEMENTED | AWS MSK | DEC-002, DEC-010 |
| ElastiCache Redis | NOT_IMPLEMENTED | AWS | Sprint 7-8 |
| S3 Buckets (KYC) | NOT_IMPLEMENTED | AWS S3 | DEC-010, DEC-011 |
| Secrets Manager | NOT_IMPLEMENTED | AWS | DEC-012 - Sprint 0/1 |
| KMS Keys | NOT_IMPLEMENTED | AWS KMS | DEC-011, DEC-013 |
| WAF + Shield | NOT_IMPLEMENTED | AWS | Sprint 8 |
| GitHub Actions CI/CD | NOT_IMPLEMENTED | GitHub Actions | Sprint 7 |
| OIDC Federation | NOT_IMPLEMENTED | GitHub Actions | Sprint 7 |
| Terraform Modules | DESIGN_COMPLETE | Terraform | infrastructure/terraform-design.md |

### 8. Observability

| Feature | Status | Component | Notes |
|---------|--------|-----------|-------|
| Actuator Endpoints | COMPLETE_AND_VERIFIED | All services | health, info, metrics, prometheus |
| Micrometer + Prometheus | PARTIALLY_IMPLEMENTED | All services | Basic metrics exposed |
| Zipkin Tracing | PARTIALLY_IMPLEMENTED | All services | Brave bridge |
| Structured JSON Logging | NOT_IMPLEMENTED | All services | Sprint 6 |
| LogstashEncoder | NOT_IMPLEMENTED | All services | Sprint 6 |
| MDC Correlation IDs | NOT_IMPLEMENTED | All services | Sprint 6 |
| Centralized Logging (ELK/Loki) | NOT_IMPLEMENTED | Infrastructure | Sprint 6 |
| Distributed Tracing (Tempo/Zipkin) | PARTIALLY_IMPLEMENTED | All services | Sprint 6 |
| Custom Business Metrics | NOT_IMPLEMENTED | All services | Sprint 6 |
| Feign Trace Propagation | NOT_IMPLEMENTED | Feign clients | Sprint 6 |
| Kafka Trace Propagation | NOT_IMPLEMENTED | Kafka | Sprint 6 |
| Health Checks (Liveness/Readiness) | PARTIALLY_IMPLEMENTED | All services | Sprint 6 |
| Custom Dashboards (Grafana) | NOT_IMPLEMENTED | Grafana | Sprint 6 |
| Alerting Rules | NOT_IMPLEMENTED | AlertManager | Sprint 6 |

### 9. Security Hardening

| Feature | Status | Component | Notes |
|---------|--------|-----------|-------|
| Rate Limiting (Auth) | NOT_IMPLEMENTED | API Gateway | P0-01 - Sprint 0 |
| Rate Limiting (API) | COMPLETE_AND_VERIFIED | API Gateway | Redis Token Bucket |
| Account Lockout | NOT_IMPLEMENTED | Auth Service | P1-01 - Sprint 1 |
| Password Policy | NOT_IMPLEMENTED | Auth Service | P1-02 - Sprint 1 |
| TOTP MFA | NOT_IMPLEMENTED | Auth Service | P1-04 - Sprint 1 |
| Password Reset | NOT_IMPLEMENTED | Auth Service | P1-03 - Sprint 1 |
| JWT Secret Validation | NOT_IMPLEMENTED | All services | P0-05, P0-07 |
| RBAC Enforcement | PARTIALLY_IMPLEMENTED | All services | Sprint 1 |
| SoD Enforcement | NOT_IMPLEMENTED | KYC Workflow | DEC-005 - Sprint 1/2 |
| Audit Logging | PARTIALLY_IMPLEMENTED | KYC, Auth | Sprint 1/2 |
| Input Validation | PARTIALLY_IMPLEMENTED | All services | P2-09 - Sprint 3 |
| Error Handling (Generic) | NOT_IMPLEMENTED | All services | P2-03 - Sprint 3 |
| Container Security (Non-root) | NOT_IMPLEMENTED | Docker | P0-11 - Sprint 0 |
| KYC Document Security | NOT_IMPLEMENTED | S3/ClamAV | DEC-010 - Sprint 3/5 |
| MySQL TDE | NOT_APPLICABLE | RDS MySQL | DEC-011 - Not supported on RDS MySQL |
| RDS TLS | NOT_IMPLEMENTED | RDS | DEC-013 - Sprint 0/1 |
| HashiCorp Vault | NOT_IMPLEMENTED | Vault | DEC-012 - Replaced by AWS Secrets Manager |

---

## Implementation Coverage Summary

| Domain | Total Features | Complete | Partial | Not Started | Planned |
|--------|---------------|----------|---------|-------------|---------|
| Auth & Authorization | 14 | 4 | 4 | 6 | 0 |
| User Profile & KYC | 15 | 2 | 2 | 10 | 1 |
| Account Management | 14 | 4 | 3 | 7 | 0 |
| Fund Transfers | 18 | 7 | 4 | 7 | 0 |
| Notifications | 8 | 3 | 1 | 4 | 0 |
| Ledger | 7 | 0 | 0 | 0 | 7 |
| Compliance | 4 | 0 | 0 | 0 | 4 |
| Infrastructure | 19 | 5 | 3 | 11 | 0 |
| Observability | 16 | 2 | 3 | 11 | 0 |
| Security | 19 | 3 | 5 | 11 | 0 |
| **TOTAL** | **132** | **30** | **24** | **66** | **12** |

**Completion Rate**: 23% Complete, 18% Partial, 50% Not Started, 9% Planned

---

## Next Steps

1. **Immediate (Sprint 0)**: Focus on P0 security fixes (15 tasks)
2. **Sprint 1-2**: Authentication hardening + Ledger implementation
3. **Sprint 3-4**: Transaction completeness + Account lifecycle
3. **Sprint 5-6**: Resilience + Observability
4. **Sprint 7-8**: CI/CD + K8s deployment
4. **Phase 5**: Advanced features & regulatory

---

*Last Updated: 2026-11-19 | Inventory Version: 1.0*