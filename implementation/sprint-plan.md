# CitiCore Implementation Sprint Plan

## Overview

This document provides a detailed sprint-by-sprint breakdown for the CitiCore Banking Platform implementation, derived from the [Implementation Roadmap](../roadmap.md) and aligned with the approved architecture and ADRs.

## Sprint Structure Overview

| Phase | Sprints | Duration | Focus |
|-------|---------|----------|-------|
| **Phase 0** | Sprint 0 | Week 1-2 | Critical Security Fixes (P0) |
| **Phase 1** | Sprint 1-2 | Week 3-6 | Core Banking Foundation |
| **Phase 2** | Sprint 3-4 | Week 7-10 | Transaction & Account Completeness |
| **Phase 3** | Sprint 5-6 | Week 11-14 | Resilience & Observability |
| **Phase 4** | Sprint 7-8 | Week 15-18 | CI/CD, Deployment & Operations |
| **Phase 5** | Sprint 9+ | Week 19+ | Advanced Features & Regulatory |

---

## Phase 0: Critical Security Fixes (Week 1-2)

### Sprint 0: Critical Security Fixes (Week 1-2)

**Objective**: Establish security baseline by fixing all P0 vulnerabilities

**Team**: 3 Engineers (Security Lead, Backend Lead, DevOps)

| Task ID | Description | Owner | Acceptance Criteria | ADR Ref |
|---------|-------------|-------|---------------------|---------|
| P0-01 | Add rate limiting to all auth endpoints (/login, /register, /verify, /resend) | Security Lead | 5 req/min login, 3 req/5min register enforced | DEC-009 |
| P0-02 | Replace 6-digit OTP with 8-char alphanumeric code | Security Lead | `generateVerificationCode()` returns 2.8T combinations | DEC-009 |
| P0-03 | Fix account-service SecurityConfig - require auth on all endpoints | Backend Lead | All `/accounts/**` return 401 without JWT | - |
| P0-04 | Fix user-service SecurityConfig - require auth on all endpoints | Backend Lead | All `/api/v1/users/**` return 401 without JWT | - |
| P0-05 | Add JWT secret validation at startup (min 32 chars, not default) | Security Lead | App fails fast if JWT_SECRET weak/missing | DEC-006 |
| P0-06 | Remove all `System.out.println` from AuthService, use SLF4J | Backend Lead | No plaintext passwords/hashes in logs | - |
| P0-07 | Add default JWT secret validation in config | DevOps | `${JWT_SECRET:CHANGE_ME}` with startup check | DEC-006 |
| P0-08 | Fix idempotency key - add auth_user_id, unique constraint | Backend Lead | Migration V5 applied, cross-user collision impossible | - |
| P0-09 | Fix account-service permitAll → authenticated | Backend Lead | SecurityConfig updated per SEC-009 | - |
| P0-10 | Fix user-service permitAll → authenticated | Backend Lead | SecurityConfig updated per SEC-010 | - |
| P0-11 | Configure non-root user in all Dockerfiles | DevOps | All images run as UID 1000 | - |
| P0-12 | Add Flyway migration framework, disable ddl-auto:update | Backend Lead | `spring.jpa.hibernate.ddl-auto: validate` | - |
| P0-13 | Configure Dead Letter Queues for all Kafka consumers | Backend Lead | DLQ topics exist, failed messages routed | - |
| P0-14 | Add balance check constraints (balance >= 0, min balance) | Backend Lead | Migration V7 applied, constraints enforced | - |
| P0-15 | Fix account_statement txn_ref unique per account | Backend Lead | Migration V6 applied, composite unique index | - |

**Definition of Done**: All P0 issues resolved, security baseline established, all services start without security warnings.

---

## Phase 1: Core Banking Foundation (Week 3-6)

### Sprint 1: Authentication & Authorization Hardening (Week 3-4)

**Objective**: Harden authentication and implement comprehensive RBAC

**Team**: 4 Engineers (Security Lead, Backend Lead ×2, DevOps)

| Task ID | Description | Owner | Acceptance Criteria | ADR Ref |
|---------|-------------|-------|---------------------|---------|
| P1-01 | Implement account lockout after 5 failed logins (15 min) | Backend Lead | LoginAttempt entity, lockout enforced | DEC-009 |
| P1-02 | Add password policy (min 12 chars, complexity) | Backend Lead | @Pattern on RegisterRequest, zxcvbn integration | DEC-009 |
| P1-03 | Implement password reset flow (secure token, 15 min expiry) | Backend Lead | /forgot-password, /reset-password endpoints | DEC-009 |
| P1-04 | Add TOTP MFA support with backup codes | Backend Lead | TOTP enroll/verify, backup codes generated | DEC-009 |
| P1-04 | Revoke all refresh tokens on password change | Backend Lead | `refreshTokenRepo.revokeAllByUser()` called | DEC-006 |
| P1-06 | Fix AccountService.credit() ownership check for deposits | Backend Lead | Deposits require ownership, saga credits don't | - |
| P1-07 | Implement RBAC with @PreAuthorize on admin endpoints | Backend Lead | ADMIN role required for KYC review, user mgmt | DEC-005 |
| P1-08 | Add password policy validation in RegisterRequest | Backend Lead | Bean Validation annotations | DEC-009 |
| P1-10 | Implement password change endpoint with token revocation | Backend Lead | /change-password endpoint | DEC-006 |

**Definition of Done**: All auth endpoints secured, RBAC enforced at gateway and service level, MFA functional.

---

### Sprint 2: Double-Entry Ledger Implementation (Week 5-6)

**Objective**: Implement double-entry general ledger for regulatory compliance

**Team**: 3 Engineers (Architect, Backend Lead ×2)

| Task ID | Description | Owner | Acceptance Criteria | ADR Ref |
|---------|-------------|-------|---------------------|---------|
| P1-11 | Design Chart of Accounts (Asset/Liability/Equity/Income/Expense) | Architect | COA document reviewed | DEC-007 |
| P1-12 | Create ledger_accounts, journal_entries, journal_lines tables | Backend Lead | Migration V8 applied | DEC-007 |
| P1-13 | Implement JournalEntryService with balanced posting validation | Backend Lead | Debits == credits enforced at DB + app level | DEC-007 |
| P1-14 | Refactor AccountService to post journal entries on every balance change | Backend Lead | Debit/credit/create account → journal entry | DEC-007 |
| P1-15 | Implement trial balance generation | Backend Lead | /admin/ledger/trial-balance endpoint | DEC-007 |
| P1-16 | Add periodic reconciliation job (daily) | Backend Lead | Scheduled job compares account.balance vs journal | DEC-007 |
| P1-17 | Add database trigger for journal balance enforcement | Backend Lead | Migration V8 trigger active | DEC-007 |
| P1-18 | Implement reconciliation views for monitoring | Backend Lead | Views for balance vs statement vs journal | DEC-007 |

**Definition of Done**: Double-entry ledger operational, all financial movements auditable, trial balance balances.

---

## Phase 2: Transaction & Account Completeness (Week 7-10)

### Sprint 3: Transaction Management & Beneficiaries (Week 7-8)

**Objective**: Complete transaction management and beneficiary features

**Team**: 4 Engineers (Backend Lead ×2, DevOps, QA)

| Task ID | Description | Owner | Acceptance Criteria | ADR Ref |
|---------|-------------|-------|---------------------|---------|
| P2-01 | Add @Positive validation on TransactionRequest.amount | Backend Lead | Negative amounts rejected at DTO level | - |
| P2-02 | Add toAccount validation in transfer initiation | Backend Lead | Feign call to verify beneficiary exists | - |
| P2-03 | Generic error messages for client, detailed server logs | Backend Lead | GlobalExceptionHandler updated | - |
| P2-04 | Implement Beneficiary entity (nickname, account, IFSC, verified) | Backend Lead | Entity, repo, CRUD endpoints | - |
| P2-05 | Implement penny drop verification (₹1 transfer) | Backend Lead | Beneficiary verification workflow | - |
| P2-06 | Add beneficiary limits (daily/monthly per user) | Backend Lead | Config + validation | - |
| P2-07 | Fix Kafka consumer group IDs (unique per topic) | Backend Lead | All consumers have distinct group IDs | - |
| P2-08 | Configure DLQ with retry/backoff for all consumers | Backend Lead | Exponential backoff, max 3 retries | DEC-010 |
| P2-09 | Add @Positive, @DecimalMin, @Digits on amount fields | Backend Lead | All amount DTOs validated | - |
| P2-10 | Add @Max(100) on pagination size parameters | Backend Lead | All pagination endpoints limited | - |

**Definition of Done**: Beneficiary management complete, penny drop functional, all amount validation in place.

---

### Sprint 4: Account Lifecycle & Statements (Week 9-10)

**Objective**: Complete account lifecycle management and statement generation

**Team**: 3 Engineers (Backend Lead ×2, DevOps)

| Task ID | Description | Owner | Acceptance Criteria | ADR Ref |
|---------|-------------|-------|---------------------|---------|
| P2-11 | Implement account closure workflow (zero balance, regulatory hold check) | Backend Lead | /accounts/{accNo}/close endpoint | - |
| P2-12 | Implement available vs ledger balance (auth holds) | Backend Lead | Dual balance model, hold/release API | - |
| P2-13 | Add interest accrual engine for savings accounts | Backend Lead | Daily job, monthly capitalization | - |
| P2-14 | Generate PDF account statements (iText/Flying Saucer) | Backend Lead | /accounts/{accNo}/statement/pdf | - |
| P2-15 | Implement nominee management (CRUD) | Backend Lead | Nominee fields editable | - |
| P2-16 | Add account tier/pricing configuration | Backend Lead | Tier entity, fee schedule | - |
| P2-17 | Fix account number generator - use sequence with allocationSize | Backend Lead | Hi-lo or Redis counter, no hotspot | - |
| P2-18 | Enforce pagination limits everywhere | Backend Lead | @Max(100) on all size params | - |
| P2-19 | Add date range filter to transaction history | Backend Lead | startDate, endDate params | - |

**Definition of Done**: Complete account lifecycle, dual balance model, PDF statements generated.

---

## Phase 3: Resilience & Observability (Week 11-14)

### Sprint 5: Circuit Breakers, Rate Limiting, Timeouts (Week 11-12)

**Objective**: Implement resilience patterns and rate limiting

**Team**: 3 Engineers (Backend Lead ×2, DevOps)

| Task ID | Description | Owner | Acceptance Criteria | ADR Ref |
|---------|-------------|-------|---------------------|---------|
| P3-01 | Add Resilience4j circuit breaker on AccountFeignClient | Backend Lead | Fallback returns false for KYC check | - |
| P3-02 | Add Resilience4j circuit breaker on UserClient | Backend Lead | Fallback for KYC status | - |
| P3-03 | Implement Feign fallback for validateTransfer | Backend Lead | Fail-open or fail-closed documented | - |
| P3-04 | Configure rate limiting on business APIs (per user) | DevOps | 100 req/min per user default | - |
| P3-05 | Configure Kafka producer/consumer timeouts | Backend Lead | delivery.timeout.ms, max.poll.interval.ms set | - |
| P3-06 | Configure database query timeouts | Backend Lead | HikariCP connection-timeout, max-lifetime | - |
| P3-07 | Configure Kafka producer acks=all, idempotence=true | Backend Lead | Exactly-once semantics | - |
| P3-08 | Strengthen consumer idempotency (DB unique constraint primary) | Backend Lead | Duplicate events rejected at DB | - |

**Definition of Done**: All Feign clients have circuit breakers, rate limiting active, timeouts configured.

---

### Sprint 6: Observability Stack (Week 13-14)

**Objective**: Deploy comprehensive observability stack

**Team**: 3 Engineers (DevOps ×2, Backend Lead)

| Task ID | Description | Owner | Acceptance Criteria | ADR Ref |
|---------|-------------|-------|---------------------|---------|
| P3-09 | Implement structured JSON logging with LogstashEncoder | DevOps | All logs JSON with traceId/spanId | - |
| P3-10 | Add MDC filter for trace correlation | DevOps | traceId/spanId in all log lines | - |
| P3-11 | Deploy ELK/Loki for centralized logging | DevOps | Logs searchable in Kibana/Grafana | - |
| P3-12 | Add custom business metrics (transfers, balances, errors) | Backend Lead | Prometheus metrics exposed | - |
| P3-13 | Configure Feign trace propagation interceptor | Backend Lead | X-B3 headers on all Feign calls | - |
| P3-14 | Configure Kafka trace propagation interceptors | Backend Lead | Trace context in Kafka headers | - |
| P3-15 | Add comprehensive health indicators (Kafka, Feign, Business) | Backend Lead | /actuator/health shows all deps | - |
| P3-16 | Add Kafka consumer lag monitoring | DevOps | Lag metric exposed to Prometheus | - |
| P3-17 | Add missing indexes (outbox polling, history queries) | Backend Lead | Migrations applied, query plans optimal | - |
| P3-18 | Tune HikariCP connection pools per service | DevOps | Pool sizes configured, monitored | - |

**Definition of Done**: Full observability stack deployed, all metrics visible, alerting configured.

---

## Phase 4: CI/CD, Deployment & Operations (Week 15-18)

### Sprint 7: CI/CD Pipeline (Week 15-16)

**Objective**: Establish complete CI/CD pipeline with quality gates

**Team**: 2 Engineers (DevOps ×2)

| Task ID | Description | Owner | Acceptance Criteria | ADR Ref |
|---------|-------------|-------|---------------------|---------|
| P4-01 | Create GitHub Actions CI pipeline (build, test, scan) | DevOps | Pipeline runs on PR/main | - |
| P4-02 | Add SAST (CodeQL, SpotBugs, PMD) to pipeline | DevOps | Zero critical findings to merge | - |
| P4-03 | Add SCA (Dependency Check) to pipeline | DevOps | No critical CVEs in dependencies | - |
| P4-04 | Add container scanning (Trivy) to pipeline | DevOps | No critical vulns in images | - |
| P4-05 | Build multi-stage Docker images (non-root) | DevOps | All services build successfully | DEC-011 |
| P4-06 | Push images to registry (GHCR/ECR) | DevOps | Images versioned by git SHA | - |
| P4-07 | Deploy to staging environment automatically | DevOps | Helm chart deploys on merge to main | DEC-014 |
| P4-08 | Run integration tests against staging | DevOps | Newman/Postman collection passes | - |

**Definition of Done**: CI/CD pipeline green, images pushed to registry, staging auto-deployed.

---

### Sprint 8: Kubernetes & Operational Excellence (Week 17-18)

**Objective**: Production-ready Kubernetes deployment and operational procedures

**Team**: 3 Engineers (DevOps ×2, Backend Lead)

| Task ID | Description | Owner | Acceptance Criteria | ADR Ref |
|---------|-------------|-------|---------------------|---------|
| P4-09 | Create K8s manifests (Deployment, Service, HPA, PDB) | DevOps | All services deployable via Helm | DEC-014 |
| P4-10 | Configure resource requests/limits per service | DevOps | Requests/limits set, QoS Guaranteed | - |
| P4-11 | Configure NetworkPolicies (zero-trust) | DevOps | Only required inter-service traffic | - |
| P4-12 | Implement graceful shutdown (SIGTERM handling) | Backend Lead | 30s drain, outbox flush, Kafka flush | - |
| P4-13 | Add OpenAPI/Swagger documentation (springdoc) | Backend Lead | /swagger-ui.html accessible | - |
| P4-14 | Create runbooks for top 10 incident scenarios | Team | Runbooks in Git, reviewed | - |
| P4-15 | Define SLOs/SLIs (availability, latency, error rate) | Architect | SLO doc, dashboards | - |
| P4-16 | Configure automated MySQL backups (XtraBackup) | DevOps | Daily full + 15min binlog, tested restore | - |
| P4-17 | Document DR runbook, schedule quarterly drills | DevOps | DR runbook in Git, drill calendar | - |
| P4-18 | Add Flyway migration for all schema changes | Backend Lead | No ddl-auto:update in any env | - |

**Definition of Done**: Production-deployable, operable, documented system.

---

## Phase 5: Advanced Features & Regulatory (Week 19+)

### Sprint 9+: Advanced Features (Ongoing)

| Task ID | Description | Priority | Effort | ADR Ref |
|---------|-------------|----------|--------|---------|
| P5-01 | IMPS/UPI integration (NPCI certified) | P1 | Very High | - |
| P5-02 | NEFT/RTGS integration (RBI certified) | P1 | Very High | - |
| P5-03 | Fixed/Term deposits with maturity workflow | P1 | High | - |
| P5-04 | Recurring deposits (RD) | P2 | High | - |
| P5-05 | Scheduled/standing transfers | P2 | Medium | - |
| P5-06 | Dispute/chargeback management | P1 | Very High | - |
| P5-07 | Fraud detection (rules + ML) | P1 | Very High | - |
| P5-08 | Sanctions/PEP screening integration | P1 | High | DEC-008 |
| P5-09 | Credit bureau integration | P2 | High | - |
| P5-10 | Payment reconciliation engine | P1 | High | - |
| P5-11 | Ledger Service implementation | P1 | Very High | DEC-007 |
| P5-12 | Compliance Service implementation | P1 | High | DEC-008 |
| P5-13 | Real KYC vendor integration (Jumio/Onfido) | P2 | Very High | DEC-015 |

---

## Sprint Planning Guidelines

### Sprint Planning Meeting (2 hours)
1. **Review previous sprint** - Demo, retrospective
2. **Capacity planning** - Team capacity, holidays, carryover
3. **Sprint goal** - Single measurable objective
4. **Task breakdown** - Break stories into ≤2 day tasks
5. **Capacity commitment** - Team commits to sprint goal

### Daily Standup (15 min)
- What did you complete yesterday?
- What will you complete today?
- Blockers/impediments

### Sprint Review (1 hour)
- Demo completed work
- Stakeholder feedback
- Update roadmap

### Sprint Retrospective (1 hour)
- What went well?
- What didn't go well?
- Action items for next sprint

### Definition of Done (All Sprints)
- [ ] Code implemented and peer-reviewed (2 approvals)
- [ ] Unit tests written (>80% coverage on new code)
- [ ] Integration tests pass (Testcontainers)
- [ ] Security scan passes (no new Critical/High)
- [ ] Documentation updated (README, API docs, runbooks)
- [ ] Deployed to staging and verified
- [ ] Performance benchmark meets SLO
- [ ] Rollback plan documented

---

## Sprint Capacity Planning

| Sprint | Team Size | Capacity (Story Points) | Focus |
|--------|-----------|------------------------|-------|
| Sprint 0 | 3 | 30 | P0 Security Fixes |
| Sprint 1 | 4 | 40 | Auth Hardening |
| Sprint 2 | 3 | 30 | Ledger Implementation |
| Sprint 3 | 4 | 40 | Transactions & Beneficiaries |
| Sprint 4 | 3 | 30 | Account Lifecycle |
| Sprint 5 | 3 | 30 | Resilience Patterns |
| Sprint 6 | 3 | 30 | Observability Stack |
| Sprint 7 | 2 | 20 | CI/CD Pipeline |
| Sprint 8 | 3 | 30 | K8s & Operations |
| Sprint 9+ | 4+ | 40+ | Advanced Features |

---

## Risk Register

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Ledger implementation complexity underestimated | High | High | Spike in Sprint 1, prototype by Sprint 2 |
| NPCI/UPI certification timeline | High | Very High | Start sandbox integration Sprint 3, parallel track |
| Team capacity (5 engineers) | Medium | High | Prioritize ruthlessly, defer P3/P4 |
| Data migration for existing accounts | Medium | High | Design migration scripts early, test thoroughly |
| Kafka message loss during DLQ implementation | Low | Critical | Test in staging with chaos engineering |
| Database constraint violations on existing data | Medium | High | Clean data before adding constraints |

---

## Definition of Ready (Story)

- [ ] Story written in standard format (As a... I want... So that...)
- [ ] Acceptance criteria defined and testable
- [ ] Dependencies identified and resolved
- [ ] UX/UI mockups ready (if applicable)
- [ ] API contract defined (OpenAPI spec)
- [ ] Database migration script reviewed
- [ ] Security review completed (if applicable)
- [ ] Estimated and sized by team

---

## Definition of Done (All Increments)

- [ ] Code implemented and reviewed (2 approvals)
- [ ] Unit tests written (>80% coverage on new code)
- [ ] Integration tests pass (Testcontainers)
- [ ] Security scan passes (no new Critical/High)
- [ ] Documentation updated (README, API docs, runbooks)
- [ ] Deployed to staging and verified
- [ ] Performance benchmark meets SLO
- [ ] Rollback plan documented

---

*Last Updated: 2026-11-19 | Sprint Plan Version: 1.0*