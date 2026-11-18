# CitiCore Architecture Decision Register

## Overview

This register tracks all Architecture Decision Records (ADRs) for the CitiCore Banking Platform. Each ADR captures a significant architectural decision with its context, alternatives considered, and consequences.

**Total ADRs:** 15 (DEC-001 through DEC-015)

---

## ADR Status Summary

| ADR ID | Title | Status | Date Approved | Document |
|--------|-------|--------|---------------|----------|
| DEC-001 | Database Topology | ✅ **APPROVED** | 2026-11-18 | [adrs/ADR-001_DATABASE_TOPOLOGY.md](adrs/ADR-001_DATABASE_TOPOLOGY.md) |
| DEC-002 | Saga Pattern | ✅ **APPROVED** | 2026-11-18 | [adrs/ADR-002_SAGA_PATTERN.md](adrs/ADR-002_SAGA_PATTERN.md) |
| DEC-003 | KYC Service Ownership | ✅ **APPROVED** | 2026-11-18 | [adrs/ADR-003_KYC_SERVICE_OWNERSHIP.md](adrs/ADR-003_KYC_SERVICE_OWNERSHIP.md) |
| DEC-004 | Case Assignment | ✅ **APPROVED** | 2026-11-18 | [adrs/ADR-004_CASE_ASSIGNMENT.md](adrs/ADR-004_CASE_ASSIGNMENT.md) |
| DEC-005 | SoD Enforcement Location | ✅ **APPROVED** | 2026-11-18 | [adrs/ADR-005_SOD_ENFORCEMENT.md](adrs/ADR-005_SOD_ENFORCEMENT.md) |
| DEC-006 | JWT Claims Size | ✅ **APPROVED** | 2026-11-18 | [adrs/ADR-006_JWT_CLAIMS_SIZE.md](adrs/ADR-006_JWT_CLAIMS_SIZE.md) |
| DEC-007 | Ledger Service | ✅ **APPROVED** | 2026-11-18 | [adrs/ADR-007_LEDGER_SERVICE.md](adrs/ADR-007_LEDGER_SERVICE.md) |
| DEC-008 | Compliance Service | ✅ **APPROVED** | 2026-11-18 | [adrs/ADR-008_COMPLIANCE_SERVICE.md](adrs/ADR-008_COMPLIANCE_SERVICE.md) |
| DEC-009 | MFA Methods | ✅ **APPROVED** | 2026-11-18 | [adrs/ADR-009_MFA_METHODS.md](adrs/ADR-009_MFA_METHODS.md) |
| DEC-010 | Document Malware Scanning | ✅ **APPROVED** | 2026-11-18 | [adrs/ADR-010_MALWARE_SCANNING.md](adrs/ADR-010_MALWARE_SCANNING.md) |
| DEC-011 | MySQL Encryption | ✅ **APPROVED** | 2026-11-18 | [adrs/ADR-011_MYSQL_ENCRYPTION.md](adrs/ADR-011_MYSQL_ENCRYPTION.md) |
| DEC-012 | Secrets Management | ✅ **APPROVED** | 2026-11-18 | [adrs/ADR-012_SECRETS_MANAGEMENT.md](adrs/ADR-012_SECRETS_MANAGEMENT.md) |
| DEC-013 | Database TLS | ✅ **APPROVED** | 2026-11-18 | [adrs/ADR-013_DATABASE_TLS.md](adrs/ADR-013_DATABASE_TLS.md) |
| DEC-014 | Service Discovery | ✅ **APPROVED** | 2026-11-18 | [adrs/ADR-014_SERVICE_DISCOVERY.md](adrs/ADR-014_SERVICE_DISCOVERY.md) |
| DEC-015 | KYC Vendor Integration | ✅ **APPROVED** | 2026-11-18 | [adrs/ADR-015_KYC_VENDOR_INTEGRATION.md](adrs/ADR-015_KYC_VENDOR_INTEGRATION.md) |

---

## Decision Category Index

### Database & Storage
- **DEC-001**: Database Topology — Single RDS with schemas
- **DEC-011**: MySQL Encryption — RDS KMS + app-level envelope encryption
- **DEC-013**: Database TLS — Mandatory TLS for RDS

### Messaging & Transactions
- **DEC-002**: Saga Pattern — Hybrid orchestration/choreography

### Security
- **DEC-010**: Document Malware Scanning — ClamAV with abstraction
- **DEC-012**: Secrets Management — AWS Secrets Manager
- **DEC-006**: JWT Claims Size — Permissions in token
- **DEC-009**: MFA Methods — TOTP primary, Email fallback

### Services & Integration
- **DEC-003**: KYC Service Ownership — Dedicated KYC Workflow Service
- **DEC-004**: Case Assignment — Hybrid auto-assign with manager override
- **DEC-005**: SoD Enforcement Location — KYC Workflow Service + Gateway
- **DEC-007**: Ledger Service — Separate microservice
- **DEC-008**: Compliance Service — Build abstraction, integrate vendor APIs
- **DEC-015**: KYC Vendor Integration — Mock provider initially

### Infrastructure
- **DEC-014**: Service Discovery — K8s DNS production, Eureka local

---

## Cross-References

| ADR | Related ADRs | Related Documents |
|-----|--------------|-------------------|
| DEC-001 | DEC-011, DEC-013 | `architecture/architecture.md` §5, Database Architecture |
| DEC-002 | DEC-005 | `architecture/architecture.md` §8, Financial Transaction Processing |
| DEC-003 | DEC-004, DEC-005, DEC-015 | `business-workflows/kyc-workflow.md` §1, §6, §12 |
| DEC-004 | DEC-003, DEC-005 | `business-workflows/kyc-workflow.md` §6.3.2 |
| DEC-005 | DEC-002, DEC-003, DEC-004 | `security/rbac.md` §5, §7; `business-workflows/kyc-workflow.md` §5 |
| DEC-006 | — | `security/rbac.md` §8 |
| DEC-007 | — | `architecture/architecture.md` §4.2.7 |
| DEC-008 | DEC-015 | `architecture/architecture.md` §4.2.8 |
| DEC-009 | — | `architecture/architecture.md` §10.1 |
| DEC-010 | — | `business-workflows/kyc-workflow.md` §9 |
| DEC-011 | DEC-001, DEC-013 | `architecture/architecture.md` §5.3 |
| DEC-012 | — | `architecture/architecture.md` §1.4, §4.2 |
| DEC-013 | DEC-001, DEC-011 | `architecture/architecture.md` §5.3 |
| DEC-014 | — | `architecture/architecture.md` §4.1, §6.4 |
| DEC-015 | DEC-003, DEC-008 | `business-workflows/kyc-workflow.md` §2.5 |

---

## Implementation Priority

| Priority | ADRs | Rationale |
|----------|------|-----------|
| **P0 - Critical (Phase 0)** | DEC-001, DEC-011, DEC-012, DEC-013 | Foundation: database, encryption, secrets, TLS |
| **P1 - High (Phase 1)** | DEC-002, DEC-003, DEC-010, DEC-015 | Core workflows: saga, KYC service, malware scanning, vendor abstraction |
| **P2 - Medium (Phase 2)** | DEC-004, DEC-005, DEC-006, DEC-009 | Operational features: assignment, SoD, JWT claims, MFA |
| **P3 - Low (Phase 3+)** | DEC-007, DEC-008, DEC-014 | Future services: ledger, compliance, service mesh transition |

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-11-18 | Architecture Team | Initial ADR register with all 15 decisions |

---

**END OF DOCUMENT**