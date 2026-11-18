# CitiCore Architecture Decision Register

## Overview

This register tracks all Architecture Decision Records (ADRs) for the CitiCore Banking Platform. Each ADR captures a significant architectural decision with its context, alternatives considered, and consequences.

**Total ADRs:** 15 (DEC-001 through DEC-015)

---

## ADR Status Summary

| ADR ID | Title | Status | Date Approved | Supersedes |
|--------|-------|--------|---------------|------------|
| DEC-001 | Database Topology | ✅ **APPROVED** | 2026-11-18 | — |
| DEC-002 | Saga Pattern | ✅ **APPROVED** | 2026-11-18 | — |
| DEC-003 | KYC Service Ownership | ✅ **APPROVED** | 2026-11-18 | — |
| DEC-004 | Case Assignment | ✅ **APPROVED** | 2026-11-18 | — |
| DEC-005 | SoD Enforcement Location | ✅ **APPROVED** | 2026-11-18 | — |
| DEC-006 | JWT Claims Size | ✅ **APPROVED** | 2026-11-18 | — |
| DEC-007 | Ledger Service | ✅ **APPROVED** | 2026-11-18 | — |
| DEC-008 | Compliance Service | ✅ **APPROVED** | 2026-11-18 | — |
| DEC-009 | MFA Methods | ✅ **APPROVED** | 2026-11-18 | — |
| DEC-010 | Document Malware Scanning | ✅ **APPROVED** | 2026-11-18 | — |
| DEC-011 | MySQL Encryption | ✅ **APPROVED** | 2026-11-18 | — |
| DEC-012 | Secrets Management | ✅ **APPROVED** | 2026-11-18 | — |
| DEC-013 | Database TLS | ✅ **APPROVED** | 2026-11-18 | — |
| DEC-014 | Service Discovery | ✅ **APPROVED** | 2026-11-18 | — |
| DEC-015 | KYC Vendor Integration | ✅ **APPROVED** | 2026-11-18 | — |

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
| DEC-001 | DEC-011, DEC-013 | CITICORE_TARGET_STATE_ARCHITECTURE.md §5, Database Architecture |
| DEC-002 | DEC-005 | CITICORE_TARGET_STATE_ARCHITECTURE.md §8, Financial Transaction Processing |
| DEC-003 | DEC-004, DEC-005, DEC-015 | KYC_ARCHITECTURE.md §1, §6, §12 |
| DEC-004 | DEC-003, DEC-005 | KYC_ARCHITECTURE.md §6.3.2 |
| DEC-005 | DEC-002, DEC-003, DEC-004 | RBAC_ARCHITECTURE.md §5, §7; KYC_ARCHITECTURE.md §5 |
| DEC-006 | — | RBAC_ARCHITECTURE.md §8 |
| DEC-007 | — | CITICORE_TARGET_STATE_ARCHITECTURE.md §4.2.7 |
| DEC-008 | DEC-015 | CITICORE_TARGET_STATE_ARCHITECTURE.md §4.2.8 |
| DEC-009 | — | CITICORE_TARGET_STATE_ARCHITECTURE.md §10.1 |
| DEC-010 | — | KYC_ARCHITECTURE.md §9 |
| DEC-011 | DEC-001, DEC-013 | CITICORE_TARGET_STATE_ARCHITECTURE.md §5.3 |
| DEC-012 | — | CITICORE_TARGET_STATE_ARCHITECTURE.md §1.4, §4.2 |
| DEC-013 | DEC-001, DEC-011 | CITICORE_TARGET_STATE_ARCHITECTURE.md §5.3 |
| DEC-014 | — | CITICORE_TARGET_STATE_ARCHITECTURE.md §4.1, §6.4 |
| DEC-015 | DEC-003, DEC-008 | KYC_ARCHITECTURE.md §2.5 |

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