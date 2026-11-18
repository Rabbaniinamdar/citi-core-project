# ADR-003: KYC Service Ownership — Dedicated KYC Workflow Service

## Status
✅ **APPROVED** (2026-11-18)

## Context

The KYC workflow was previously embedded within the User Service, mixing profile management with KYC case lifecycle management. This created tight coupling, unclear ownership, and made the KYC maker-checker workflow difficult to maintain and scale.

**Requirements:**
- Clear separation between user profile management and KYC case lifecycle
- Dedicated ownership for KYC maker-checker workflow
- Independent scaling and deployment of KYC workflow
- Clear API boundaries between user profile and KYC case management

## Decision

**Create a dedicated KYC Workflow Service (Port 8087) that owns the complete KYC case lifecycle.**

### Service Responsibilities

| Responsibility | Description |
|----------------|-------------|
| **Case Creation** | Create KycCase from User Service submission |
| **Case Assignment** | Auto-assign (round-robin) or Manager manual assign to KYC Officers |
| **Maker-Checker Workflow** | Officer review → recommendation → Manager approval/rejection |
| **State Machine** | 13-state lifecycle enforcement |
| **SLA Monitoring** | Per-stage deadlines, breach alerts, auto-escalation |
| **Audit Trail** | Immutable hash-chained history per transition |
| **Document References** | Link to documents stored in User Service / S3 |

### Service Boundaries

| Service | Responsibility |
|---------|----------------|
| **User Service** | Profile management, document upload to S3, KYC submission trigger |
| **KYC Workflow Service** | Case lifecycle, assignments, reviews, approvals, SoD enforcement |
| **Auth Service** | Role verification (KYC_OFFICER, KYC_MANAGER) |
| **Notification Service** | Email notifications for all KYC events |

## Alternatives Considered

| Option | Description | Pros | Cons | Verdict |
|--------|-------------|------|------|---------|
| **A) Dedicated KYC Workflow Service** (Selected) | New microservice owning KYC case lifecycle | Clean separation; independent scaling; clear ownership | New service overhead | ✅ **Selected** |
| **B) Extended User Service** | Extend User Service with KYC workflow logic | Reuses existing service | Tight coupling; mixed responsibilities; scaling issues | ❌ Rejected |
| **C) Embedded in Account Service** | KYC as part of account opening flow | Simplifies account opening | Wrong domain; KYC ≠ Account | ❌ Rejected |

## Consequences & Trade-offs

### Positive
- **Clear Ownership**: KYC Workflow Service owns case lifecycle end-to-end
- **Independent Scaling**: KYC workflow scales independently from user profile
- **SoD Enforcement**: Centralized maker-checker enforcement in one place
- **Independent Deployment**: KYC workflow can be deployed/updated independently
- **Testability**: Isolated unit/integration testing for KYC workflow

### Negative
- **Operational Overhead**: Additional service to deploy, monitor, maintain
- **Network Latency**: Additional Feign/Kafka calls between services
- **Data Consistency**: Cross-service referential integrity via reconciliation

## Security Implications

- **SoD Enforcement**: Centralized in KYC Workflow Service (DEC-005)
- **RBAC**: KYC_OFFICER and KYC_MANAGER roles enforced at service level
- **Audit Trail**: Immutable hash-chained audit log per case transition
- **Document Access**: Pre-signed S3 URLs (15-min expiry) via KYC Workflow Service

## Implementation Considerations

1. **Database**: New schema `citicore_kycdb` with tables: `kyc_cases`, `kyc_case_assignments`, `kyc_case_history`, `kyc_review_findings`, `kyc_document_refs`
2. **Events**: Consumes `kyc-submission-topic`, `kyc-document-topic`; Produces `kyc-case-topic`, `kyc-notification-topic`
3. **Feign Clients**: UserClient (profile, documents), AuthClient (role verification), NotificationClient
4. **Service Discovery**: Kubernetes DNS (prod) / Eureka (local) per DEC-014
5. **Secrets**: DB credentials from AWS Secrets Manager per DEC-012

## Dependencies

- **DEC-001**: Database Topology (separate schema `citicore_kycdb`)
- **DEC-004**: Case Assignment (assignment logic in KYC Workflow Service)
- **DEC-005**: SoD Enforcement Location (enforced in KYC Workflow Service)
- **DEC-015**: KYC Vendor Integration (mock provider interface in KYC Workflow Service)

## Future Migration Considerations

| Phase | Trigger | Action |
|-------|---------|--------|
| **Phase 2** | Scale needs | Split KYC DB to dedicated RDS if throughput exceeds threshold |
| **Phase 3** | Multi-region | Cross-region KYC case replication |
| **Phase 4** | Real vendor | Pluggable adapter for Jumio/Onfido/HyperVerge |

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-11-18 | Architecture Team | Initial ADR |

---

**END OF ADR**