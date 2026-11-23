# CitiCore Architecture Changes - Change Summary

## Executive Summary

This document summarizes the architecture changes made to incorporate **Comprehensive RBAC** and **KYC Maker-Checker Workflow** into the existing CitiCore Banking Platform architecture. The changes update the target state architecture documentation, create dedicated RBAC and KYC architecture documents, and provide updated Mermaid diagrams.

**All 8 pending Architecture Decision Records (ADRs) have been APPROVED** as of 2026-11-18.

**Version:** 3.0  
**Date:** 2026-11-18  
**Status:** Approved  

---

## 1. Existing Design Decisions Retained

The following architectural decisions from the existing design have been **preserved without modification**:

| # | Decision | Document Reference | Rationale |
|---|----------|-------------------|-----------|
| 1 | **Schema per Service** | Section 1.3, 4.2, 5.1 | Maintains service autonomy, data ownership within single RDS |
| 2 | **Event-Driven Architecture (Kafka)** | Section 6.3, 7.1 | Proven reliable async communication |
| 3 | **Saga Pattern (Hybrid)** | Section 8.1 | Fund transfers use orchestration; notifications use choreography |
| 4 | **Primary/Replica Routing** | Section 5.4 | Read scaling for Account Service |
| 5 | **Redis Cache-Aside** | Section 5.4 | Balance query performance |
| 6 | **Transactional Outbox Pattern** | Section 7.3 | Reliable event publishing |
| 7 | **Pessimistic Locking** | Section 4.2.3 | Account balance consistency |
| 8 | **Spring Cloud Gateway** | Section 4.2.9 | Reactive routing, resilience |
| 9 | **Eureka Service Discovery (Local Only)** | Section 4.2.9 | Local/Docker Compose service registry |
| 10 | **Double-Entry Ledger (Planned)** | Section 9 | Regulatory compliance requirement |
| 11 | **JWT Authentication (HS256)** | Section 10.1, 10.2 | Stateless auth working |
| 12 | **Refresh Token Rotation** | Section 10.3 | Token security |
| 13 | **Service-to-Service JWT Propagation** | Section 10.5 | Independent service validation |
| 14 | **Docker Compose / K8s Deployment** | Section 11 | Infrastructure as Code |
| 15 | **Observability Stack (Prometheus, Grafana, Zipkin)** | Section 12 | Monitoring working |
| 16 | **CI/CD Pipeline Design** | Section 11.5 | Deployment automation |

---

## 2. New RBAC Decisions

The following **new RBAC architecture decisions** have been introduced:

| # | Decision | Details | Impact |
|---|----------|---------|--------|
| **RBAC-01** | **9 System Roles** (was 5) | CUSTOMER, KYC_OFFICER, KYC_MANAGER, SUPPORT_AGENT, OPERATIONS_OFFICER, COMPLIANCE_OFFICER, ADMIN, AUDITOR, SERVICE | Supports separation of duties, granular access |
| **RBAC-02** | **28 Granular Permissions** | Resource-action-scope model (e.g., `KYC_CASE_APPROVE`, `CUSTOMER_PROFILE_READ_ALL`) | Least-privilege enforcement |
| **RBAC-03** | **Explicit Role-Permission Matrix** | 9×28 matrix documented (Section 3.4 in Target Architecture) | Auditable, configurable |
| **RBAC-04** | **Resource Ownership & Access Scopes** | Own, Assigned, Authorized, Limited, All, None | Data isolation per role |
| **RBAC-05** | **User-to-Role Assignment Model** | Self-registration (CUSTOMER), Admin assignment (staff), Manager case assignment (officers) | Clear ownership |
| **RBAC-06** | **JWT Claims with Permissions** | Roles + permissions + case assignments in 15min token | Stateless authorization |
| **RBAC-07** | **Revocation Strategy** | 15min JWT + Redis cache eviction + Refresh token revocation + Emergency blocklist | Near-real-time (≤15min) |
| **RBAC-08** | **API Gateway Coarse RBAC** | Endpoint pattern + role check at gateway | First line of defense |
| **RBAC-09** | **Backend Independent Enforcement** | Each service validates JWT, checks @PreAuthorize, enforces ownership | No gateway dependency |
| **RBAC-10** | **Custom PermissionEvaluator** | SpEL + ABAC for resource-scoped decisions | Dynamic authorization |
| **RBAC-11** | **Separation of Duties Engine** | KYC Workflow Service enforces Officer ≠ Manager | Regulatory compliance |
| **RBAC-12** | **Security Audit Logging** | Immutable audit trail for authz decisions, role changes, SoD violations | Compliance, forensics |
| **RBAC-13** | **Admin Access Controls** | MFA required, dual approval for critical actions, break-glass procedure | Privileged access governance |
| **RBAC-14** | **API Endpoint Access Matrix** | 130+ endpoint/role combinations documented | Implementation reference |

---

## 3. New KYC Workflow Decisions

The following **new KYC Maker-Checker workflow decisions** have been introduced:

| # | Decision | Details | Impact |
|---|----------|---------|--------|
| **KYC-01** | **13-State KYC Lifecycle** | DRAFT → SUBMITTED → PENDING_REVIEW → UNDER_REVIEW → MORE_INFO_REQUIRED → RESUBMITTED → RECOMMENDED_APPROVAL/REJECTION → PENDING_MANAGER_APPROVAL → APPROVED/REJECTED/ESCALATED | Complete traceability |
| **KYC-02** | **Maker-Checker Enforcement** | Officer reviews & recommends; Manager approves/rejects; **Officer ≠ Manager** enforced | Regulatory requirement |
| **KYC-03** | **Case Assignment Model** | Manager assigns to Officer; Auto-assignment (round-robin) with override; Reassignment support | Workload distribution |
| **KYC-04** | **SLA Monitoring** | Per-stage deadlines (1hr assign, 24hr review, 48hr decision); Breach alerts; Auto-escalation | Operational excellence |
| **KYC-05** | **Immutable Audit Trail** | Every transition: actor, role, timestamp, prev/new state, reason, metadata; Hash-chained | Full traceability |
| **KYC-06** | **Document Access Control** | Pre-signed S3 URLs (15min); Role-based visibility; SSE-KMS encryption; Malware scanning (ClamAV) | Data protection |
| **KYC-07** | **KYC Workflow Service (New)** | Port 8087, `citicore_kycdb`, owns case lifecycle, state machine, assignments | Separation of concerns |
| **KYC-08** | **KYC Events** | `kyc-case-topic` (lifecycle), `kyc-notification-topic` (customer/staff alerts) | Decoupled communication |
| **KYC-09** | **Account Creation Gating** | Account Service checks KYC via Feign + Kafka `KYC_APPROVED` event | Prevents accounts without KYC |
| **KYC-10** | **Permitted Rejection Reasons** | 15 categorized reasons with evidence requirements | Consistent, auditable decisions |
| **KYC-11** | **Escalation Path** | Manager → Compliance → Head of Compliance → CRO/CEO | Exception handling |
| **KYC-12** | **Operational Procedures** | Daily/weekly/monthly runbooks, incident response procedures | Production readiness |
| **KYC-13** | **Mock KYC Verification Provider** | Deterministic mock for testing; pluggable interface for future real vendors | Testability, vendor flexibility |

---

## 4. Existing Design Conflicts Discovered & Resolved

The following conflicts were discovered during the architecture review and have been **resolved in the updated documentation**:

| # | Conflict | Original State | Resolution | Document Reference |
|---|----------|---------------|------------|-------------------|
| **CON-01** | User Service SecurityConfig references `/accounts/**` | Copy-paste bug - all endpoints public | Fixed: Updated to `/users/**` patterns | SECURITY_AUDIT.md SEC-010, Target Architecture Section 3.6 |
| **CON-02** | Account Service permits all requests | `.permitAll()` on all endpoints | Fixed: Require authentication + RBAC | SECURITY_AUDIT.md SEC-009, Target Architecture Section 3.6 |
| **CON-03** | Saga: Choreography vs Orchestration ambiguity | Docs say choreography, Transaction Service acts as orchestrator | Clarified: **Hybrid** - Orchestration for transfers, Choreography for notifications | ARCHITECTURE_REVIEW_OBSERVATIONS.md OBS-CD-001, Target Architecture Section 8 |
| **CON-04** | Cross-Schema Referential Integrity | Single RDS but FK refs across schemas | Documented: App-level validation + reconciliation jobs | Target Architecture Section 5.4 |
| **CON-05** | Idempotency Key Not User-Scoped | Global unique constraint | Fixed: Composite unique `(auth_user_id, idempotency_key)` | SECURITY_AUDIT.md SEC-012, IMPLEMENTATION_ROADMAP.md P0-08 |
| **CON-06** | Account Statement `txn_ref` Global Unique | Prevents transfer debit+credit with same txnRef | Fixed: Composite unique `(account_number, txn_ref)` | DATABASE_AUDIT.md BUG-019, IMPLEMENTATION_ROADMAP.md P0-15 |
| **CON-07** | KYC Review in User Service | KYC logic mixed with profile management | **Moved to dedicated KYC Workflow Service** (Port 8087) | Target Architecture Section 2.1, 4.2.3 |
| **CON-08** | Simple KYC Status Enum | User Service has `KYC_STATUS` enum | **Replaced with full KYC Case State Machine** (13 states) | KYC_ARCHITECTURE.md Section 3 |
| **CON-09** | No Separation of Duties | Single admin could review and approve | **Enforced: Officer ≠ Manager for same case** | KYC_ARCHITECTURE.md Section 5, RBAC_ARCHITECTURE.md Section 5 |
| **CON-10** | No Document Access Control | Direct S3 access possible | **All access via KYC Workflow Service + pre-signed URLs** | KYC_ARCHITECTURE.md Section 9 |

---

## 5. Approved Architecture Decision Records (ADRs)

All 8 pending ADRs have been **APPROVED** with the exact choices and constraints specified below:

| # | Decision ID | Decision Point | Status | Approved Choice |
|---|-------------|----------------|--------|-----------------|
| **1** | **DEC-001** | Database Topology | ✅ **APPROVED** | Single AWS RDS for MySQL deployment with separate schemas per microservice; separate credentials per service; logical database ownership; APIs/events for cross-service communication; future migration path documented |
| **2** | **DEC-002** | Saga Pattern | ✅ **APPROVED** | Hybrid: Fund transfers = Orchestration (Transaction Service); Notifications = Choreography; Audit events = Async consumption; Outbox pattern for reliable publishing |
| **3** | **DEC-010** | Document Malware Scanning | ✅ **APPROVED** | Scanner abstraction with ClamAV initial implementation; quarantine until clean; fail-closed; pluggable for commercial vendors |
| **4** | **DEC-011** | MySQL Encryption | ✅ **APPROVED** | AWS RDS KMS storage encryption (customer-managed key); selective application-level envelope encryption for sensitive PII; no native MySQL TDE (not supported on RDS MySQL) |
| **5** | **DEC-012** | Secrets Management | ✅ **APPROVED** | AWS Secrets Manager; IAM workload identity for EKS; least-privilege; rotation policies; no hardcoded secrets |
| **6** | **DEC-013** | Database TLS | ✅ **APPROVED** | Mandatory TLS for all production RDS MySQL; `require_secure_transport=ON`; certificate validation; local dev exceptions documented |
| **7** | **DEC-014** | Service Discovery | ✅ **APPROVED** | Kubernetes DNS (CoreDNS) for EKS production; Eureka for local Docker Compose only; dual-write transition; no Eureka code removal yet |
| **8** | **DEC-015** | KYC Vendor Integration | ✅ **APPROVED** | Internal KYC workflow with mock provider initially; India market (PAN/Aadhaar); deterministic mock responses; pluggable adapter; no real vendor integration now |

**Previously Approved (Preserved):**
- DEC-003: KYC Service Ownership → Dedicated KYC Workflow Service ✅
- DEC-004: Case Assignment → Hybrid: Auto-assign with Manager override ✅
- DEC-005: SoD Enforcement Location → KYC Workflow Service + Gateway ✅
- DEC-006: JWT Claims Size → Permissions in JWT (15min expiry) ✅
- DEC-007: Ledger Service → Separate microservice ✅
- DEC-008: Compliance Service → Build abstraction; integrate vendor APIs ✅
- DEC-009: MFA Methods → Email OTP primary ✅

---

## 6. Documentation Changes Summary

### 6.1 Updated Documents

| Document | Version | Key Changes |
|----------|---------|-------------|
| `architecture/docs/CITICORE_TARGET_STATE_ARCHITECTURE.md` | 1.0 → 3.0 | **Major rewrite**: Schema per Service (1.3), Hybrid Saga (8.1), AWS RDS topology (5.1), K8s DNS discovery (4.1, 6.4), Security controls updated (10.6), KYC vendor mock (11.1) |
| `architecture/docs/ARCHITECTURE_DIAGRAMS.md` | 1.0 → 3.0 | Updated diagrams for: Database topology, Service topology, Hybrid saga, Service discovery, KYC malware scanning flow |
| `architecture/docs/RBAC_ARCHITECTURE.md` | 2.0 | No changes needed (already aligned) |
| `architecture/docs/KYC_ARCHITECTURE.md` | 2.0 → 3.0 | Added KYC vendor mock provider (2.5), Updated malware scanning flow (9.2), Updated security controls (9.4) |

### 6.2 New Documents Created

| Document | Purpose |
|----------|---------|
| `architecture/docs/ADR_REGISTER.md` | Decision register with all 15 ADRs (DEC-001 through DEC-015) |
| `architecture/docs/ADR-001_DATABASE_TOPOLOGY.md` | DEC-001: Single RDS with schemas |
| `architecture/docs/ADR-002_SAGA_PATTERN.md` | DEC-002: Hybrid saga architecture |
| `architecture/docs/ADR-010_MALWARE_SCANNING.md` | DEC-010: ClamAV with abstraction |
| `architecture/docs/ADR-011_MYSQL_ENCRYPTION.md` | DEC-011: RDS KMS + app-level encryption |
| `architecture/docs/ADR-012_SECRETS_MANAGEMENT.md` | DEC-012: AWS Secrets Manager |
| `architecture/docs/ADR-013_DATABASE_TLS.md` | DEC-013: Mandatory TLS for RDS |
| `architecture/docs/ADR-014_SERVICE_DISCOVERY.md` | DEC-014: K8s DNS production, Eureka local |
| `architecture/docs/ADR-015_KYC_VENDOR_INTEGRATION.md` | DEC-015: Mock provider initially |

### 6.3 Documents Referenced (Unchanged)

| Document | Status |
|----------|--------|
| `PROJECT_ARCHITECTURE.md` | Referenced as baseline |
| `SECURITY_AUDIT.md` | Referenced for security gaps |
| `DATABASE_AUDIT.md` | Referenced for schema issues |
| `BANKING_FUNCTIONALITY_GAP_ANALYSIS.md` | Referenced for functional gaps |
| `IMPLEMENTATION_ROADMAP.md` | Referenced for implementation priority |
| `PRODUCTION_READINESS.md` | Referenced for deployment gaps |
| `ARCHITECTURE_REVIEW_OBSERVATIONS.md` | Referenced for discovered conflicts |

---

## 7. Implementation Impact Assessment

### 7.1 New Components Required

| Component | Type | Effort | Dependencies |
|-----------|------|--------|--------------|
| KYC Workflow Service | New Microservice | High (6-8 weeks) | User Service, Auth Service, Kafka, MySQL |
| RBAC Infrastructure | Library/Framework | Medium (3-4 weeks) | Spring Security, Redis |
| PermissionEvaluator | Custom Component | Low (1-2 weeks) | Spring Security |
| Audit Log Hash Chaining | Database + Service | Medium (2-3 weeks) | MySQL, KYC Workflow Service |
| Document Malware Scanning | S3 Object Lambda + ClamAV | Low (1-2 weeks) | AWS S3, ClamAV |
| SLA Monitoring | Scheduler + Alerts | Medium (2-3 weeks) | KYC Workflow Service, AlertManager |
| AWS Secrets Manager Integration | Config/Code | Low (1 week) | Spring Cloud AWS, IAM |
| RDS TLS Configuration | Config | Low (1 week) | Spring Boot, RDS CA certs |

### 7.2 Modified Components

| Component | Changes | Effort |
|-----------|---------|--------|
| API Gateway | Coarse RBAC filter, rate limiting per role | Medium |
| Auth Service | JWT claims enhancement (permissions, case assignments), MFA | Medium |
| User Service | SecurityConfig fix, remove KYC review logic, Feign for KYC status | Low |
| Account Service | KYC status check via Feign + Kafka event, dual balance | Medium |
| Transaction Service | Orchestration logic for fund transfers | Medium |
| Notification Service | New KYC notification templates, consume kyc-case-topic | Low |
| Config Server | AWS Secrets Manager integration | Low |
| All Services | DB credentials from Secrets Manager; RDS TLS config | Low |

### 7.3 Database Changes

| Database/Schema | New Tables | Migrations |
|-----------------|------------|------------|
| `citicore_kycdb` | kyc_cases, kyc_case_assignments, kyc_case_history, kyc_review_findings, kyc_document_refs | New (V1-V5) |
| `citicore_authdb` | login_attempts, user_role_audit | V2-V3 |
| `citicore_userdb` | No new tables (KYC docs remain) | N/A |
| `citicore_account` | account_holds | V2 |

### 7.4 Infrastructure Changes

| Area | Change |
|------|--------|
| **RDS** | Single MySQL 8.0 deployment; customer-managed KMS key; `require_secure_transport=ON`; separate schemas/users per service |
| **EKS** | IRSA for Secrets Manager access; CoreDNS for service discovery; NetworkPolicies for zero-trust |
| **S3** | Quarantine + Clean buckets; SSE-KMS; Object Lambda for ClamAV scanning |
| **Secrets Manager** | DB credentials, JWT secret, API keys; rotation policies |
| **KMS** | RDS storage encryption key; envelope encryption keys for PII |

---

## 8. Review Checklist for Stakeholders

### 8.1 Architecture Review

- [x] RBAC role definitions align with organizational structure
- [x] Permission granularity supports least-privilege without operational burden
- [x] KYC state machine covers all regulatory required states
- [x] Maker-checker separation meets PMLA/RBI requirements
- [x] SLA definitions are operationally achievable
- [x] Audit trail integrity (hash chaining) meets SOX/regulatory needs
- [x] Document security (SSE-KMS, pre-signed URLs, malware scanning) is sufficient

### 8.2 Security Review

- [x] Default-deny enforced at all layers
- [x] JWT 15min expiry + refresh rotation is acceptable risk window
- [x] Revocation latency (≤15min) meets security requirements
- [x] SoD enforcement cannot be bypassed
- [x] Admin break-glass procedure is auditable
- [x] Encryption at rest/in transit decisions approved (RDS KMS + TLS)

### 8.3 Operations Review

- [x] Daily/weekly/monthly operational procedures are feasible
- [x] SLA breach alerting won't cause alert fatigue
- [x] Case assignment auto-distribution logic is fair
- [x] Escalation paths have clear ownership
- [x] Audit integrity verification job schedule is appropriate

### 8.4 Implementation Review

- [x] KYC Workflow Service scope is bounded correctly
- [x] Schema per service principle maintained
- [x] Event-driven communication doesn't create circular dependencies
- [x] Migration path for existing KYC data defined
- [x] Backward compatibility for API consumers considered

---

## 9. Next Steps

### Immediate (Documentation Complete)
1. ✅ All 8 pending ADRs approved and documented
2. ✅ Architecture documentation updated consistently
3. ✅ Mermaid diagrams updated
4. ✅ Decision register created

### Upon Implementation Authorization
1. Update `IMPLEMENTATION_ROADMAP.md` with RBAC/KYC tasks (Phase 0-1)
2. Create GitHub Epics/Stories for implementation
3. Begin SPIKE for KYC Workflow Service domain model
4. Begin SPIKE for PermissionEvaluator implementation
5. Provision AWS resources (RDS, Secrets Manager, KMS, S3 buckets)

---

## 10. Implementation Gaps

The following gaps exist between current implementation and approved target state:

| Gap | Description | Priority |
|-----|-------------|----------|
| **Database** | Current: Multiple MySQL instances in Docker Compose; Target: Single RDS with schemas | High |
| **Secrets** | Current: Env vars in docker-compose; Target: AWS Secrets Manager | High |
| **TLS** | Current: No TLS for DB connections; Target: Mandatory RDS TLS | High |
| **Encryption** | Current: No encryption at rest; Target: RDS KMS + app-level envelope encryption | High |
| **Service Discovery** | Current: Eureka in all environments; Target: K8s DNS (prod), Eureka (local) | Medium |
| **KYC Service** | Current: KYC logic in User Service; Target: Dedicated KYC Workflow Service | High |
| **Saga** | Current: Choreography; Target: Hybrid (Orchestration for transfers) | Medium |
| **Malware Scanning** | Current: None; Target: ClamAV via S3 Object Lambda | Medium |
| **Secrets Rotation** | Current: None; Target: Automated rotation policies | Low |
| **Audit Hash Chaining** | Current: None; Target: Immutable hash-chained audit logs | Medium |

---

## 11. Dependencies & Future Considerations

### Dependencies
1. **AWS Account Setup** - RDS, EKS, MSK, ElastiCache, Secrets Manager, KMS, S3
2. **IRSA Configuration** - IAM roles for service accounts
3. **Network Topology** - VPC, subnets, security groups, NACLs
4. **CI/CD Pipeline** - GitHub Actions/GitLab CI with AWS deployment
5. **Monitoring Stack** - Prometheus, Grafana, Loki, Tempo, AlertManager

### Future Migration Considerations
| Phase | Trigger | Action |
|-------|---------|--------|
| **Phase 2** | Scale/Isolation needs | Split high-throughput schemas to dedicated RDS instances |
| **Phase 3** | Regulatory/Compliance | Independent database deployments per service with VPC isolation |
| **Phase 4** | Multi-region | Cross-region read replicas; active-active for critical schemas |
| **KYC Vendor** | Regulatory requirement | Real vendor integration (Jumio/Onfido) via pluggable adapter |
| **Service Mesh** | mTLS/Traffic needs | Istio/Linkerd for zero-trust networking |

### Unresolved Architectural Questions
1. **Multi-tenancy** - Not addressed; future requirement for white-label banking
2. **Data Residency** - Cross-border data flow not specified
3. **Disaster Recovery** - RPO/RTO targets need stakeholder alignment
4. **API Versioning** - Strategy for breaking changes not finalized
5. **Real-time Fraud Detection** - Placeholder for future ML-based system

---

## 12. Appendix: File Inventory

### Updated Files
```
architecture/docs/CITICORE_TARGET_STATE_ARCHITECTURE.md  (v3.0)
architecture/docs/ARCHITECTURE_DIAGRAMS.md                (v3.0)
architecture/docs/KYC_ARCHITECTURE.md                     (v3.0)
architecture/docs/CHANGE_SUMMARY.md                       (v3.0)
```

### New Files (ADRs)
```
architecture/docs/ADR_REGISTER.md
architecture/docs/ADR-001_DATABASE_TOPOLOGY.md
architecture/docs/ADR-002_SAGA_PATTERN.md
architecture/docs/ADR-010_MALWARE_SCANNING.md
architecture/docs/ADR-011_MYSQL_ENCRYPTION.md
architecture/docs/ADR-012_SECRETS_MANAGEMENT.md
architecture/docs/ADR-013_DATABASE_TLS.md
architecture/docs/ADR-014_SERVICE_DISCOVERY.md
architecture/docs/ADR-015_KYC_VENDOR_INTEGRATION.md
```

### Existing Files (Unchanged)
```
architecture/docs/RBAC_ARCHITECTURE.md (v2.0)
architecture/docs/ARCHITECTURE_REVIEW_OBSERVATIONS.md
architecture/DELIVERABLES_INDEX.md
PROJECT_ARCHITECTURE.md
SECURITY_AUDIT.md
DATABASE_AUDIT.md
BANKING_FUNCTIONALITY_GAP_ANALYSIS.md
IMPLEMENTATION_ROADMAP.md
PRODUCTION_READINESS.md
```

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-11-18 | Architecture Team | Initial change summary |
| 2.0 | 2026-11-18 | Architecture Team | Major update with RBAC/KYC |
| 3.0 | 2026-11-18 | Architecture Team | **All 8 pending ADRs approved**; documentation updated; ADR documents created; implementation gaps identified |

---

**END OF DOCUMENT**

*This change summary reflects the approved target-state architecture. All 8 pending decisions (DEC-001, DEC-002, DEC-010, DEC-011, DEC-012, DEC-013, DEC-014, DEC-015) have been approved and documented. The 7 previously approved decisions (DEC-003 through DEC-009) are preserved. Stop after documentation updates. Do not begin implementation until explicitly authorized.*# CitiCore Architecture Changes - Change Summary

## Executive Summary

This document summarizes the architecture changes made to incorporate **Comprehensive RBAC** and **KYC Maker-Checker Workflow** into the existing CitiCore Banking Platform architecture. The changes update the target state architecture documentation, create dedicated RBAC and KYC architecture documents, and provide updated Mermaid diagrams.

**All 8 pending Architecture Decision Records (ADRs) have been APPROVED** as of 2026-11-18.

**Version:** 3.0  
**Date:** 2026-11-18  
**Status:** Approved  

---

## 1. Existing Design Decisions Retained

The following architectural decisions from the existing design have been **preserved without modification**:

| # | Decision | Document Reference | Rationale |
|---|----------|-------------------|-----------|
| 1 | **Schema per Service** | Section 1.3, 4.2, 5.1 | Maintains service autonomy, data ownership within single RDS |
| 2 | **Event-Driven Architecture (Kafka)** | Section 6.3, 7.1 | Proven reliable async communication |
| 3 | **Saga Pattern (Hybrid)** | Section 8.1 | Fund transfers use orchestration; notifications use choreography |
| 4 | **Primary/Replica Routing** | Section 5.4 | Read scaling for Account Service |
| 5 | **Redis Cache-Aside** | Section 5.4 | Balance query performance |
| 6 | **Transactional Outbox Pattern** | Section 7.3 | Reliable event publishing |
| 7 | **Pessimistic Locking** | Section 4.2.3 | Account balance consistency |
| 8 | **Spring Cloud Gateway** | Section 4.2.9 | Reactive routing, resilience |
| 9 | **Eureka Service Discovery (Local Only)** | Section 4.2.9 | Local/Docker Compose service registry |
| 10 | **Double-Entry Ledger (Planned)** | Section 9 | Regulatory compliance requirement |
| 11 | **JWT Authentication (HS256)** | Section 10.1, 10.2 | Stateless auth working |
| 12 | **Refresh Token Rotation** | Section 10.3 | Token security |
| 13 | **Service-to-Service JWT Propagation** | Section 10.5 | Independent service validation |
| 14 | **Docker Compose / K8s Deployment** | Section 11 | Infrastructure as Code |
| 15 | **Observability Stack (Prometheus, Grafana, Zipkin)** | Section 12 | Monitoring working |
| 16 | **CI/CD Pipeline Design** | Section 11.5 | Deployment automation |

---

## 2. New RBAC Decisions

The following **new RBAC architecture decisions** have been introduced:

| # | Decision | Details | Impact |
|---|----------|---------|--------|
| **RBAC-01** | **9 System Roles** (was 5) | CUSTOMER, KYC_OFFICER, KYC_MANAGER, SUPPORT_AGENT, OPERATIONS_OFFICER, COMPLIANCE_OFFICER, ADMIN, AUDITOR, SERVICE | Supports separation of duties, granular access |
| **RBAC-02** | **28 Granular Permissions** | Resource-action-scope model (e.g., `KYC_CASE_APPROVE`, `CUSTOMER_PROFILE_READ_ALL`) | Least-privilege enforcement |
| **RBAC-03** | **Explicit Role-Permission Matrix** | 9×28 matrix documented (Section 3.4 in Target Architecture) | Auditable, configurable |
| **RBAC-04** | **Resource Ownership & Access Scopes** | Own, Assigned, Authorized, Limited, All, None | Data isolation per role |
| **RBAC-05** | **User-to-Role Assignment Model** | Self-registration (CUSTOMER), Admin assignment (staff), Manager case assignment (officers) | Clear ownership |
| **RBAC-06** | **JWT Claims with Permissions** | Roles + permissions + case assignments in 15min token | Stateless authorization |
| **RBAC-07** | **Revocation Strategy** | 15min JWT + Redis cache eviction + Refresh token revocation + Emergency blocklist | Near-real-time (≤15min) |
| **RBAC-08** | **API Gateway Coarse RBAC** | Endpoint pattern + role check at gateway | First line of defense |
| **RBAC-09** | **Backend Independent Enforcement** | Each service validates JWT, checks @PreAuthorize, enforces ownership | No gateway dependency |
| **RBAC-10** | **Custom PermissionEvaluator** | SpEL + ABAC for resource-scoped decisions | Dynamic authorization |
| **RBAC-11** | **Separation of Duties Engine** | KYC Workflow Service enforces Officer ≠ Manager | Regulatory compliance |
| **RBAC-12** | **Security Audit Logging** | Immutable audit trail for authz decisions, role changes, SoD violations | Compliance, forensics |
| **RBAC-13** | **Admin Access Controls** | MFA required, dual approval for critical actions, break-glass procedure | Privileged access governance |
| **RBAC-14** | **API Endpoint Access Matrix** | 130+ endpoint/role combinations documented | Implementation reference |

---

## 3. New KYC Workflow Decisions

The following **new KYC Maker-Checker workflow decisions** have been introduced:

| # | Decision | Details | Impact |
|---|----------|---------|--------|
| **KYC-01** | **13-State KYC Lifecycle** | DRAFT → SUBMITTED → PENDING_REVIEW → UNDER_REVIEW → MORE_INFO_REQUIRED → RESUBMITTED → RECOMMENDED_APPROVAL/REJECTION → PENDING_MANAGER_APPROVAL → APPROVED/REJECTED/ESCALATED | Complete traceability |
| **KYC-02** | **Maker-Checker Enforcement** | Officer reviews & recommends; Manager approves/rejects; **Officer ≠ Manager** enforced | Regulatory requirement |
| **KYC-03** | **Case Assignment Model** | Manager assigns to Officer; Auto-assignment (round-robin) with override; Reassignment support | Workload distribution |
| **KYC-04** | **SLA Monitoring** | Per-stage deadlines (1hr assign, 24hr review, 48hr decision); Breach alerts; Auto-escalation | Operational excellence |
| **KYC-05** | **Immutable Audit Trail** | Every transition: actor, role, timestamp, prev/new state, reason, metadata; Hash-chained | Full traceability |
| **KYC-06** | **Document Access Control** | Pre-signed S3 URLs (15min); Role-based visibility; SSE-KMS encryption; Malware scanning (ClamAV) | Data protection |
| **KYC-07** | **KYC Workflow Service (New)** | Port 8087, `citicore_kycdb`, owns case lifecycle, state machine, assignments | Separation of concerns |
| **KYC-08** | **KYC Events** | `kyc-case-topic` (lifecycle), `kyc-notification-topic` (customer/staff alerts) | Decoupled communication |
| **KYC-09** | **Account Creation Gating** | Account Service checks KYC via Feign + Kafka `KYC_APPROVED` event | Prevents accounts without KYC |
| **KYC-10** | **Permitted Rejection Reasons** | 15 categorized reasons with evidence requirements | Consistent, auditable decisions |
| **KYC-11** | **Escalation Path** | Manager → Compliance → Head of Compliance → CRO/CEO | Exception handling |
| **KYC-12** | **Operational Procedures** | Daily/weekly/monthly runbooks, incident response procedures | Production readiness |
| **KYC-13** | **Mock KYC Verification Provider** | Deterministic mock for testing; pluggable interface for future real vendors | Testability, vendor flexibility |

---

## 4. Existing Design Conflicts Discovered & Resolved

The following conflicts were discovered during the architecture review and have been **resolved in the updated documentation**:

| # | Conflict | Original State | Resolution | Document Reference |
|---|----------|---------------|------------|-------------------|
| **CON-01** | User Service SecurityConfig references `/accounts/**` | Copy-paste bug - all endpoints public | Fixed: Updated to `/users/**` patterns | SECURITY_AUDIT.md SEC-010, Target Architecture Section 3.6 |
| **CON-02** | Account Service permits all requests | `.permitAll()` on all endpoints | Fixed: Require authentication + RBAC | SECURITY_AUDIT.md SEC-009, Target Architecture Section 3.6 |
| **CON-03** | Saga: Choreography vs Orchestration ambiguity | Docs say choreography, Transaction Service acts as orchestrator | Clarified: **Hybrid** - Orchestration for transfers, Choreography for notifications | ARCHITECTURE_REVIEW_OBSERVATIONS.md OBS-CD-001, Target Architecture Section 8 |
| **CON-04** | Cross-Schema Referential Integrity | Single RDS but FK refs across schemas | Documented: App-level validation + reconciliation jobs | Target Architecture Section 5.4 |
| **CON-05** | Idempotency Key Not User-Scoped | Global unique constraint | Fixed: Composite unique `(auth_user_id, idempotency_key)` | SECURITY_AUDIT.md SEC-012, IMPLEMENTATION_ROADMAP.md P0-08 |
| **CON-06** | Account Statement `txn_ref` Global Unique | Prevents transfer debit+credit with same txnRef | Fixed: Composite unique `(account_number, txn_ref)` | DATABASE_AUDIT.md BUG-019, IMPLEMENTATION_ROADMAP.md P0-15 |
| **CON-07** | KYC Review in User Service | KYC logic mixed with profile management | **Moved to dedicated KYC Workflow Service** (Port 8087) | Target Architecture Section 2.1, 4.2.3 |
| **CON-08** | Simple KYC Status Enum | User Service has `KYC_STATUS` enum | **Replaced with full KYC Case State Machine** (13 states) | KYC_ARCHITECTURE.md Section 3 |
| **CON-09** | No Separation of Duties | Single admin could review and approve | **Enforced: Officer ≠ Manager for same case** | KYC_ARCHITECTURE.md Section 5, RBAC_ARCHITECTURE.md Section 5 |
| **CON-10** | No Document Access Control | Direct S3 access possible | **All access via KYC Workflow Service + pre-signed URLs** | KYC_ARCHITECTURE.md Section 9 |

---

## 5. Approved Architecture Decision Records (ADRs)

All 8 pending ADRs have been **APPROVED** with the exact choices and constraints specified below:

| # | Decision ID | Decision Point | Status | Approved Choice |
|---|-------------|----------------|--------|-----------------|
| **1** | **DEC-001** | Database Topology | ✅ **APPROVED** | Single AWS RDS for MySQL deployment with separate schemas per microservice; separate credentials per service; logical database ownership; APIs/events for cross-service communication; future migration path documented |
| **2** | **DEC-002** | Saga Pattern | ✅ **APPROVED** | Hybrid: Fund transfers = Orchestration (Transaction Service); Notifications = Choreography; Audit events = Async consumption; Outbox pattern for reliable publishing |
| **3** | **DEC-010** | Document Malware Scanning | ✅ **APPROVED** | Scanner abstraction with ClamAV initial implementation; quarantine until clean; fail-closed; pluggable for commercial vendors |
| **4** | **DEC-011** | MySQL Encryption | ✅ **APPROVED** | AWS RDS KMS storage encryption (customer-managed key); selective application-level envelope encryption for sensitive PII; no native MySQL TDE (not supported on RDS MySQL) |
| **5** | **DEC-012** | Secrets Management | ✅ **APPROVED** | AWS Secrets Manager; IAM workload identity for EKS; least-privilege; rotation policies; no hardcoded secrets |
| **6** | **DEC-013** | Database TLS | ✅ **APPROVED** | Mandatory TLS for all production RDS MySQL; `require_secure_transport=ON`; certificate validation; local dev exceptions documented |
| **7** | **DEC-014** | Service Discovery | ✅ **APPROVED** | Kubernetes DNS (CoreDNS) for EKS production; Eureka for local Docker Compose only; dual-write transition; no Eureka code removal yet |
| **8** | **DEC-015** | KYC Vendor Integration | ✅ **APPROVED** | Internal KYC workflow with mock provider initially; India market (PAN/Aadhaar); deterministic mock responses; pluggable adapter; no real vendor integration now |

**Previously Approved (Preserved):**
- DEC-003: KYC Service Ownership → Dedicated KYC Workflow Service ✅
- DEC-004: Case Assignment → Hybrid: Auto-assign with Manager override ✅
- DEC-005: SoD Enforcement Location → KYC Workflow Service + Gateway ✅
- DEC-006: JWT Claims Size → Permissions in JWT (15min expiry) ✅
- DEC-007: Ledger Service → Separate microservice ✅
- DEC-008: Compliance Service → Build abstraction; integrate vendor APIs ✅
- DEC-009: MFA Methods → Email OTP primary ✅

---

## 6. Documentation Changes Summary

### 6.1 Updated Documents

| Document | Version | Key Changes |
|----------|---------|-------------|
| `architecture/docs/CITICORE_TARGET_STATE_ARCHITECTURE.md` | 1.0 → 3.0 | **Major rewrite**: Schema per Service (1.3), Hybrid Saga (8.1), AWS RDS topology (5.1), K8s DNS discovery (4.1, 6.4), Security controls updated (10.6), KYC vendor mock (11.1) |
| `architecture/docs/ARCHITECTURE_DIAGRAMS.md` | 1.0 → 3.0 | Updated diagrams for: Database topology, Service topology, Hybrid saga, Service discovery, KYC malware scanning flow |
| `architecture/docs/RBAC_ARCHITECTURE.md` | 2.0 | No changes needed (already aligned) |
| `architecture/docs/KYC_ARCHITECTURE.md` | 2.0 → 3.0 | Added KYC vendor mock provider (2.5), Updated malware scanning flow (9.2), Updated security controls (9.4) |

### 6.2 New Documents Created

| Document | Purpose |
|----------|---------|
| `architecture/docs/ADR_REGISTER.md` | Decision register with all 15 ADRs (DEC-001 through DEC-015) |
| `architecture/docs/ADR-001_DATABASE_TOPOLOGY.md` | DEC-001: Single RDS with schemas |
| `architecture/docs/ADR-002_SAGA_PATTERN.md` | DEC-002: Hybrid saga architecture |
| `architecture/docs/ADR-010_MALWARE_SCANNING.md` | DEC-010: ClamAV with abstraction |
| `architecture/docs/ADR-011_MYSQL_ENCRYPTION.md` | DEC-011: RDS KMS + app-level encryption |
| `architecture/docs/ADR-012_SECRETS_MANAGEMENT.md` | DEC-012: AWS Secrets Manager |
| `architecture/docs/ADR-013_DATABASE_TLS.md` | DEC-013: Mandatory TLS for RDS |
| `architecture/docs/ADR-014_SERVICE_DISCOVERY.md` | DEC-014: K8s DNS production, Eureka local |
| `architecture/docs/ADR-015_KYC_VENDOR_INTEGRATION.md` | DEC-015: Mock provider initially |

### 6.3 Documents Referenced (Unchanged)

| Document | Status |
|----------|--------|
| `PROJECT_ARCHITECTURE.md` | Referenced as baseline |
| `SECURITY_AUDIT.md` | Referenced for security gaps |
| `DATABASE_AUDIT.md` | Referenced for schema issues |
| `BANKING_FUNCTIONALITY_GAP_ANALYSIS.md` | Referenced for functional gaps |
| `IMPLEMENTATION_ROADMAP.md` | Referenced for implementation priority |
| `PRODUCTION_READINESS.md` | Referenced for deployment gaps |
| `ARCHITECTURE_REVIEW_OBSERVATIONS.md` | Referenced for discovered conflicts |

---

## 7. Implementation Impact Assessment

### 7.1 New Components Required

| Component | Type | Effort | Dependencies |
|-----------|------|--------|--------------|
| KYC Workflow Service | New Microservice | High (6-8 weeks) | User Service, Auth Service, Kafka, MySQL |
| RBAC Infrastructure | Library/Framework | Medium (3-4 weeks) | Spring Security, Redis |
| PermissionEvaluator | Custom Component | Low (1-2 weeks) | Spring Security |
| Audit Log Hash Chaining | Database + Service | Medium (2-3 weeks) | MySQL, KYC Workflow Service |
| Document Malware Scanning | S3 Object Lambda + ClamAV | Low (1-2 weeks) | AWS S3, ClamAV |
| SLA Monitoring | Scheduler + Alerts | Medium (2-3 weeks) | KYC Workflow Service, AlertManager |
| AWS Secrets Manager Integration | Config/Code | Low (1 week) | Spring Cloud AWS, IAM |
| RDS TLS Configuration | Config | Low (1 week) | Spring Boot, RDS CA certs |

### 7.2 Modified Components

| Component | Changes | Effort |
|-----------|---------|--------|
| API Gateway | Coarse RBAC filter, rate limiting per role | Medium |
| Auth Service | JWT claims enhancement (permissions, case assignments), MFA | Medium |
| User Service | SecurityConfig fix, remove KYC review logic, Feign for KYC status | Low |
| Account Service | KYC status check via Feign + Kafka event, dual balance | Medium |
| Transaction Service | Orchestration logic for fund transfers | Medium |
| Notification Service | New KYC notification templates, consume kyc-case-topic | Low |
| Config Server | AWS Secrets Manager integration | Low |
| All Services | DB credentials from Secrets Manager; RDS TLS config | Low |

### 7.3 Database Changes

| Database/Schema | New Tables | Migrations |
|-----------------|------------|------------|
| `citicore_kycdb` | kyc_cases, kyc_case_assignments, kyc_case_history, kyc_review_findings, kyc_document_refs | New (V1-V5) |
| `citicore_authdb` | login_attempts, user_role_audit | V2-V3 |
| `citicore_userdb` | No new tables (KYC docs remain) | N/A |
| `citicore_account` | account_holds | V2 |

### 7.4 Infrastructure Changes

| Area | Change |
|------|--------|
| **RDS** | Single MySQL 8.0 deployment; customer-managed KMS key; `require_secure_transport=ON`; separate schemas/users per service |
| **EKS** | IRSA for Secrets Manager access; CoreDNS for service discovery; NetworkPolicies for zero-trust |
| **S3** | Quarantine + Clean buckets; SSE-KMS; Object Lambda for ClamAV scanning |
| **Secrets Manager** | DB credentials, JWT secret, API keys; rotation policies |
| **KMS** | RDS storage encryption key; envelope encryption keys for PII |

---

## 8. Review Checklist for Stakeholders

### 8.1 Architecture Review

- [x] RBAC role definitions align with organizational structure
- [x] Permission granularity supports least-privilege without operational burden
- [x] KYC state machine covers all regulatory required states
- [x] Maker-checker separation meets PMLA/RBI requirements
- [x] SLA definitions are operationally achievable
- [x] Audit trail integrity (hash chaining) meets SOX/regulatory needs
- [x] Document security (SSE-KMS, pre-signed URLs, malware scanning) is sufficient

### 8.2 Security Review

- [x] Default-deny enforced at all layers
- [x] JWT 15min expiry + refresh rotation is acceptable risk window
- [x] Revocation latency (≤15min) meets security requirements
- [x] SoD enforcement cannot be bypassed
- [x] Admin break-glass procedure is auditable
- [x] Encryption at rest/in transit decisions approved (RDS KMS + TLS)

### 8.3 Operations Review

- [x] Daily/weekly/monthly operational procedures are feasible
- [x] SLA breach alerting won't cause alert fatigue
- [x] Case assignment auto-distribution logic is fair
- [x] Escalation paths have clear ownership
- [x] Audit integrity verification job schedule is appropriate

### 8.4 Implementation Review

- [x] KYC Workflow Service scope is bounded correctly
- [x] Schema per service principle maintained
- [x] Event-driven communication doesn't create circular dependencies
- [x] Migration path for existing KYC data defined
- [x] Backward compatibility for API consumers considered

---

## 9. Next Steps

### Immediate (Documentation Complete)
1. ✅ All 8 pending ADRs approved and documented
2. ✅ Architecture documentation updated consistently
3. ✅ Mermaid diagrams updated
4. ✅ Decision register created

### Upon Implementation Authorization
1. Update `IMPLEMENTATION_ROADMAP.md` with RBAC/KYC tasks (Phase 0-1)
2. Create GitHub Epics/Stories for implementation
3. Begin SPIKE for KYC Workflow Service domain model
4. Begin SPIKE for PermissionEvaluator implementation
5. Provision AWS resources (RDS, Secrets Manager, KMS, S3 buckets)

---

## 10. Implementation Gaps

The following gaps exist between current implementation and approved target state:

| Gap | Description | Priority |
|-----|-------------|----------|
| **Database** | Current: Multiple MySQL instances in Docker Compose; Target: Single RDS with schemas | High |
| **Secrets** | Current: Env vars in docker-compose; Target: AWS Secrets Manager | High |
| **TLS** | Current: No TLS for DB connections; Target: Mandatory RDS TLS | High |
| **Encryption** | Current: No encryption at rest; Target: RDS KMS + app-level envelope encryption | High |
| **Service Discovery** | Current: Eureka in all environments; Target: K8s DNS (prod), Eureka (local) | Medium |
| **KYC Service** | Current: KYC logic in User Service; Target: Dedicated KYC Workflow Service | High |
| **Saga** | Current: Choreography; Target: Hybrid (Orchestration for transfers) | Medium |
| **Malware Scanning** | Current: None; Target: ClamAV via S3 Object Lambda | Medium |
| **Secrets Rotation** | Current: None; Target: Automated rotation policies | Low |
| **Audit Hash Chaining** | Current: None; Target: Immutable hash-chained audit logs | Medium |

---

## 11. Dependencies & Future Considerations

### Dependencies
1. **AWS Account Setup** - RDS, EKS, MSK, ElastiCache, Secrets Manager, KMS, S3
2. **IRSA Configuration** - IAM roles for service accounts
3. **Network Topology** - VPC, subnets, security groups, NACLs
4. **CI/CD Pipeline** - GitHub Actions/GitLab CI with AWS deployment
5. **Monitoring Stack** - Prometheus, Grafana, Loki, Tempo, AlertManager

### Future Migration Considerations
| Phase | Trigger | Action |
|-------|---------|--------|
| **Phase 2** | Scale/Isolation needs | Split high-throughput schemas to dedicated RDS instances |
| **Phase 3** | Regulatory/Compliance | Independent database deployments per service with VPC isolation |
| **Phase 4** | Multi-region | Cross-region read replicas; active-active for critical schemas |
| **KYC Vendor** | Regulatory requirement | Real vendor integration (Jumio/Onfido) via pluggable adapter |
| **Service Mesh** | mTLS/Traffic needs | Istio/Linkerd for zero-trust networking |

### Unresolved Architectural Questions
1. **Multi-tenancy** - Not addressed; future requirement for white-label banking
2. **Data Residency** - Cross-border data flow not specified
3. **Disaster Recovery** - RPO/RTO targets need stakeholder alignment
4. **API Versioning** - Strategy for breaking changes not finalized
5. **Real-time Fraud Detection** - Placeholder for future ML-based system

---

## 12. Appendix: File Inventory

### Updated Files
```
architecture/docs/CITICORE_TARGET_STATE_ARCHITECTURE.md  (v3.0)
architecture/docs/ARCHITECTURE_DIAGRAMS.md                (v3.0)
architecture/docs/KYC_ARCHITECTURE.md                     (v3.0)
architecture/docs/CHANGE_SUMMARY.md                       (v3.0)
```

### New Files (ADRs)
```
architecture/docs/ADR_REGISTER.md
architecture/docs/ADR-001_DATABASE_TOPOLOGY.md
architecture/docs/ADR-002_SAGA_PATTERN.md
architecture/docs/ADR-010_MALWARE_SCANNING.md
architecture/docs/ADR-011_MYSQL_ENCRYPTION.md
architecture/docs/ADR-012_SECRETS_MANAGEMENT.md
architecture/docs/ADR-013_DATABASE_TLS.md
architecture/docs/ADR-014_SERVICE_DISCOVERY.md
architecture/docs/ADR-015_KYC_VENDOR_INTEGRATION.md
```

### Existing Files (Unchanged)
```
architecture/docs/RBAC_ARCHITECTURE.md (v2.0)
architecture/docs/ARCHITECTURE_REVIEW_OBSERVATIONS.md
architecture/DELIVERABLES_INDEX.md
PROJECT_ARCHITECTURE.md
SECURITY_AUDIT.md
DATABASE_AUDIT.md
BANKING_FUNCTIONALITY_GAP_ANALYSIS.md
IMPLEMENTATION_ROADMAP.md
PRODUCTION_READINESS.md
```

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-11-18 | Architecture Team | Initial change summary |
| 2.0 | 2026-11-18 | Architecture Team | Major update with RBAC/KYC |
| 3.0 | 2026-11-18 | Architecture Team | **All 8 pending ADRs approved**; documentation updated; ADR documents created; implementation gaps identified |

---

**END OF DOCUMENT**

*This change summary reflects the approved target-state architecture. All 8 pending decisions (DEC-001, DEC-002, DEC-010, DEC-011, DEC-012, DEC-013, DEC-014, DEC-015) have been approved and documented. The 7 previously approved decisions (DEC-003 through DEC-009) are preserved. Stop after documentation updates. Do not begin implementation until explicitly authorized.*