# ADR-001: Database Topology — Single AWS RDS with Separate Schemas

## Status
✅ **APPROVED** (2026-11-18)

## Context

CitiCore requires a database strategy that balances operational simplicity with service autonomy. The current Docker Compose deployment uses multiple MySQL instances, but production requires a managed database solution.

**Requirements:**
- Service data isolation and ownership
- Referential integrity within service boundaries
- Operational simplicity for initial deployment
- Clear migration path to independent databases
- No direct cross-service table access

## Decision

**Use a single AWS RDS for MySQL 8.0 deployment with separate schemas per microservice.**

### Implementation Details

| Aspect | Specification |
|--------|---------------|
| **Deployment** | Single RDS MySQL 8.0 instance (Multi-AZ for production) |
| **Schemas** | One schema per service: `citicore_authdb`, `citicore_kycdb`, `citicore_userdb`, `citicore_account`, `citicore_transactiondb`, `citicore_ledgerdb`, `citicore_compliancedb` |
| **Credentials** | Separate database user per service with schema-scoped GRANTs |
| **Permissions** | Service users: SELECT, INSERT, UPDATE, DELETE, EXECUTE on own schema only |
| **Cross-Schema Access** | Explicitly prohibited; use APIs/events for cross-service data |
| **Encryption** | RDS storage encryption with customer-managed KMS key (see DEC-011) |
| **TLS** | Mandatory TLS 1.2+ with `require_secure_transport=ON` (see DEC-013) |

### Schema Ownership Matrix

| Schema | Owner Service | DB User | Grants |
|--------|---------------|---------|--------|
| `citicore_authdb` | Auth Service | `auth_svc_user` | ALL on `citicore_authdb`.* |
| `citicore_kycdb` | KYC Workflow Service | `kyc_svc_user` | ALL on `citicore_kycdb`.* |
| `citicore_userdb` | User Service | `user_svc_user` | ALL on `citicore_userdb`.* |
| `citicore_account` | Account Service | `account_svc_user` | ALL on `citicore_account`.* |
| `citicore_transactiondb` | Transaction Service | `txn_svc_user` | ALL on `citicore_transactiondb`.* |
| `citicore_ledgerdb` | Ledger Service | `ledger_svc_user` | ALL on `citicore_ledgerdb`.* |
| `citicore_compliancedb` | Compliance Service | `compliance_svc_user` | ALL on `citicore_compliancedb`.* |

## Alternatives Considered

| Option | Description | Pros | Cons | Verdict |
|--------|-------------|------|------|---------|
| **A) Single RDS with Schemas** | One RDS instance, separate schemas per service | Simple ops; shared backup; cost-effective; easy migration | Shared compute; blast radius; no true isolation | ✅ **Selected** |
| **B) Multiple RDS Instances** | One RDS per service | True isolation; independent scaling; blast radius containment | Complex ops; higher cost; backup coordination; cross-DB referential integrity harder | Future migration |
| **C) Shared Schema** | All services in one schema | Simplest | No ownership; coupling; security risk | ❌ Rejected |
| **D) Aurora Serverless v2** | Serverless MySQL-compatible | Auto-scaling; pay-per-use | Higher latency; cost unpredictability; limited control | Evaluate later |

## Consequences & Trade-offs

### Positive
- **Operational Simplicity**: Single RDS to monitor, backup, patch, and secure
- **Cost Efficiency**: Shared compute resources for initial workload
- **Referential Integrity**: Within-schema FKs work normally
- **Migration Path**: Clear path to split schemas to dedicated RDS instances
- **Backup/Recovery**: Single snapshot captures all schemas; PITR works uniformly

### Negative
- **Shared Compute**: Noisy neighbor risk (mitigated by RDS instance sizing + monitoring)
- **Blast Radius**: RDS failure affects all services (mitigated by Multi-AZ + cross-region read replica)
- **Schema Coupling**: Accidental cross-schema queries possible (prevented by DB user permissions + code reviews)
- **Scaling Limits**: Vertical scaling only; cannot scale schemas independently

## Security Implications

- **Least Privilege**: Each service DB user has access only to its schema
- **No Cross-Schema GRANTs**: Enforced by RDS parameter group and IAM policies
- **Encryption**: RDS storage encryption + TLS in transit (DEC-011, DEC-013)
- **Audit**: All cross-schema access attempts logged and alerted

## Implementation Considerations

1. **RDS Parameter Group**: `require_secure_transport=ON`, `sql_mode=STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION`
2. **Backup**: Automated daily snapshots + 35-day retention; cross-region copy for DR
3. **Monitoring**: Enhanced Monitoring + Performance Insights; alarms for CPU, connections, storage
4. **Migration Trigger**: When any schema exceeds 30% of instance capacity or regulatory isolation required
5. **Migration Process**: 
   - Provision new RDS instance
   - Schema export/import (mysqldump / DMS)
   - Dual-write period with CDC
   - DNS switchover
   - Decommission old schema

## Dependencies

- **DEC-011**: MySQL Encryption (RDS KMS key must exist before RDS creation)
- **DEC-012**: Secrets Management (DB credentials in AWS Secrets Manager)
- **DEC-013**: Database TLS (CA certs for RDS validation)
- **AWS Account**: VPC, subnets, security groups, IAM roles

## Future Migration Considerations

| Phase | Trigger | Action |
|-------|---------|--------|
| **Phase 2** | Scale/Isolation needs | Split high-throughput schemas (transactiondb, account) to dedicated RDS |
| **Phase 3** | Regulatory/Compliance | Independent RDS per service with dedicated VPC/subnet |
| **Phase 4** | Multi-region | Cross-region read replicas; active-active for critical schemas |

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-11-18 | Architecture Team | Initial ADR |

---

**END OF ADR**