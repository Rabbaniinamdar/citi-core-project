# CitiCore Banking Platform - Implementation Roadmap

## Executive Summary

This roadmap prioritizes all identified issues from the architecture, bug, security, database, and production readiness audits into a phased implementation plan. The roadmap spans **20 weeks** with a team of **5-7 engineers**.

**Total Issues:** 47 confirmed bugs + 23 risks + 32 security findings + 15 database issues + 40 production gaps

---

## Priority Framework

| Priority | Definition | SLA |
|----------|------------|-----|
| **P0 - Critical** | Security vulnerabilities, financial correctness, data loss risk | Fix immediately (Week 1-2) |
| **P1 - High** | Core banking functionality, transaction consistency, regulatory gaps | Sprint 1-4 (Week 3-10) |
| **P2 - Medium** | Reliability, resilience, observability, testing | Sprint 5-8 (Week 11-18) |
| **P3 - Low** | Performance optimization, advanced features, tech debt | Quarter 2+ (Week 19+) |

---

## Phase 0: Immediate Critical Fixes (Week 1-2)

### Team: 3 Engineers (Security Lead, Backend Lead, DevOps)

| Task ID | Issue | Source | Description | Owner | Done Criteria |
|---------|-------|--------|-------------|-------|---------------|
| P0-01 | SEC-001 | Security | Add rate limiting to all auth endpoints (/login, /register, /verify, /resend) | Security Lead | 5 req/min login, 3 req/5min register enforced |
| P0-02 | SEC-002 | Security | Implement 6-digit numeric OTP for email verification (2.8M combinations) | Security Lead | `generateVerificationCode()` returns 6-digit numeric |
| P0-03 | SEC-009 | Security | Fix account-service SecurityConfig - require auth on all endpoints | Backend Lead | All `/accounts/**` return 401 without JWT |
| P0-04 | SEC-010 | Security | Fix user-service SecurityConfig - require auth on all endpoints | Backend Lead | All `/api/v1/users/**` return 401 without JWT |
| P0-05 | SEC-015 | Security | Add JWT secret validation at startup (min 32 chars, not default) | Security Lead | App fails fast if JWT_SECRET weak/missing |
| P0-06 | SEC-016 | Security | Remove all `System.out.println` from AuthService, use SLF4J | Backend Lead | No plaintext passwords/hashes in logs |
| P0-07 | BUG-001 | Bug Audit | Add default JWT secret validation in config | DevOps | `${JWT_SECRET:CHANGE_ME}` with startup check |
| P0-08 | BUG-008 | Bug Audit | Fix idempotency key - add auth_user_id, unique constraint | Backend Lead | Migration V5 applied, cross-user collision impossible |
| P0-09 | BUG-013 | Bug Audit | Fix account-service permitAll → authenticated | Backend Lead | SecurityConfig updated per SEC-009 |
| P0-10 | BUG-014 | Bug Audit | Fix user-service permitAll → authenticated | Backend Lead | SecurityConfig updated per SEC-010 |
| P0-11 | PROD-01 | Prod Ready | Configure non-root user in all Dockerfiles | DevOps | All images run as UID 1000 |
| P0-12 | PROD-02 | Prod Ready | Add Flyway migration framework, disable ddl-auto:update | Backend Lead | `spring.jpa.hibernate.ddl-auto: validate` |
| P0-13 | PROD-03 | Prod Ready | Configure Dead Letter Queues for all Kafka consumers | Backend Lead | DLQ topics exist, failed messages routed |
| P0-14 | DB-01 | Database | Add balance check constraints (balance >= 0, min balance) | Backend Lead | Migration V7 applied, constraints enforced |
| P0-15 | DB-02 | Database | Fix account_statement txn_ref unique per account | Backend Lead | Migration V6 applied, composite unique index |

**Deliverable:** All P0 issues resolved, security baseline established.

---

## Phase 1: Core Banking Foundation (Week 3-6)

### Sprint 1 (Week 3-4): Authentication & Authorization Hardening

| Task ID | Issue | Source | Description | Owner | Done Criteria |
|---------|-------|--------|-------------|-------|---------------|
| P1-01 | SEC-003 | Security | Implement account lockout after 5 failed logins (15 min) | Backend Lead | LoginAttempt entity, lockout enforced |
| P1-02 | SEC-004 | Security | Add password policy (min 12 chars, complexity) | Backend Lead | @Pattern on RegisterRequest, zxcvbn integration |
| P1-03 | SEC-005 | Security | Implement password reset flow (secure token, 15 min expiry) | Backend Lead | /forgot-password, /reset-password endpoints |
| P1-04 | SEC-006 | Security | Add Email OTP MFA support (6-digit, 5-min expiry, 3 attempts) | Backend Lead | MFA enable/disable, login challenge flow |
| P1-05 | SEC-007 | Security | Revoke all refresh tokens on password change | Backend Lead | `refreshTokenRepo.revokeAllByUser()` called |
| P1-06 | SEC-011 | Security | Fix AccountService.credit() ownership check for deposits | Backend Lead | Deposits require ownership, saga credits don't |
| P1-07 | SEC-013 | Security | Implement RBAC with @PreAuthorize on admin endpoints | Backend Lead | ADMIN role required for KYC review, user mgmt |
| P1-08 | BUG-010 | Bug Audit | Add password policy validation in RegisterRequest | Backend Lead | Bean Validation annotations |
| P1-09 | BUG-011 | Bug Audit | Increase verification code entropy (already in P0-02) | - | Covered |
| P1-10 | BUG-012 | Bug Audit | Implement password change endpoint with token revocation | Backend Lead | /change-password endpoint |

### Sprint 2 (Week 5-6): Double-Entry Ledger Implementation

| Task ID | Issue | Source | Description | Owner | Done Criteria |
|---------|-------|--------|-------------|-------|---------------|
| P1-11 | GAP-E-01 | Banking Gap | Design Chart of Accounts (Asset/Liability/Equity/Income/Expense) | Architect | COA document reviewed |
| P1-12 | GAP-E-02 | Banking Gap | Create ledger_accounts, journal_entries, journal_lines tables | Backend Lead | Migration V8 applied |
| P1-13 | GAP-E-03 | Banking Gap | Implement JournalEntryService with balanced posting validation | Backend Lead | Debits == credits enforced at DB + app level |
| P1-14 | GAP-E-04 | Banking Gap | Refactor AccountService to post journal entries on every balance change | Backend Lead | Debit/credit/create account → journal entry |
| P1-15 | GAP-E-05 | Banking Gap | Implement trial balance generation | Backend Lead | /admin/ledger/trial-balance endpoint |
| P1-16 | GAP-E-06 | Banking Gap | Add periodic reconciliation job (daily) | Backend Lead | Scheduled job compares account.balance vs journal |
| P1-17 | DB-03 | Database | Add database trigger for journal balance enforcement | Backend Lead | Migration V8 trigger active |
| P1-18 | DB-04 | Database | Implement reconciliation views for monitoring | Backend Lead | Views for balance vs statement vs journal |

**Deliverable:** Double-entry ledger operational, all financial movements auditable.

---

## Phase 2: Transaction & Account Completeness (Week 7-10)

### Sprint 3 (Week 7-8): Transaction Management & Beneficiaries

| Task ID | Issue | Source | Description | Owner | Done Criteria |
|---------|-------|--------|-------------|-------|---------------|
| P2-01 | BUG-005 | Bug Audit | Add @Positive validation on TransactionRequest.amount | Backend Lead | Negative amounts rejected at DTO level |
| P2-02 | BUG-018 | Bug Audit | Add toAccount validation in transfer initiation | Backend Lead | Feign call to verify beneficiary exists |
| P2-03 | BUG-023 | Bug Audit | Generic error messages for client, detailed server logs | Backend Lead | GlobalExceptionHandler updated |
| P2-04 | GAP-D-01 | Banking Gap | Implement Beneficiary entity (nickname, account, IFSC, verified) | Backend Lead | Entity, repo, CRUD endpoints |
| P2-05 | GAP-D-02 | Banking Gap | Implement penny drop verification (₹1 transfer) | Backend Lead | Beneficiary verification workflow |
| P2-06 | GAP-D-03 | Banking Gap | Add beneficiary limits (daily/monthly per user) | Backend Lead | Config + validation |
| P2-07 | BUG-006 | Bug Audit | Fix Kafka consumer group IDs (unique per topic) | Backend Lead | All consumers have distinct group IDs |
| P2-08 | BUG-007 | Bug Audit | Configure DLQ with retry/backoff for all consumers | Backend Lead | Exponential backoff, max 3 retries |
| P2-09 | SEC-022 | Security | Add @Positive, @DecimalMin, @Digits on amount fields | Backend Lead | All amount DTOs validated |
| P2-10 | SEC-023 | Security | Add @Max(100) on pagination size parameters | Backend Lead | All pagination endpoints limited |

### Sprint 4 (Week 9-10): Account Lifecycle & Statements

| Task ID | Issue | Source | Description | Owner | Done Criteria |
|---------|-------|--------|-------------|-------|---------------|
| P2-11 | GAP-C-01 | Banking Gap | Implement account closure workflow (zero balance, regulatory hold check) | Backend Lead | /accounts/{accNo}/close endpoint |
| P2-12 | GAP-C-02 | Banking Gap | Implement available vs ledger balance (auth holds) | Backend Lead | Dual balance model, hold/release API |
| P2-13 | GAP-C-03 | Banking Gap | Add interest accrual engine for savings accounts | Backend Lead | Daily job, monthly capitalization |
| P2-14 | GAP-C-04 | Banking Gap | Generate PDF account statements (iText/Flying Saucer) | Backend Lead | /accounts/{accNo}/statement/pdf |
| P2-15 | GAP-C-05 | Banking Gap | Implement nominee management (CRUD) | Backend Lead | Nominee fields editable |
| P2-16 | GAP-C-06 | Banking Gap | Add account tier/pricing configuration | Backend Lead | Tier entity, fee schedule |
| P2-17 | BUG-021 | Bug Audit | Fix account number generator - use sequence with allocationSize | Backend Lead | Hi-lo or Redis counter, no hotspot |
| P2-18 | BUG-036 | Bug Audit | Enforce pagination limits everywhere | Backend Lead | @Max(100) on all size params |
| P2-19 | BUG-037 | Bug Audit | Add date range filter to transaction history | Backend Lead | startDate, endDate params |

**Deliverable:** Complete account lifecycle, beneficiary management, dual balance, PDF statements.

---

## Phase 3: Resilience & Observability (Week 11-14)

### Sprint 5 (Week 11-12): Circuit Breakers, Rate Limiting, Timeouts

| Task ID | Issue | Source | Description | Owner | Done Criteria |
|---------|-------|--------|-------------|-------|---------------|
| P3-01 | SEC-024 | Security | Add Resilience4j circuit breaker on AccountFeignClient | Backend Lead | Fallback returns false for KYC check |
| P3-02 | SEC-024 | Security | Add Resilience4j circuit breaker on UserClient | Backend Lead | Fallback for KYC status |
| P3-03 | BUG-009 | Bug Audit | Implement Feign fallback for validateTransfer | Backend Lead | Fail-open or fail-closed documented |
| P3-04 | PROD-04 | Prod Ready | Configure rate limiting on business APIs (per user) | DevOps | 100 req/min per user default |
| P3-05 | PROD-05 | Prod Ready | Configure Kafka producer/consumer timeouts | Backend Lead | delivery.timeout.ms, max.poll.interval.ms set |
| P3-06 | PROD-06 | Prod Ready | Configure database query timeouts | Backend Lead | HikariCP connection-timeout, max-lifetime |
| P3-07 | BUG-027 | Bug Audit | Configure Kafka producer acks=all, idempotence=true | Backend Lead | Exactly-once semantics |
| P3-08 | BUG-028 | Bug Audit | Strengthen consumer idempotency (DB unique constraint primary) | Backend Lead | Duplicate events rejected at DB |

### Sprint 6 (Week 13-14): Observability Stack

| Task ID | Issue | Source | Description | Owner | Done Criteria |
|---------|-------|--------|-------------|-------|---------------|
| P3-09 | PROD-07 | Prod Ready | Implement structured JSON logging with LogstashEncoder | DevOps | All logs JSON with traceId/spanId |
| P3-10 | PROD-08 | Prod Ready | Add MDC filter for trace correlation | DevOps | traceId/spanId in all log lines |
| P3-11 | PROD-09 | Prod Ready | Deploy ELK/Loki for centralized logging | DevOps | Logs searchable in Kibana/Grafana |
| P3-12 | PROD-10 | Prod Ready | Add custom business metrics (transfers, balances, errors) | Backend Lead | Prometheus metrics exposed |
| P3-13 | PROD-11 | Prod Ready | Configure Feign trace propagation interceptor | Backend Lead | X-B3 headers on all Feign calls |
| P3-14 | PROD-12 | Prod Ready | Configure Kafka trace propagation interceptors | Backend Lead | Trace context in Kafka headers |
| P3-15 | PROD-13 | Prod Ready | Add comprehensive health indicators (Kafka, Feign, Business) | Backend Lead | /actuator/health shows all deps |
| P3-16 | PROD-14 | Prod Ready | Add Kafka consumer lag monitoring | DevOps | Lag metric exposed to Prometheus |
| P3-17 | DB-05 | Database | Add missing indexes (outbox polling, history queries) | Backend Lead | Migrations applied, query plans optimal |
| P3-18 | DB-06 | Database | Tune HikariCP connection pools per service | DevOps | Pool sizes configured, monitored |

**Deliverable:** Full observability, resilience patterns operational.

---

## Phase 4: CI/CD, Deployment & Operations (Week 15-18)

### Sprint 7 (Week 15-16): CI/CD Pipeline

| Task ID | Issue | Source | Description | Owner | Done Criteria |
|---------|-------|--------|-------------|-------|---------------|
| P4-01 | PROD-15 | Prod Ready | Create GitHub Actions CI pipeline (build, test, scan) | DevOps | Pipeline runs on PR/main |
| P4-02 | PROD-16 | Prod Ready | Add SAST (CodeQL, SpotBugs, PMD) to pipeline | DevOps | Zero critical findings to merge |
| P4-03 | PROD-17 | Prod Ready | Add SCA (Dependency Check) to pipeline | DevOps | No critical CVEs in dependencies |
| P4-04 | PROD-18 | Prod Ready | Add container scanning (Trivy) to pipeline | DevOps | No critical vulns in images |
| P4-05 | PROD-19 | Prod Ready | Build multi-stage Docker images (non-root) | DevOps | All services build successfully |
| P4-06 | PROD-20 | Prod Ready | Push images to registry (GHCR/ECR) | DevOps | Images versioned by git SHA |
| P4-07 | PROD-21 | Prod Ready | Deploy to staging environment automatically | DevOps | Helm chart deploys on merge to main |
| P4-08 | PROD-22 | Prod Ready | Run integration tests against staging | DevOps | Newman/Postman collection passes |

### Sprint 8 (Week 17-18): Kubernetes & Operational Excellence

| Task ID | Issue | Source | Description | Owner | Done Criteria |
|---------|-------|--------|-------------|-------|---------------|
| P4-09 | PROD-23 | Prod Ready | Create K8s manifests (Deployment, Service, HPA, PDB) | DevOps | All services deployable via Helm |
| P4-10 | PROD-24 | Prod Ready | Configure resource requests/limits per service | DevOps | Requests/limits set, QoS Guaranteed |
| P4-11 | PROD-25 | Prod Ready | Configure NetworkPolicies (zero-trust) | DevOps | Only required inter-service traffic |
| P4-12 | PROD-26 | Prod Ready | Implement graceful shutdown (SIGTERM handling) | Backend Lead | 30s drain, outbox flush, Kafka flush |
| P4-13 | PROD-27 | Prod Ready | Add OpenAPI/Swagger documentation (springdoc) | Backend Lead | /swagger-ui.html accessible |
| P4-14 | PROD-28 | Prod Ready | Create runbooks for top 10 incident scenarios | Team | Runbooks in Git, reviewed |
| P4-15 | PROD-29 | Prod Ready | Define SLOs/SLIs (availability, latency, error rate) | Architect | SLO doc, dashboards |
| P4-16 | DB-07 | Database | Configure automated MySQL backups (XtraBackup) | DevOps | Daily full + 15min binlog, tested restore |
| P4-17 | DB-08 | Database | Document DR runbook, schedule quarterly drills | DevOps | DR runbook in Git, drill calendar |
| P4-18 | BUG-044 | Bug Audit | Add Flyway migration for all schema changes | Backend Lead | No ddl-auto:update in any env |

**Deliverable:** Production-deployable, operable, documented system.

---

## Phase 5: Advanced Features & Regulatory (Week 19+)

### Quarter 2+ (Ongoing)

| Task ID | Issue | Source | Description | Priority | Effort |
|---------|-------|--------|-------------|----------|--------|
| P5-01 | GAP-D-04 | Banking Gap | IMPS/UPI integration (NPCI certified) | P1 | Very High |
| P5-02 | GAP-D-05 | Banking Gap | NEFT/RTGS integration (RBI certified) | P1 | Very High |
| P5-03 | GAP-H-01 | Banking Gap | Fixed/Term deposits with maturity workflow | P1 | High |
| P5-04 | GAP-H-02 | Banking Gap | Recurring deposits (RD) | P2 | High |
| P5-05 | GAP-H-03 | Banking Gap | Scheduled/standing transfers | P2 | Medium |
| P5-06 | GAP-H-04 | Banking Gap | Dispute/chargeback management | P1 | Very High |
| P5-07 | GAP-H-05 | Banking Gap | Fraud detection (rules + ML) | P1 | Very High |
| P5-08 | GAP-H-06 | Banking Gap | Sanctions/PEP screening integration | P1 | High |
| P5-09 | GAP-H-07 | Banking Gap | Credit bureau integration | P2 | High |
| P5-10 | GAP-H-08 | Banking Gap | Payment reconciliation engine | P1 | High |
| P5-11 | GAP-A-01 | Banking Gap | Customer risk scoring (KYC/AML) | P1 | High |
| P5-12 | GAP-A-02 | Banking Gap | Consent management (GDPR/PDPB) | P2 | Medium |
| P5-13 | GAP-B-01 | Banking Gap | SMS OTP provider integration | P1 | Medium |
| P5-14 | GAP-B-02 | Banking Gap | Push notifications (FCM/APNs) | P2 | Medium |
| P5-15 | GAP-G-01 | Banking Gap | In-app notification center | P2 | Medium |
| P5-16 | RISK-004 | Risks | Multi-region DR deployment | P2 | High |
| P5-17 | RISK-005 | Risks | Kafka HA (3-broker cluster) | P1 | Medium |
| P5-18 | RISK-006 | Risks | Secrets management (Vault) | P1 | Medium |
| P5-19 | RISK-013 | Risks | Beneficiary management completion | P1 | Medium |
| P5-20 | RISK-014 | Risks | Fraud detection velocity checks | P1 | High |

---

## Resource Allocation

| Role | Phase 0 | Phase 1 | Phase 2 | Phase 3 | Phase 4 | Phase 5 |
|------|---------|---------|---------|---------|---------|---------|
| Architect | 0.5 | 1 | 0.5 | 0.5 | 0.5 | 1 |
| Security Lead | 1 | 1 | 0.5 | 0.5 | 0 | 0.5 |
| Backend Lead | 1 | 1 | 1 | 1 | 1 | 1 |
| Backend Engineer | 1 | 1 | 1 | 1 | 1 | 2 |
| DevOps Engineer | 0.5 | 0.5 | 0.5 | 1 | 1 | 1 |
| QA Engineer | 0 | 0.5 | 0.5 | 0.5 | 1 | 1 |
| **Total FTE** | **4** | **5** | **4** | **4.5** | **3.5** | **5.5** |

---

## Milestone Gates

| Milestone | Target Week | Gate Criteria |
|-----------|-------------|---------------|
| **M1: Security Baseline** | Week 2 | All P0 security issues fixed, penetration test passed |
| **M2: Ledger Operational** | Week 6 | Double-entry ledger posting, reconciliation job green |
| **M3: Core Banking Complete** | Week 10 | Account lifecycle, beneficiaries, transfers, PDF statements |
| **M4: Production Ready** | Week 18 | CI/CD green, K8s deployable, observability stack operational |
| **M5: Regulatory Ready** | Week 30+ | External integrations certified, compliance audit passed |

---

## Risk Mitigation

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Double-entry ledger complexity underestimated | High | High | Spike in Week 1, prototype by Week 2 |
| NPCI/UPI certification timeline | High | Very High | Start sandbox integration Week 3, parallel track |
| Team capacity (5 engineers) | Medium | High | Prioritize ruthlessly, defer P3/P4 |
| Data migration for existing accounts | Medium | High | Design migration scripts early, test thoroughly |
| Kafka message loss during DLQ implementation | Low | Critical | Test in staging with chaos engineering |
| Database constraint violations on existing data | Medium | High | Clean data before adding constraints |

---

## Success Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| Security vulnerabilities (Critical/High) | 0 | Weekly SAST/DAST scans |
| Test coverage (unit + integration) | >80% | JaCoCo + Newman |
| API latency (p99) | <500ms | Prometheus/Grafana |
| Error rate | <0.1% | Prometheus/Grafana |
| Availability | 99.9% | Uptime monitoring |
| Deployment frequency | Daily | CI/CD pipeline |
| Lead time for changes | <1 day | GitHub Actions |
| MTTR | <30 min | Incident tracking |
| DR drill success | 100% | Quarterly drill report |

---

## Budget Estimate (Engineering Weeks)

| Phase | Weeks | Engineers | Cost (Weeks × FTE) |
|-------|-------|-----------|-------------------|
| Phase 0: Critical Fixes | 2 | 4 | 8 |
| Phase 1: Core Banking | 4 | 5 | 20 |
| Phase 2: Transaction/Account | 4 | 4 | 16 |
| Phase 3: Resilience/Observability | 4 | 4.5 | 18 |
| Phase 4: CI/CD/Operations | 4 | 3.5 | 14 |
| Phase 5: Advanced (Ongoing) | 12+ | 5.5 | 66+ |
| **Total (Phases 0-4)** | **18** | **-** | **76 engineering weeks** |
| **Total (Incl. Phase 5)** | **30+** | **-** | **142+ engineering weeks** |

---

## Dependencies Map

```
P0-01..P0-15 (Week 1-2)
    │
    ├─→ P1-01..P1-10 (Week 3-4) ──→ P1-11..P1-18 (Week 5-6)
    │                                      │
    │                                      └─→ P2-01..P2-10 (Week 7-8) ──→ P2-11..P2-19 (Week 9-10)
    │                                                                      │
    │                                                                      └─→ P3-01..P3-08 (Week 11-12) ──→ P3-09..P3-18 (Week 13-14)
    │                                                                                      │
    │                                                                                      └─→ P4-01..P4-08 (Week 15-16) ──→ P4-09..P4-18 (Week 17-18)
    │                                                                                                        │
    │                                                                                                        └─→ Phase 5 (Week 19+)
```

**Critical Path:** P0 → P1 (Ledger) → P2 (Account/Transaction) → P3 (Observability) → P4 (Deploy) → Phase 5

---

## Communication Plan

| Ceremony | Frequency | Participants | Purpose |
|----------|-----------|--------------|---------|
| Daily Standup | Daily | All engineers | Sync, blockers |
| Sprint Planning | Bi-weekly | All engineers | Commit to sprint goals |
| Sprint Review | Bi-weekly | Team + Stakeholders | Demo, feedback |
| Sprint Retrospective | Bi-weekly | All engineers | Process improvement |
| Architecture Review | Weekly | Architect + Leads | Technical decisions |
| Security Review | Weekly | Security Lead + Backend | Vulnerability triage |
| DR Drill Review | Quarterly | DevOps + Leads | DR readiness |
| Stakeholder Update | Monthly | PM + Architect | Progress, risks |

---

## Definition of Done (Per Task)

- [ ] Code implemented and reviewed (2 approvals)
- [ ] Unit tests written (>80% coverage on new code)
- [ ] Integration tests pass (Testcontainers)
- [ ] Security scan passes (no new Critical/High)
- [ ] Documentation updated (README, API docs, runbooks)
- [ ] Deployed to staging and verified
- [ ] Performance benchmark meets SLO
- [ ] Rollback plan documented

---

## Next Immediate Actions (This Week)

1. **Today:** Assign P0 tasks to engineers, create GitHub issues
2. **Day 2:** Implement JWT secret validation (P0-05, P0-07)
3. **Day 3:** Fix SecurityConfig permitAll issues (P0-03, P0-04, P0-09, P0-10)
4. **Day 4:** Add rate limiting to auth endpoints (P0-01)
5. **Day 5:** Replace 6-digit OTP (P0-02)
6. **Day 6-7:** Idempotency key migration (P0-08), balance constraints (P0-14), Docker hardening (P0-11)

**Week 2 Goal:** All P0 complete, security baseline established, ready for ledger implementation.