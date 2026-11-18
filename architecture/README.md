# Architecture Documentation Index

This directory contains all architecture documentation for the CitiCore Banking Platform.

## Quick Navigation

| Document | Description |
|----------|-------------|
| [architecture.md](architecture.md) | **Authoritative Target State Architecture** (Approved v3.0) |
| [observations.md](observations.md) | Architecture review observations and change summary |
| [diagrams/](diagrams/) | 17 Mermaid diagrams |

### Key Architecture Documents

| Document | Location | Description |
|----------|----------|-------------|
| **Target State Architecture** | [architecture.md](architecture.md) | Single source of truth for target architecture |
| **RBAC Specification** | [../security/rbac.md](../security/rbac.md) | 9 roles, 28 permissions, enforcement |
| **KYC Workflow** | [../business-workflows/kyc-workflow.md](../business-workflows/kyc-workflow.md) | 13-state maker-checker workflow |
| **Environment Architecture** | [../environments/environment-architecture.md](../environments/environment-architecture.md) | 4 environments (Local, CI, Staging, Prod) |

### Architecture Decision Records
- [ADR Register](../decisions/adr-register.md) - All 15 ADRs
- [Individual ADRs](adrs/) - DEC-001 through DEC-015
- [Changelog](../decisions/changelog.md) - Consolidated change log

### Diagrams
- [All Diagrams](diagrams/) - 17 Mermaid diagrams as individual .mmd files

### Security
- [RBAC Specification](../security/rbac.md) - 9 roles, 28 permissions
- [Environment Security](../security/environment-security.md) - Zero trust, encryption, secrets

### Environments
- [Environment Architecture](../environments/environment-architecture.md) - 4 environments
- [Local Development](../environments/local-development.md) - Docker Compose guide
- [Production Deployment](../environments/production-deployment.md) - AWS EKS architecture

### Implementation
- [Roadmap](../implementation/roadmap.md) - 20-week phased plan
- [Sprint Plan](../implementation/sprint-plan.md) - Detailed sprint breakdown
- [Inventory](../implementation/implementation-inventory.md) - Feature status tracking
- [Technical Debt](../implementation/technical-debt.md) - Debt register

### Security
- [RBAC Specification](../security/rbac.md) - 9 roles, 28 permissions
- [Environment Security](../security/environment-security.md) - Zero trust, encryption

### Analysis & Audits
- [Gap Analysis](../analysis/gap-analysis.md) - Banking functionality gaps
- [Bug Audit](../analysis/bug-audit.md) - 47 confirmed bugs
- [Production Readiness](../analysis/production-readiness.md) - 45 gaps
- [Security Audit](../security/audit.md) - 32 findings
- [Database Audit](../database/audit.md) - Schema audit

### Diagrams Catalog

| # | Diagram | File |
|---|---------|------|
| 1 | Authentication & RBAC Architecture | [01-auth-rbac.mmd](diagrams/01-auth-rbac.mmd) |
| 2 | Role-to-Permission Authorization Flow | [02-role-permission-flow.mmd](diagrams/02-role-permission-flow.mmd) |
| 3 | KYC End-to-End Workflow | [03-kyc-workflow.mmd](diagrams/03-kyc-workflow.mmd) |
| 4 | KYC Officer & Manager Interaction | [04-officer-manager.mmd](diagrams/04-officer-manager.mmd) |
| 5 | KYC State Machine | [05-kyc-state-machine.mmd](diagrams/05-kyc-state-machine.mmd) |
| 6 | KYC Service Communication | [06-kyc-communication.mmd](diagrams/06-kyc-communication.mmd) |
| 7 | Service Topology | [07-service-topology.mmd](diagrams/07-service-topology.mmd) |
| 8 | Database Topology | [08-database-topology.mmd](diagrams/08-database-topology.mmd) |
| 9 | Event-Driven Architecture | [09-event-architecture.mmd](diagrams/09-event-architecture.mmd) |
| 10 | Fund Transfer Saga | [10-fund-transfer-saga.mmd](diagrams/10-fund-transfer-saga.mmd) |
| 11 | Security Model | [11-security-model.mmd](diagrams/11-security-model.mmd) |
| 12 | Customer Onboarding | [12-onboarding.mmd](diagrams/12-onboarding.mmd) |
| 13 | Role Hierarchy | [13-role-hierarchy.mmd](diagrams/13-role-hierarchy.mmd) |
| 14 | Database Encryption | [14-database-encryption.mmd](diagrams/14-database-encryption.mmd) |

### Approved ADRs (15)

| ADR | Title | Status |
|-----|-------|--------|
| DEC-001 | Database Topology - Single RDS with schemas | ✅ Approved |
| DEC-002 | Saga Pattern - Hybrid orchestration/choreography | ✅ Approved |
| DEC-003 | KYC Service Ownership - Dedicated service | ✅ Approved |
| DEC-004 | Case Assignment - Hybrid auto-assign | ✅ Approved |
| DEC-005 | SoD Enforcement - KYC Service + Gateway | ✅ Approved |
| DEC-006 | JWT Claims Size - Permissions in token | ✅ Approved |
| DEC-007 | Ledger Service - Separate microservice | ✅ Approved |
| DEC-008 | Compliance Service - Build abstraction | ✅ Approved |
| DEC-009 | MFA Methods - TOTP primary, Email fallback | ✅ Approved |
| DEC-010 | Malware Scanning - ClamAV with abstraction | ✅ Approved |
| DEC-011 | MySQL Encryption - RDS KMS + app-level envelope | ✅ Approved |
| DEC-012 | Secrets Management - AWS Secrets Manager | ✅ Approved |
| DEC-013 | Database TLS - Mandatory TLS for RDS | ✅ Approved |
| DEC-014 | Service Discovery - K8s DNS prod, Eureka local | ✅ Approved |
| DEC-015 | KYC Vendor - Mock provider initially | ✅ Approved |

---

## Quick Start

### Local Development
```bash
# 1. Configure environment
cp .env.example .env.local

# 2. Start infrastructure
docker compose up -d mysql redis kafka minio clamav mailpit eureka-server config-server

# 3. Start services
docker compose up -d auth-service user-service kyc-workflow-service account-service transaction-service notification-service api-gateway
```

### Architecture Review
1. Start with [architecture.md](architecture.md) - the authoritative target state
2. Review [security/rbac.md](../security/rbac.md) for authorization model
3. Review [business-workflows/kyc-workflow.md](../business-workflows/kyc-workflow.md) for KYC maker-checker
4. Check [environments/local-development.md](../environments/local-development.md) for local setup

### Implementation
- [Implementation Roadmap](../implementation/roadmap.md) - 20-week plan
- [Sprint Plan](../implementation/sprint-plan.md) - Detailed sprint breakdown
- [Technical Debt](../implementation/technical-debt.md) - Debt register

---

*Last Updated: 2026-11-19 | Architecture Version: 3.0 (Approved)*