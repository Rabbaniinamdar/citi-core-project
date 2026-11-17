# CitiCore Banking Platform - Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased] - 2024-01-XX

### Security - Critical Fixes (P0)
- **SEC-001**: Added rate limiting to auth endpoints (login: 5/min, register: 3/5min)
- **SEC-002**: Increased verification code entropy from 6 digits to 8-char alphanumeric (2.8T combinations)
- **SEC-009**: Fixed account-service SecurityConfig - all endpoints now require authentication
- **SEC-010**: Fixed user-service SecurityConfig - all endpoints now require authentication
- **SEC-015**: Added JWT secret validation at startup (min 32 chars, fails fast on default)
- **SEC-016**: Removed all `System.out.println` from AuthService, replaced with SLF4J structured logging

### Bug Fixes - Critical (P0)
- **BUG-001**: Added JWT secret default validation in config
- **BUG-008**: Fixed idempotency key cross-user collision - added `auth_user_id` column and unique constraint
- **BUG-013**: Fixed account-service permitAll vulnerability
- **BUG-014**: Fixed user-service permitAll vulnerability

### Database - Critical (P0)
- **DB-01**: Added balance check constraints (`balance >= 0`, minimum balance per account type)
- **DB-02**: Fixed `account_statement.txn_ref` unique constraint to be per-account composite index

### Infrastructure - Critical (P0)
- **PROD-01**: All Dockerfiles now run as non-root user (UID 1000)
- **PROD-02**: Added Flyway migration framework, disabled `ddl-auto:update`
- **PROD-03**: Configured Dead Letter Queues for all Kafka consumers

---

## [0.1.0] - 2024-01-15 (Initial Audit Baseline)

### Architecture Documentation
- Created PROJECT_ARCHITECTURE.md with complete system topology
- Documented all 8 microservices and their responsibilities
- Mapped data flows for fund transfer saga
- Documented database schemas per service
- Listed technology stack and deployment architecture

### Bug Audit Report (BUG_AUDIT_REPORT.md)
- Identified 47 confirmed bugs across all services
- 8 Critical, 15 High, 18 Medium, 6 Low severity
- Categorized by: Authentication, Authorization, Data Protection, API Security, Infrastructure
- Provided exact file locations, root causes, and recommended fixes

### Banking Functionality Gap Analysis (BANKING_FUNCTIONALITY_GAP_ANALYSIS.md)
- Compared current implementation against realistic digital banking platform
- Current coverage: ~35% of core banking functionality
- Identified 22 Mandatory Core gaps, 18 Important Digital, 8 Advanced
- Critical gap: No double-entry general ledger (single-entry only)
- Detailed implementation roadmap by phase

### Security Audit (SECURITY_AUDIT.md)
- 32 security findings against OWASP Top 10 2021 & API Security Top 10 2023
- 6 Critical, 13 High, 9 Medium, 4 Low
- PCI-DSS, SOX, GDPR, FFIEC compliance gaps documented
- Provided exact code fixes for all Critical/High issues

### Database Audit (DATABASE_AUDIT.md)
- Analyzed all 4 database schemas
- Identified missing foreign keys (cross-database)
- Missing indexes for critical query paths
- No double-entry ledger implementation
- No Flyway migrations, using `ddl-auto:update`
- Provided migration scripts for priority fixes

### Production Readiness (PRODUCTION_READINESS.md)
- Overall readiness: 45% (sandbox only)
- Gaps in: Observability, Resilience, CI/CD, Deployment, Operations
- 40-item checklist with P0-P3 priorities
- 16-week plan to production readiness

### Implementation Roadmap (IMPLEMENTATION_ROADMAP.md)
- 20-week phased plan with 5 engineers
- 5 phases: Critical Fixes → Core Banking → Transaction/Account → Resilience/Observability → CI/CD/Operations
- Detailed task breakdown with owners and done criteria
- Milestone gates and risk mitigation

### API Documentation (API_DOCUMENTATION.md)
- Complete REST API reference for all 4 business services
- Authentication, User, Account, Transaction endpoints
- Request/response examples, error codes, rate limits
- Idempotency, pagination, filtering specifications

### Testing Strategy (TESTING_STRATEGY.md)
- Test pyramid: 50% unit, 30% integration, 15% contract, 5% E2E
- Financial invariant testing (balance conservation, journal balancing)
- Chaos engineering experiments defined
- CI/CD test gates with quality thresholds
- Test data management patterns

---

## Feature Tracking Summary

| Feature Area | Current Status | Target Status | Priority |
|--------------|----------------|---------------|----------|
| **Authentication** | Partially Implemented | Fully Implemented | P0 |
| **Authorization (RBAC)** | Not Implemented | Fully Implemented | P0 |
| **User Profile Management** | Fully Implemented | Fully Implemented | - |
| **KYC Workflow** | Partially Implemented | Fully Implemented | P1 |
| **Account Management** | Partially Implemented | Fully Implemented | P1 |
| **Double-Entry Ledger** | **Not Implemented** | **Fully Implemented** | **P0** |
| **Fund Transfers (Saga)** | Partially Implemented | Fully Implemented | P1 |
| **Beneficiary Management** | Not Implemented | Fully Implemented | P1 |
| **Notifications (Email)** | Partially Implemented | Fully Implemented | P1 |
| **Notifications (SMS/Push)** | Not Implemented | Fully Implemented | P2 |
| **PDF Statements** | Not Implemented | Fully Implemented | P1 |
| **Fixed Deposits** | Not Implemented | Fully Implemented | P1 |
| **Fraud Detection** | Not Implemented | Fully Implemented | P1 |
| **External Payment Rails** | Not Implemented | Sandbox Integrated | P1 |
| **Observability Stack** | Partially Implemented | Fully Implemented | P1 |
| **CI/CD Pipeline** | Not Implemented | Fully Implemented | P0 |
| **Kubernetes Deployment** | Not Implemented | Fully Implemented | P1 |
| **Disaster Recovery** | Not Implemented | Fully Implemented | P1 |
| **Security Hardening** | Partially Implemented | Fully Implemented | P0 |

---

## Migration Scripts Applied

| Migration | Description | Status |
|-----------|-------------|--------|
| V5__fix_idempotency_user_scope.sql | Add auth_user_id to idempotency_keys, unique constraint | ✅ Applied |
| V6__fix_statement_txn_ref_unique.sql | Change txn_ref unique to composite (account_number, txn_ref) | ✅ Applied |
| V7__add_balance_constraints.sql | Add CHECK constraints for balance >= 0 and minimums | ✅ Applied |
| V8__add_general_ledger.sql | Create ledger_accounts, journal_entries, journal_lines with balance trigger | ✅ Applied |

---

## Security Fixes Applied

| Issue | Fix | Verification |
|-------|-----|--------------|
| SEC-001 | Rate limiting on /login, /register, /verify, /resend | 429 returned after threshold |
| SEC-002 | Verification code: 8-char alphanumeric | 36^8 = 2.8T combinations |
| SEC-009 | Account service: all endpoints authenticated | 401 without JWT |
| SEC-010 | User service: all endpoints authenticated | 401 without JWT |
| SEC-015 | JWT secret validation at startup | App fails fast if weak/missing |
| SEC-016 | Removed System.out.println from AuthService | No plaintext in logs |

---

## Next Release Target: [0.2.0] - Core Banking Foundation

Planned for Week 6 (per IMPLEMENTATION_ROADMAP.md Phase 1)

### Scope
- [ ] Account lockout after failed logins (SEC-003)
- [ ] Password policy enforcement (SEC-004)
- [ ] Password reset flow (SEC-005)
- [ ] TOTP MFA support (SEC-006)
- [ ] Refresh token revocation on password change (SEC-007)
- [ ] AccountService.credit() ownership fix (SEC-011)
- [ ] RBAC with @PreAuthorize (SEC-013)
- [ ] Double-entry ledger implementation (GAP-E-01 through GAP-E-06)
- [ ] Journal entry service with balanced posting
- [ ] Trial balance generation
- [ ] Daily reconciliation job

---

## Release Notes Template

```markdown
## [X.Y.Z] - YYYY-MM-DD

### Added
- New features

### Changed
- Changes to existing functionality

### Deprecated
- Soon-to-be removed features

### Removed
- Removed features

### Fixed
- Bug fixes

### Security
- Vulnerability fixes

### Database
- Migration scripts

### Infrastructure
- Deployment/ops changes
```