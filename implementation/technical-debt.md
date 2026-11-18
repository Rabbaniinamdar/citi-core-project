# CitiCore Technical Debt Register

## Overview

This register tracks all known technical debt items in the CitiCore Banking Platform. Items are categorized by severity and linked to the implementation roadmap where applicable.

## Classification

| Severity | Description | SLA |
|----------|-------------|-----|
| **CRITICAL** | Security vulnerability, data loss risk, financial incorrectness | Immediate (Sprint 0) |
| **HIGH** | Architectural violation, scalability blocker, compliance gap | Next sprint |
| **MEDIUM** | Code quality, performance, maintainability | Next 2-3 sprints |
| **LOW** | Cosmetic, documentation, minor optimization | Backlog |

---

## Technical Debt Items

### Critical (Sprint 0)

| ID | Title | Description | Impact | Sprint | Owner |
|------|-------|-------------|--------|--------|-------|
| TD-001 | All services permitAll() on endpoints | Account Service and User Service have `.permitAll()` on all endpoints - SEC-009, SEC-010 | **CRITICAL** - Unauthenticated access to financial operations | Sprint 0 | Backend Lead |
| TD-002 | JWT Secret validation missing | No startup validation for JWT_SECRET length/strength | **CRITICAL** - Weak secret = token forgery | Sprint 0 | Security Lead |
| TD-003 | Idempotency key not user-scoped | Global unique constraint allows cross-user collision | **CRITICAL** - Cross-user information leakage | Sprint 0 | Backend Lead |
| TD-004 | No rate limiting on auth endpoints | No protection against brute force | **CRITICAL** - Brute force, credential stuffing | Sprint 0 | Security Lead |
| TD-005 | 6-digit OTP | 900,000 combinations - brute forcable | **CRITICAL** - OTP brute force in ~2 hours | Sprint 0 | Security Lead |
| TD-006 | No account lockout | Unlimited password guessing | **CRITICAL** - Unlimited password guessing | Sprint 0 | Security Lead |
| TD-007 | No password policy | Weak passwords accepted | **CRITICAL** - Weak passwords accepted | Sprint 0 | Security Lead |
| TD-008 | No password reset flow | Users cannot recover accounts | **CRITICAL** - Support burden | Sprint 0 | Security Lead |
| TD-009 | No password change with token revocation | Compromised password → persistent access | **CRITICAL** - Persistent access via refresh token | Sprint 0 | Security Lead |
| TD-010 | System.out.println in AuthService | BCrypt hashes logged | **CRITICAL** - Hashes in logs → offline cracking | Sprint 0 | Backend Lead |
| TD-011 | No balance check constraints | No DB CHECK constraints (balance >= 0, min_balance) | **CRITICAL** - Negative balances possible | Sprint 0 | Backend Lead |
| TD-012 | account_statement txn_ref global unique | Prevents transfer debit+credit with same txnRef | **CRITICAL** - Insert failures or workaround breaks idempotency | Sprint 0 | Backend Lead |
| TD-013 | No DLQ for Kafka consumers | Failed messages lost or block processing | **CRITICAL** - Message loss, processing stalls | Sprint 0 | Backend Lead |
| TD-014 | Docker images run as root | Container escape → host compromise | **CRITICAL** - Container escape | Sprint 0 | DevOps |
| TD-015 | No Flyway migrations | ddl-auto: update in production | **CRITICAL** - Schema drift, data loss risk | Sprint 0 | Backend Lead |
| TD-016 | Account Service credit() no ownership check | Any user can credit ANY account | **CRITICAL** - BOLA/BFLA | Sprint 0 | Backend Lead |
| TD-015 | User Service SecurityConfig references /accounts/** | Copy-paste bug - all endpoints public | **CRITICAL** - All user endpoints public | Sprint 0 | Backend Lead |

---

### High (Sprints 1-2)

| ID | Title | Description | Impact | Sprint | Owner |
|------|-------|-------------|--------|--------|-------|
| TD-017 | No password reset flow | Users cannot recover accounts | HIGH | Sprint 1 | Backend Lead |
| TD-018 | No TOTP MFA support | Single factor only - phishing vulnerable | HIGH | Sprint 1 | Backend Lead |
| TD-015 | No password policy enforcement | Weak passwords accepted | HIGH | Sprint 1 | Backend Lead |
| TD-016 | No account lockout | Unlimited password guessing | HIGH | Sprint 1 | Backend Lead |
| TD-017 | Saga: Choreography vs Orchestration ambiguity | Transaction Service acts as orchestrator but docs say choreography | HIGH - Confusion in failure handling | Sprint 1 | Architect |
| TD-018 | Cross-DB referential integrity not enforced | MySQL doesn't support cross-schema FKs | HIGH - Data inconsistency risk | Sprint 1 | Architect |
| TD-019 | Idempotency key not user-scoped | Global unique constraint allows cross-user collision | HIGH - Cross-user info leakage | Sprint 0/1 | Backend Lead |
| TD-018 | Account Statement txn_ref global unique | Prevents transfer debit+credit with same txnRef | HIGH | Sprint 0 | Backend Lead |
| TD-019 | No DB-level balance constraints | Negative balances possible | HIGH | Sprint 0 | Backend Lead |
| TD-019 | No DLQ configuration | Failed messages lost/block processing | HIGH | Sprint 0 | Backend Lead |
| TD-020 | No circuit breakers on Feign clients | Cascade failures | HIGH | Sprint 1 | Backend Lead |
| TD-021 | Single Kafka broker (no HA) | Message loss on restart | HIGH | Sprint 2 | DevOps |
| TD-022 | No database backup/recovery strategy | Data loss risk, RPO/RTO undefined | HIGH | Sprint 1 | DevOps |
| TD-023 | No secrets management (Vault/Parameter Store) | Secrets in env vars, git history, CI logs | HIGH | Sprint 0/1 | DevOps |
| TD-024 | No network segmentation | Single Docker network for all services | HIGH | Sprint 2 | DevOps |
| TD-025 | No TLS for database connections | Credentials and data in transit exposed | HIGH | Sprint 0/1 | DevOps |
| TD-026 | No encryption at rest for PII | GDPR/PDPB non-compliance | HIGH | Sprint 1 | DevOps |
| TD-027 | Transaction isolation TOCTOU risk | validateTransfer uses non-locking read | HIGH | Sprint 1 | Architect |
| TD-028 | No fee/commission engine | Revenue leakage | MEDIUM | Phase 5 | Backend Lead |
| TD-029 | No interest accrual engine | Regulatory requirement | HIGH | Sprint 4 | Backend Lead |
| TD-029 | Beneficiary management missing | Poor UX, no verification | HIGH | Sprint 3 | Backend Lead |
| TD-030 | Account closure workflow missing | Regulatory compliance risk | HIGH | Sprint 4 | Backend Lead |
| TD-030 | PDF statement generation missing | Customers can't download statements | MEDIUM | Sprint 4 | Backend Lead |
| TD-031 | Multi-currency not designed | Cannot expand internationally | LOW | Phase 5 | Architect |
| TD-032 | ddl-auto: update in production config | Schema drift, data loss risk | CRITICAL | Sprint 0 | Backend Lead |
| TD-033 | No Flyway migrations | Schema versioning missing | CRITICAL | Sprint 0 | Backend Lead |
| TD-033 | System.out.println debug logging | Hashes in logs | CRITICAL | Sprint 0 | Backend Lead |
| TD-034 | Plain text logging (no JSON, no correlation IDs) | Debugging difficulty | MEDIUM | Sprint 6 | DevOps |
| TD-034 | RoutingDataSource with AOP - complex, hard to test | Maintainability | MEDIUM | Sprint 2 | Backend Lead |
| TD-034 | Saga state not persisted | Only in Kafka | HIGH | Sprint 2 | Architect |
| TD-035 | kafka-events shared library - versioning coupling | Breaking changes require coordinated deployment | MEDIUM | Phase 5 | Architect |
| TD-035 | Config Server native profile only | No Git backend for prod | MEDIUM | Sprint 2 | DevOps |
| TD-034 | No API versioning strategy | Breaking changes require coordinated deployment | MEDIUM | Phase 5 | Architect |
| TD-035 | Balance query forces primary (bypasses replica) | Replica not utilized for reads | LOW | Sprint 2 | Backend Lead |

---

### Medium (Sprints 3-6)

| ID | Title | Description | Impact | Sprint | Owner |
|------|-------|-------------|--------|--------|-------|
| TD-036 | No circuit breakers on Feign clients | Cascade failures | HIGH | Sprint 5 | Backend Lead |
| TD-037 | No rate limiting on business APIs | Unrestricted resource consumption | HIGH | Sprint 5 | DevOps |
| TD-038 | No Kafka producer/consumer timeouts | Cascading timeouts, thread pool exhaustion | HIGH | Sprint 5 | Backend Lead |
| TD-034 | No database query timeouts | Thread pool exhaustion | HIGH | Sprint 5 | Backend Lead |
| TD-035 | Kafka producer acks=all not configured | Data loss risk | HIGH | Sprint 5 | Backend Lead |
| TD-035 | Consumer idempotency not strengthened | Duplicate events | HIGH | Sprint 5 | Backend Lead |
| TD-036 | No structured JSON logging | Debugging difficulty | MEDIUM | Sprint 6 | DevOps |
| TD-037 | No MDC correlation IDs | Distributed tracing broken | MEDIUM | Sprint 6 | DevOps |
| TD-037 | No centralized logging (ELK/Loki) | Logs not searchable | MEDIUM | Sprint 6 | DevOps |
| TD-037 | No custom business metrics | Business visibility | MEDIUM | Sprint 6 | Backend Lead |
| TD-038 | No Feign trace propagation | Broken traces across sync calls | MEDIUM | Sprint 6 | Backend Lead |
| TD-038 | No Kafka trace propagation | Broken traces across async boundaries | MEDIUM | Sprint 6 | Backend Lead |
| TD-038 | Health checks incomplete | Missing Kafka, Feign, Business checks | MEDIUM | Sprint 6 | Backend Lead |
| TD-038 | No Kafka consumer lag monitoring | Can't detect processing delays | MEDIUM | Sprint 6 | DevOps |
| TD-039 | Missing database indexes | Slow outbox polling, history queries | MEDIUM | Sprint 6 | Backend Lead |
| TD-040 | HikariCP pools not tuned | Connection exhaustion | MEDIUM | Sprint 6 | DevOps |
| TD-040 | No CI/CD pipeline | Manual deployments | HIGH | Sprint 7 | DevOps |
| TD-040 | No SAST/SCA/Container scanning | Vulnerabilities undetected | HIGH | Sprint 7 | DevOps |
| TD-040 | No K8s manifests | Not deployable to EKS | HIGH | Sprint 7 | DevOps |
| TD-041 | No resource requests/limits | OOM kills, noisy neighbors | HIGH | Sprint 8 | DevOps |
| TD-041 | No NetworkPolicies | Zero trust not enforced | HIGH | Sprint 8 | DevOps |
| TD-042 | No graceful shutdown | Outbox/Kafka flush on SIGTERM | HIGH | Sprint 8 | Backend Lead |
| TD-042 | No OpenAPI/Swagger docs | API consumers lack documentation | MEDIUM | Sprint 8 | Backend Lead |
| TD-042 | No runbooks | Incident response slow | MEDIUM | Sprint 8 | Team |
| TD-042 | No SLOs/SLIs defined | No measurable reliability targets | MEDIUM | Sprint 8 | Architect |
| TD-043 | No automated MySQL backups | Data loss risk | HIGH | Sprint 8 | DevOps |
| TD-043 | No DR runbook/drills | RPO/RTO untested | HIGH | Sprint 8 | DevOps |
| TD-043 | No Flyway for all schema changes | ddl-auto:update still used | CRITICAL | Sprint 0 | Backend Lead |

---

### Low (Backlog)

| ID | Title | Description | Impact | Sprint | Owner |
|------|-------|-------------|--------|--------|-------|
| TD-044 | No multi-currency support | Cannot expand internationally | LOW | Phase 5 | Architect |
| TD-045 | No API versioning strategy | Breaking changes require coordinated deployment | MEDIUM | Phase 5 | Architect |
| TD-045 | Balance query forces primary for consistency | Replica underutilized | LOW | Sprint 2 | Backend Lead |
| TD-045 | No beneficiary management | Poor UX, no verification | HIGH | Sprint 3 | Backend Lead |
| TD-045 | No interest accrual engine | Regulatory requirement | HIGH | Sprint 4 | Backend Lead |
| TD-045 | Account closure workflow missing | Regulatory compliance risk | HIGH | Sprint 4 | Backend Lead |
| TD-045 | PDF statement generation missing | Customers can't download statements | MEDIUM | Sprint 4 | Backend Lead |
| TD-045 | No dispute/chargeback flow | Regulatory requirement | HIGH | Phase 5 | Backend Lead |
| TD-045 | No fraud detection | High fraud risk | HIGH | Phase 5 | Backend Lead |
| TD-045 | No sanctions/PEP screening | Regulatory requirement | HIGH | Phase 5 | Backend Lead |
| TD-045 | No credit bureau integration | Required for lending | MEDIUM | Phase 5 | Backend Lead |
| TD-045 | No payment reconciliation | Operational requirement | HIGH | Phase 5 | Backend Lead |
| TD-045 | No real KYC vendor integration | Mock only | MEDIUM | Phase 5 | Backend Lead |
| TD-045 | No Ledger Service | Regulatory requirement | HIGH | Phase 5 | Backend Lead |
| TD-045 | No Compliance Service | Regulatory requirement | HIGH | Phase 5 | Backend Lead |

---

## Debt Paydown Strategy

### Sprint 0 (Week 1-2): Critical Security
- Fix all 16 CRITICAL items
- Establish security baseline
- **Target**: Zero CRITICAL debt

### Sprint 1-2 (Week 3-6): Foundation
- Authentication hardening (10 items)
- Ledger implementation (8 items)
- **Target**: Zero HIGH debt from auth/ledger

### Sprint 3-4 (Week 7-10): Features
- Transaction completeness (10 items)
- Account lifecycle (9 items)
- **Target**: Core banking features complete

### Sprint 5-6 (Week 11-14): Resilience & Observability
- Circuit breakers, rate limiting (8 items)
- Full observability stack (12 items)
- **Target**: Production-grade reliability

### Sprint 7-8 (Week 15-18): CI/CD & Operations
- CI/CD pipeline (8 items)
- K8s deployment (10 items)
- **Target**: Production deployable

### Phase 5 (Week 19+): Advanced
- External integrations
- Advanced features
- Regulatory compliance

---

## Debt Metrics Tracking

| Metric | Current | Target (Sprint 8) | Measurement |
|--------|---------|-------------------|-------------|
| Critical Debt Items | 16 | 0 | Count |
| High Debt Items | 25 | 0 | Count |
| Medium Debt Items | 45 | < 10 | Count |
| Code Coverage | ~45% | > 80% | JaCoCo |
| Critical Vulnerabilities | Unknown | 0 | Trivy/OWASP |
| High Vulnerabilities | Unknown | < 5 | Trivy/OWASP |
| Technical Debt Ratio | ~35% | < 10% | SonarQube |
| Duplicated Code | ~15% | < 3% | SonarQube |

---

## Debt Prevention Rules (Going Forward)

1. **No new CRITICAL debt** - All PRs must pass quality gates
2. **Definition of Done** includes: no new CRITICAL/HIGH debt
3. **Architecture Review** required for new features
3. **Security Review** for auth/payment/KYC changes
4. **Code Review** requires 2 approvals
5. **Definition of Done** enforced in CI/CD

---

*Last Updated: 2026-11-19 | Technical Debt Register Version: 1.0*