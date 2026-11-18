# CitiCore Banking Platform - Architecture Review Observations

## Document Information

| Field | Value |
|-------|-------|
| **Document Title** | Architecture Review Observations |
| **Version** | 2.0 |
| **Status** | Approved |
| **Classification** | Internal - Confidential |
| **Author** | Architecture Team |
| **Reviewers** | Engineering Lead, Security Lead, Platform Team, Product Owner |
| **Last Updated** | 2026-11-18 |

---

## Purpose

This document captures observations discovered during the architecture documentation process. These include missing information, contradictory design decisions, potential architectural problems, ambiguous service responsibilities, and unclear communication flows.

**IMPORTANT:** These observations are documented for review and approval. **Do not implement changes without explicit approval from stakeholders.**

---

## Summary of Observations

| Category | Count | Critical | High | Medium | Low |
|----------|-------|----------|------|--------|-----|
| Missing Information | 8 | 2 | 3 | 2 | 1 |
| Contradictory Decisions | 4 | 1 | 2 | 1 | 0 |
| Architectural Problems | 12 | 4 | 5 | 3 | 0 |
| Ambiguous Responsibilities | 6 | 1 | 2 | 2 | 1 |
| Unclear Communication Flows | 5 | 1 | 2 | 1 | 1 |
| **Total** | **35** | **9** | **14** | **9** | **3** |

---

## 1. Missing Information

### OBS-MI-001: Ledger Service Not Implemented
**Severity:** CRITICAL
**Description:** The double-entry General Ledger is documented as a core requirement but the Ledger Service (Port 8086) is marked as "Planned" and does not exist in the codebase. Current AccountStatement implementation is single-entry bookkeeping.
**Impact:** Cannot produce financial statements, trial balance, or meet regulatory requirements (SOX, IFRS, RBI).
**Current State:** Account Service directly updates `accounts.balance` and writes `AccountStatement` records (DEBIT or CREDIT per row).
**Required:** New microservice with Chart of Accounts, Journal Entry engine, balanced posting validation, trial balance generation, GL reconciliation jobs.
**Decision Needed:** Approve Ledger Service implementation as separate microservice vs. embedding in Account Service.

### OBS-MI-002: Available vs Ledger Balance Not Implemented
**Severity:** CRITICAL
**Description:** Only a single `balance` column exists in accounts table. No support for authorization holds (card payments, pending transfers), available balance calculation, or hold/release workflows.
**Impact:** Cannot support card payments, pre-authorizations, or proper available balance display to customers.
**Current State:** `accounts.balance` updated directly on every debit/credit.
**Required:** Add `available_balance` column, implement hold/release API, modify debit/credit to affect available balance first.
**Decision Needed:** Approve dual-balance model implementation.

### OBS-MI-003: Beneficiary Management Missing
**Severity:** HIGH
**Description:** No payee/beneficiary registry exists. Customers cannot save beneficiaries, perform penny-drop verification, or set beneficiary limits.
**Impact:** Poor user experience (re-enter details every transfer), no beneficiary verification, security risk.
**Current State:** Transfer requires `toAccount` number each time.
**Required:** Beneficiary entity with nickname, accountNumber, IFSC, name, verificationStatus, limits. Penny drop (₹1) verification workflow.
**Decision Needed:** Approve beneficiary management feature scope and timeline.

### OBS-MI-004: Interest Accrual Engine Missing
**Severity:** HIGH
**Description:** Savings accounts don't accrue interest. No daily accrual job, monthly capitalization, configurable rates, or TDS withholding.
**Impact:** Regulatory non-compliance, customer dissatisfaction, revenue leakage.
**Current State:** No interest calculation or posting.
**Required:** Daily batch job, configurable rate tiers, monthly capitalization, TDS calculation, ledger posting to interest income/expense.
**Decision Needed:** Approve interest engine implementation approach.

### OBS-MI-005: Fee/Commission Engine Missing
**Severity:** MEDIUM
**Description:** No support for transaction fees, service charges, interchange fees, or pricing tiers.
**Impact:** Revenue leakage, cannot implement pricing strategies.
**Current State:** All transfers are free.
**Required:** Configurable fee rules engine, per-transaction assessment, ledger posting to fee income accounts.
**Decision Needed:** Define fee structure and approve implementation.

### OBS-MI-006: Account Closure Workflow Missing
**Severity:** HIGH
**Description:** Account Service has `status` enum with CLOSED but no closure API or workflow. No zero-balance enforcement, regulatory hold checks, or balance transfer process.
**Impact:** Cannot close accounts properly, regulatory compliance risk.
**Current State:** Status field exists but no business logic.
**Required:** Closure endpoint with validations (zero balance, no holds, no pending transactions), regulatory hold check, audit trail.
**Decision Needed:** Approve closure workflow requirements.

### OBS-MI-007: PDF Statement Generation Missing
**Severity:** MEDIUM
**Description:** Account statements only available via JSON API. No PDF generation, storage, or download capability.
**Impact:** Customers cannot download official statements for records/tax purposes.
**Current State:** Only paginated JSON response.
**Required:** iText/Flying Saucer PDF generation, S3 storage with presigned URLs, email delivery option.
**Decision Needed:** Approve PDF statement feature.

### OBS-MI-008: Multi-Currency Support Not Designed
**Severity:** LOW
**Description:** Platform assumes INR only. No currency column, exchange rate service, or multi-currency ledger design.
**Impact:** Cannot expand to international operations or support foreign currency accounts.
**Current State:** All amounts in INR, no currency field.
**Required:** Add currency to accounts/transactions/ledger, exchange rate service, multi-currency GL.
**Decision Needed:** Defer to Phase 5 or include in core design.

---

## 2. Contradictory Design Decisions

### OBS-CD-001: Saga Choreography vs. Orchestration Ambiguity
**Severity:** HIGH
**Description:** Documentation describes "Saga Choreography" but Transaction Service acts as orchestrator (initiates saga, manages idempotency, tracks status, triggers reversal). Account Service consumers are participants.
**Evidence:** 
- PROJECT_ARCHITECTURE.md line 449: "Saga Choreography (Not Orchestration) - No central saga orchestrator"
- But Transaction Service: "Fund transfer initiation (saga orchestrator)" and manages complete flow
**Impact:** Confusion in failure handling, compensation logic, and observability.
**Decision Needed:** Clarify: Is Transaction Service a choreography participant or orchestrator? Document the pattern consistently.

### OBS-CD-002: Database per Service vs. Cross-DB Referential Integrity
**Severity:** HIGH
**Description:** Architecture mandates "Database per Service" but multiple cross-database foreign key references exist without enforcement (auth_user_id → auth.users, account_number → accounts).
**Evidence:** 
- PROJECT_ARCHITECTURE.md: "Database per Service"
- DATABASE_AUDIT.md: Lists 6 missing cross-DB FKs
- Application uses Feign for validation but no DB enforcement
**Impact:** Data inconsistency, orphaned records, audit failures.
**Decision Needed:** Choose: (A) Single database with schemas, (B) Application-level integrity + reconciliation jobs, (C) Database links.

### OBS-CD-003: Primary/Replica on Same Host
**Severity:** MEDIUM
**Description:** Account Service uses Primary/Replica routing but Docker Compose shows single MySQL instance. No true HA.
**Evidence:**
- PROJECT_ARCHITECTURE.md: "Primary/Replica setup (same host, different connection pools)"
- docker-compose.yml: Single mysql service
**Impact:** Single point of failure, no read scalability, replica lag not real.
**Decision Needed:** For production, provision separate replica in different AZ. Document current as dev-only.

### OBS-CD-004: Kafka Replication Factor = 1
**Severity:** HIGH
**Description:** Kafka configured with RF=1, minISR=1 but production requirements need RF=3.
**Evidence:**
- docker-compose.yml: `KAFKA_DEFAULT_REPLICATION_FACTOR: 1`, `KAFKA_MIN_INSYNC_REPLICAS: 1`
- PRODUCTION_READINESS.md: Requires 3-broker cluster, RF=3
**Impact:** Message loss on broker failure, no HA.
**Decision Needed:** Update docker-compose for local dev (keep RF=1) but document production config separately.

---

## 3. Architectural Problems

### OBS-AP-001: TOCTOU in Transfer Validation
**Severity:** HIGH
**Description:** `TransactionService.validateTransfer()` uses non-locking Feign call to Account Service. Balance may change between validation and actual debit by DebitConsumer.
**Evidence:** DATABASE_AUDIT.md Section 4.2: "Critical Gap: validateTransfer uses non-locking read (TOCTOU vulnerability)"
**Impact:** Potential overdraft if concurrent debits occur.
**Options:** 
- A: Remove pre-validation (rely on debit locking)
- B: Make validateTransfer use SELECT FOR UPDATE (adds latency)
- C: Optimistic locking with version column
**Decision Needed:** Choose approach and document trade-offs.

### OBS-AP-002: Idempotency Key Not User-Scoped
**Severity:** HIGH
**Description:** `IdempotencyKey` entity has global unique constraint on `idempotency_key`. User A's key could reveal User B's transaction reference.
**Evidence:** SECURITY_AUDIT.md SEC-012, DATABASE_AUDIT.md BUG-008
**Impact:** Cross-user information leakage, idempotency collision.
**Fix:** Add `auth_user_id` column, unique constraint on `(auth_user_id, idempotency_key)`.
**Decision Needed:** Approve schema migration (already in IMPLEMENTATION_ROADMAP P0-08).

### OBS-AP-003: Account Statement txn_ref Global Unique Constraint
**Severity:** MEDIUM
**Description:** `account_statements.txn_ref` has global unique index. But a transfer creates two statements (debit + credit) with same txnRef (credit appends "_CREDIT").
**Evidence:** DATABASE_AUDIT.md BUG-019, BANKING_FUNCTIONALITY_GAP_ANALYSIS.md
**Impact:** Insert failures or workaround with suffixes breaks idempotency.
**Fix:** Change to composite unique: `(account_number, txn_ref)`.
**Decision Needed:** Approve migration (already in IMPLEMENTATION_ROADMAP P0-15).

### OBS-AP-004: No Database-Level Balance Constraints
**Severity:** HIGH
**Description:** No CHECK constraints on `accounts.balance >= 0` or minimum balance per account type.
**Evidence:** DATABASE_AUDIT.md Section 2.2: "No check constraint balance >= 0", "No check constraint balance >= min_balance"
**Impact:** Negative balances possible via bugs, minimum balance violations not enforced at DB level.
**Fix:** Add CHECK constraints (migration V7 in DATABASE_AUDIT.md).
**Decision Needed:** Approve constraints (already in IMPLEMENTATION_ROADMAP P0-14).

### OBS-AP-005: No Dead Letter Queue Configuration
**Severity:** HIGH
**Description:** Kafka consumers have no DLQ configuration. Failed messages may be lost, block processing, or retry indefinitely.
**Evidence:** PRODUCTION_READINESS.md, SECURITY_AUDIT.md, IMPLEMENTATION_ROADMAP P0-13
**Impact:** Message loss, processing stalls, no visibility into failures.
**Fix:** Configure DLQ for all topics, retry with exponential backoff (max 3), DLQ monitoring.
**Decision Needed:** Approve DLQ implementation (already in IMPLEMENTATION_ROADMAP P0-13).

### OBS-AP-006: No Circuit Breakers on Feign Clients
**Severity:** HIGH
**Description:** Transaction Service → Account Service and Account Service → User Service Feign calls have no circuit breakers.
**Evidence:** PRODUCTION_READINESS.md Section 3.1, SECURITY_AUDIT.md SEC-024
**Impact:** Cascade failures, service down → entire transfer flow breaks.
**Fix:** Add Resilience4j circuit breakers with fallbacks (fail-closed for KYC).
**Decision Needed:** Approve circuit breaker implementation (IMPLEMENTATION_ROADMAP P3-01, P3-02).

### OBS-AP-007: Single Kafka Broker (No HA)
**Severity:** HIGH
**Description:** Docker Compose uses single Kafka broker. No replication, no HA.
**Evidence:** docker-compose.yml single kafka service, SECURITY_AUDIT.md SEC-028
**Impact:** Single point of failure, message loss on restart.
**Fix:** Production: 3-broker cluster, RF=3, minISR=2.
**Decision Needed:** Document production Kafka topology separately from dev.

### OBS-AP-008: No Database Backup/Recovery Strategy
**Severity:** CRITICAL
**Description:** Only Docker volume persistence. No automated backups, PITR, or tested restores.
**Evidence:** PRODUCTION_READINESS.md Section 6.2, SECURITY_AUDIT.md SEC-029, DATABASE_AUDIT.md Section 10
**Impact:** Data loss risk, RPO/RTO undefined, regulatory non-compliance.
**Fix:** Percona XtraBackup daily + 15min binlog, PITR tested quarterly, cross-region replication.
**Decision Needed:** Approve backup strategy and tooling investment.

### OBS-AP-009: No Secrets Management
**Severity:** HIGH
**Description:** JWT secret, DB passwords in env vars/docker-compose. Exposed in git history, CI logs, container inspect.
**Evidence:** SECURITY_AUDIT.md SEC-020, PRODUCTION_READINESS.md Section 1
**Impact:** Credential leakage, compliance violations.
**Fix:** HashiCorp Vault or AWS Secrets Manager integration.
**Decision Needed:** Choose secrets management solution and approve integration.

### OBS-AP-010: Docker Images Run as Root
**Severity:** HIGH
**Description:** No USER directive in Dockerfiles. Containers run as root.
**Evidence:** SECURITY_AUDIT.md SEC-027, PRODUCTION_READINESS.md Section 4.1
**Impact:** Container escape → host compromise.
**Fix:** Add non-root user (UID 1000) to all Dockerfiles.
**Decision Needed:** Approve Dockerfile changes (already in IMPLEMENTATION_ROADMAP P0-11).

### OBS-AP-011: No TLS for Database Connections
**Severity:** MEDIUM
**Description:** MySQL connections unencrypted. Credentials and data in transit exposed.
**Evidence:** SECURITY_AUDIT.md SEC-019, DATABASE_AUDIT.md
**Impact:** Network sniffing risk, compliance gaps.
**Fix:** Enable MySQL TLS: `useSSL=true&requireSSL=true&verifyServerCertificate=true`
**Decision Needed:** Approve TLS configuration for MySQL.

### OBS-AP-012: No Encryption at Rest for PII
**Severity:** HIGH
**Description:** Email, phone, address, KYC docs stored in plaintext. No MySQL TDE or column encryption.
**Evidence:** SECURITY_AUDIT.md SEC-018, PRODUCTION_READINESS.md
**Impact:** GDPR/PDPB non-compliance, data breach impact.
**Fix:** MySQL TDE for tablespace encryption, column-level encryption for PII, S3 SSE-KMS for KYC.
**Decision Needed:** Approve encryption strategy and implementation.

---

## 4. Ambiguous Service Responsibilities

### OBS-AS-001: Account Service Credit() Ownership Check
**Severity:** HIGH
**Description:** `AccountService.credit()` has comment "Ownership check is intentionally NOT added here" but deposits should require ownership while saga credits (receiver) should not.
**Evidence:** SECURITY_AUDIT.md SEC-011, AccountService.java line 265
**Impact:** Any authenticated user can credit ANY account if they know the number.
**Fix:** Distinguish deposit (require ownership) vs. saga credit (no ownership check).
**Decision Needed:** Approve the distinction logic and implementation.

### OBS-AS-002: User Service SecurityConfig Copy-Paste Bug
**Severity:** CRITICAL
**Description:** User Service SecurityConfig references `/accounts/**` endpoints instead of `/users/**`.
**Evidence:** SECURITY_AUDIT.md SEC-010, UserService SecurityConfig.java line 27
**Impact:** All user endpoints publicly accessible.
**Fix:** Update to proper user endpoint patterns.
**Decision Needed:** Immediate fix required (already in IMPLEMENTATION_ROADMAP P0-04, P0-10).

### OBS-AS-003: Notification Service Responsibility Boundaries
**Severity:** MEDIUM
**Description:** Notification Service consumes events from all services but ownership of templates, delivery retry, and preferences unclear.
**Evidence:** Multiple services publish to topics consumed by Notification Service.
**Impact:** Duplicate notifications, inconsistent templates, no preference management.
**Decision Needed:** Define Notification Service as platform service with clear APIs for template management, delivery tracking, preferences.

### OBS-AS-004: Ledger Service vs. Account Service Boundary
**Severity:** HIGH
**Description:** Account Service currently posts "AccountStatement" but Ledger Service will post "JournalEntry". Overlap in financial recording.
**Evidence:** PLANNED Ledger Service, current AccountService behavior
**Impact:** Dual recording, reconciliation complexity, data inconsistency.
**Decision Needed:** Define clear boundary: Account Service → sub-ledger (customer view), Ledger Service → GL (regulatory view). Account Service calls Ledger Service via Feign for journal posting.

### OBS-AS-005: Config Server Native Profile Only
**Severity:** MEDIUM
**Description:** Config Server uses native profile (local filesystem) but production needs Git backend.
**Evidence:** docker-compose.yml: `SPRING_PROFILES_ACTIVE: native`, `SPRING_CLOUD_CONFIG_SERVER_NATIVE_SEARCH_LOCATIONS: file:/config-repo`
**Impact:** No version control for config, no audit trail, manual updates.
**Decision Needed:** Plan Git backend migration for production.

### OBS-AS-006: Kafka Events Library Versioning
**Severity:** LOW
**Description:** Shared `kafka-events` library creates compile-time coupling. Schema evolution not managed.
**Evidence:** kafka-events module with shared DTOs
**Impact:** Breaking changes require coordinated deployment of all services.
**Decision Needed:** Migrate to Schema Registry (Avro/Protobuf) for schema evolution.

---

## 5. Unclear Communication Flows

### OBS-UC-001: Service-to-Service Authentication Mechanism
**Severity:** HIGH
**Description:** Documentation says "Feign clients propagate JWT via request interceptors" but unclear if user JWT or service token. Gateway validates user JWT but internal calls need service identity.
**Evidence:** PROJECT_ARCHITECTURE.md Section 362-365
**Impact:** Authorization confusion, audit trail gaps.
**Decision Needed:** Define: User JWT propagation for user-context calls, Service tokens (ROLE_SERVICE) for internal-only endpoints.

### OBS-UC-002: Kafka Trace Context Propagation
**Severity:** MEDIUM
**Description:** Distributed tracing configured but Kafka producer/consumer interceptors not implemented.
**Evidence:** PRODUCTION_READINESS.md Section 2.3: "Kafka Trace Propagation - Not implemented"
**Impact:** Broken traces across async boundaries, debugging difficulty.
**Decision Needed:** Implement Micrometer Tracing Kafka interceptors.

### OBS-UC-003: Feign Client Timeout Configuration
**Severity:** MEDIUM
**Description:** Feign timeouts documented (3s connect, 5s read) but not verified in all services. No circuit breaker timeout coordination.
**Evidence:** PRODUCTION_READINESS.md Section 3.3
**Impact:** Cascading timeouts, thread pool exhaustion.
**Decision Needed:** Standardize Feign config across services with centralized timeout values.

### OBS-UC-004: Eureka vs. Kubernetes Service Discovery
**Severity:** LOW
**Description:** Eureka used for local/Docker Compose but Kubernetes has native service discovery. Dual discovery mechanism in hybrid deployment.
**Evidence:** docker-compose.yml has Eureka, K8s manifests would use CoreDNS
**Impact:** Complexity, potential conflicts.
**Decision Needed:** Plan Eureka retirement for K8s deployment.

### OBS-UC-005: Config Refresh Mechanism
**Severity:** MEDIUM
**Description:** Spring Cloud Bus Kafka configured but config refresh not tested. `@RefreshScope` usage unclear.
**Evidence:** PROJECT_ARCHITECTURE.md, docker-compose.yml Spring Cloud Bus config
**Impact:** Config changes require restart, no dynamic updates.
**Decision Needed:** Test and document config refresh workflow.

---

## 6. Observation Resolution Tracking

| Observation ID | Status | Resolution | Target Date | Owner |
|----------------|--------|------------|-------------|-------|
| OBS-MI-001 | Open | Pending Approval | - | Architect |
| OBS-MI-002 | Open | Pending Approval | - | Architect |
| OBS-MI-003 | Open | In Roadmap Phase 2 | Week 7-8 | Backend Lead |
| OBS-MI-004 | Open | In Roadmap Phase 2 | Week 9-10 | Backend Lead |
| OBS-MI-005 | Open | Deferred to Phase 5 | - | Product Owner |
| OBS-MI-006 | Open | In Roadmap Phase 2 | Week 9-10 | Backend Lead |
| OBS-MI-007 | Open | In Roadmap Phase 2 | Week 9-10 | Backend Lead |
| OBS-MI-008 | Open | Deferred to Phase 5 | - | Architect |
| OBS-CD-001 | Open | Needs Clarification | Week 1 | Architect |
| OBS-CD-002 | Open | Needs Decision | Week 1 | Architect + DBA |
| OBS-CD-003 | Open | Document as Dev-Only | Week 1 | DevOps |
| OBS-CD-004 | Open | Separate Prod Config | Week 1 | DevOps |
| OBS-AP-001 | Open | Needs Decision | Week 1 | Architect |
| OBS-AP-002 | Approved | In Roadmap P0-08 | Week 1-2 | Backend Lead |
| OBS-AP-003 | Approved | In Roadmap P0-15 | Week 1-2 | Backend Lead |
| OBS-AP-004 | Approved | In Roadmap P0-14 | Week 1-2 | Backend Lead |
| OBS-AP-005 | Approved | In Roadmap P0-13 | Week 1-2 | Backend Lead |
| OBS-AP-006 | Approved | In Roadmap P3-01/02 | Week 11-12 | Backend Lead |
| OBS-AP-007 | Open | Document Prod Topology | Week 1 | DevOps |
| OBS-AP-008 | Open | Pending Approval | - | DevOps + DBA |
| OBS-AP-009 | Open | Pending Approval | - | Security Lead |
| OBS-AP-010 | Approved | In Roadmap P0-11 | Week 1-2 | DevOps |
| OBS-AP-011 | Open | Pending Approval | - | DevOps |
| OBS-AP-012 | Open | Pending Approval | - | Security Lead + DBA |
| OBS-AS-001 | Approved | In Roadmap P1-06 | Week 3-4 | Backend Lead |
| OBS-AS-002 | Approved | In Roadmap P0-04/10 | Week 1-2 | Backend Lead |
| OBS-AS-003 | Open | Needs Definition | Week 3 | Architect |
| OBS-AS-004 | Open | Needs Decision | Week 1 | Architect |
| OBS-AS-005 | Open | Plan Git Backend | Week 5 | DevOps |
| OBS-AS-006 | Open | Migrate to Schema Registry | Phase 5 | Architect |
| OBS-UC-001 | Open | Needs Definition | Week 1 | Security Lead |
| OBS-UC-002 | Approved | In Roadmap P3-14 | Week 13-14 | Backend Lead |
| OBS-UC-003 | Open | Standardize Config | Week 3 | Architect |
| OBS-UC-004 | Open | Plan Eureka Retirement | Phase 4 | DevOps |
| OBS-UC-005 | Open | Test & Document | Week 5 | Backend Lead |

---

## 7. Stakeholder Review Required

### Decisions Required This Week (Critical Path)

1. **OBS-CD-002**: Database topology - Single DB with schemas vs. Multi-DB with app-level integrity
2. **OBS-AP-001**: Transfer validation TOCTOU - Remove pre-validation vs. Locking validation
3. **OBS-AS-004**: Ledger Service boundary - Separate microservice vs. embedded in Account Service
4. **OBS-UC-001**: Service-to-service auth - User JWT propagation vs. Service tokens
5. **OBS-MI-001**: Ledger Service implementation approval
6. **OBS-MI-002**: Dual balance model approval

### Decisions Required Next Week

7. **OBS-AP-008**: Backup strategy and tooling approval
8. **OBS-AP-009**: Secrets management solution selection
9. **OBS-AP-011**: Database TLS configuration
10. **OBS-AP-012**: Encryption at rest strategy
11. **OBS-CD-001**: Saga pattern documentation clarification

### Decisions for Sprint Planning

12. **OBS-MI-003, 004, 006, 007**: Feature scope confirmation for Phase 2
13. **OBS-AS-003**: Notification Service responsibility definition
14. **OBS-AS-005**: Config Server Git backend timeline

---

## 8. Risk Assessment

| Risk | Related Observations | Probability | Impact | Mitigation |
|------|---------------------|-------------|--------|------------|
| **Regulatory Non-Compliance** | OBS-MI-001, OBS-MI-002, OBS-MI-004, OBS-AP-008, OBS-AP-012 | High | Critical | Prioritize Ledger Service, Dual Balance, Interest Engine, Backup, Encryption |
| **Data Loss** | OBS-AP-007, OBS-AP-008, OBS-CD-003 | Medium | Critical | 3-broker Kafka, Automated Backups, Cross-AZ Replica |
| **Security Breach** | OBS-AP-009, OBS-AP-010, OBS-AP-011, OBS-AS-002, OBS-CD-004 | High | Critical | Vault Integration, Non-Root Containers, DB TLS, Auth Hardening |
| **Financial Incorrectness** | OBS-AP-001, OBS-AP-002, OBS-AP-003, OBS-AP-004, OBS-AS-001 | Medium | Critical | Fix Idempotency, Constraints, TXN Ref, Ownership Checks, TOCTOU |
| **Operational Blindness** | OBS-AP-005, OBS-UC-002, OBS-AS-003 | Medium | High | DLQ Implementation, Kafka Tracing, Notification Service Definition |
| **Technical Debt Accumulation** | OBS-CD-001, OBS-CD-002, OBS-AS-006, OBS-UC-004 | High | Medium | Architecture Decisions Documented, Schema Registry Planned, Eureka Retirement |

---

## 9. Next Steps

1. **Immediate (This Week)**: Review and decide on 6 critical path decisions
2. **Week 2**: Update architecture documents with decisions, begin P0 implementation
3. **Week 3-4**: Resolve Phase 1 observations during Auth/Ledger implementation
4. **Ongoing**: Track resolution in IMPLEMENTATION_ROADMAP.md and GitHub Issues

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-11-17 | Architecture Team | Initial observations from architecture documentation process |

---

**END OF DOCUMENT**