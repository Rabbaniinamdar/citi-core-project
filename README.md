# CitiCore Banking Platform

A microservices-based digital banking platform built with Spring Boot 3.2.4, Java 17, and Spring Cloud 2023.0.3.

## Quick Links

- [Architecture Documentation](architecture/README.md) - Complete architecture documentation
- [Implementation Roadmap](implementation/roadmap.md) - 20-week phased implementation plan
- [Local Development Guide](environments/local-development.md) - Get started locally with Docker Compose
- [Production Deployment](environments/production-deployment.md) - AWS production infrastructure
- [Security & RBAC](security/rbac.md) - Role-based access control specification
- [KYC Workflow](business-workflows/kyc-workflow.md) - Maker-checker KYC workflow

## Architecture Overview

CitiCore is a microservices-based digital banking platform implementing:
- Customer onboarding with KYC maker-checker workflow
- Account lifecycle management (Savings/Current)
- Internal fund transfers with saga orchestration
- Double-entry general ledger (planned)
- Comprehensive RBAC with 9 roles and 28 permissions
- Event-driven architecture with Kafka and transactional outbox

## Technology Stack

| Layer | Technology | Version |
|-------|------------|---------|
| Language | Java | 17 |
| Framework | Spring Boot | 3.2.4 |
| Cloud | Spring Cloud | 2023.0.3 |
| Database | AWS RDS for MySQL | 8.0 |
| Cache | Redis | 7 |
| Messaging | Apache Kafka | 3.8 |
| Service Discovery | Kubernetes DNS (Prod) / Eureka (Local) | - |
| Gateway | Spring Cloud Gateway | - |
| Security | Spring Security, JWT | - |
| Observability | Micrometer, Prometheus, Grafana | - |

## Quick Start (Local Development)

```bash
# 1. Clone and configure
git clone <repository-url>
cd CitiCoreProject
cp .env.example .env.local
# Edit .env.local with your values

# 2. Start infrastructure
docker compose up -d mysql redis kafka minio clamav mailpit eureka-server config-server

# 3. Start services
docker compose up -d auth-service user-service kyc-workflow-service account-service transaction-service notification-service api-gateway

# 4. Verify
curl http://localhost:8080/actuator/health
```

## Documentation Structure

```
architecture/           # Architecture documentation
├── architecture.md     # Authoritative target state architecture
├── diagrams/           # Mermaid diagrams (17 diagrams)
├── observations.md     # Architecture review observations
├── README.md           # This file
decisions/
├── adr-register.md     # ADR register (15 ADRs)
├── adrs/               # Individual ADRs (15)
├── changelog.md        # Consolidated changelog
security/
├── rbac.md             # RBAC specification (9 roles, 28 permissions)
├── environment-security.md
├── audit.md
business-workflows/
├── kyc-workflow.md     # KYC maker-checker workflow
environments/
├── environment-architecture.md
├── local-development.md
├── production-deployment.md
engineering/
├── cicd-strategy.md
├── configuration-management.md
├── testing-strategy.md
implementation/
├── roadmap.md          # 20-week phased implementation plan
├── sprint-plan.md      # Detailed sprint breakdown
├── implementation-inventory.md
├── technical-debt.md
analysis/
├── gap-analysis.md
├── bug-audit.md
├── production-readiness.md
security/
├── audit.md            # Security audit findings
├── rbac.md             # RBAC specification
├── environment-security.md
database/
├── audit.md            # Database audit
analysis/
├── gap-analysis.md
├── bug-audit.md
├── production-readiness.md
api/
├── documentation.md
infrastructure/
├── terraform-design.md
├── terraform/          # Future Terraform modules
archive/
├── PROJECT_ARCHITECTURE_v1.md
```

## Key Architecture Decisions (ADRs)

All 15 ADRs are approved and documented in [decisions/adrs/](decisions/adrs/):

| ADR | Decision |
|-----|----------|
| DEC-001 | Single AWS RDS with separate schemas per microservice |
| DEC-002 | Hybrid saga: orchestration for transfers, choreography for notifications |
| DEC-003 | Dedicated KYC Workflow Service (Port 8087) |
| DEC-004 | Hybrid case assignment: auto-assign with manager override |
| DEC-005 | SoD enforcement in KYC Workflow Service + Gateway |
| DEC-006 | JWT claims include permissions (15min expiry) |
| DEC-007 | Separate Ledger Service microservice |
| DEC-008 | Compliance Service with vendor abstraction |
| DEC-009 | Email OTP primary |
| DEC-010 | ClamAV via S3 Object Lambda for malware scanning |
| DEC-011 | RDS KMS storage encryption + app-level envelope encryption |
| DEC-012 | AWS Secrets Manager with IRSA |
| DEC-013 | Mandatory TLS 1.2+ for RDS |
| DEC-014 | K8s DNS (prod) / Eureka (local) |
| DEC-015 | Internal KYC workflow with mock provider initially |

## Environments

| Environment | Purpose | Infrastructure |
|-------------|---------|----------------|
| **Local** | Developer inner loop | Docker Compose (MySQL, Kafka KRaft, Redis, MinIO, ClamAV, Mailpit, Eureka) |
| **CI** | Automated testing | GitHub Actions with Testcontainers |
| **Staging** | Pre-production validation | AWS EKS, RDS, MSK, ElastiCache, S3, SES Sandbox |
| **Production** | Live traffic | AWS EKS (3 AZ), RDS Multi-AZ, MSK (3 AZ), ElastiCache, S3, SES Production |

## Security

- **Zero Trust**: JWT authentication, RBAC + ABAC, least privilege
- **Encryption**: RDS KMS storage encryption + app-level envelope encryption for PII
- **Secrets**: AWS Secrets Manager with IRSA (prod), `.env.local` (local)
- **Malware Scanning**: ClamAV via S3 Object Lambda (quarantine → scan → clean)
- **Audit**: Immutable hash-chained audit logs for all financial/KYC events

## Compliance

- PCI-DSS SAQ-A eligible
- SOX audit trail (immutable, hash-chained)
- RBI KYC/AML guidelines
- GDPR/PDPB data protection

## Getting Started

1. Read the [Local Development Guide](environments/local-development.md)
2. Review the [Architecture](architecture/architecture.md)
3. Check the [Implementation Roadmap](implementation/roadmap.md)
4. Review [Security & RBAC](security/rbac.md)

## Contributing

1. Fork the repository
2. Create a feature branch (`feature/JIRA-123-description`)
3. Make changes with tests
4. Submit PR with conventional commits
5. Pass all CI quality gates

## License

Internal - Confidential. CitiCore Banking Platform.