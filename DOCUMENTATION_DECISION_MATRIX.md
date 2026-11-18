# CitiCore Documentation Decision Matrix

## Repository Documentation Audit

| Existing Document | Current Purpose | Content Quality | Overlap / Conflict | Recommended Action | Final Destination |
|---|---|---|---|---|---|
| `README.md` | Project overview | Minimal - just title | None | UPDATE | `README.md` (root) |
| `PROJECT_ARCHITECTURE.md` | Original system architecture | Good - comprehensive but outdated vs target state | Overlaps with CITICORE_TARGET_STATE_ARCHITECTURE.md | ARCHIVE | `archive/PROJECT_ARCHITECTURE_v1.md` |
| `CITICORE_TARGET_STATE_ARCHITECTURE.md` (v3.0) | Target state architecture (Approved) | Excellent - comprehensive, approved | Authoritative source | KEEP (as authoritative) | `architecture/architecture.md` (root) |
| `ARCHITECTURE_DIAGRAMS.md` (v3.0) | All Mermaid diagrams | Excellent - 17 diagrams, approved | Some overlap with target state doc | MERGE | Merge into `architecture/architecture.md` and `architecture/diagrams/` |
| `ARCHITECTURE_REVIEW_OBSERVATIONS.md` | Architecture review findings | Good - 35 observations | Some overlap with CHANGE_SUMMARY.md | UPDATE | `architecture/observations.md` |
| `CHANGE_SUMMARY.md` (v3.0) | Change summary for ADRs | Excellent - comprehensive | Overlap with ADR register and observations | MERGE | Merge into `decisions/changelog.md` |
| `RBAC_ARCHITECTURE.md` (v2.0) | RBAC specification | Excellent - comprehensive | Authoritative for RBAC | KEEP | `security/rbac.md` |
| `KYC_ARCHITECTURE.md` (v3.0) | KYC maker-checker workflow | Excellent - comprehensive | Authoritative for KYC | KEEP | `business-workflows/kyc-workflow.md` |
| `CITICORE_ENVIRONMENT_ARCHITECTURE.md` | Environment architecture (4 envs) | Excellent - comprehensive | Authoritative for environments | KEEP | `environments/environment-architecture.md` |
| `CITICORE_LOCAL_DEVELOPMENT_GUIDE.md` | Local dev guide | Excellent - comprehensive | Authoritative for local dev | KEEP | `environments/local-development.md` |
| `CITICORE_PRODUCTION_INFRASTRUCTURE.md` | Production AWS architecture | Excellent - comprehensive | Authoritative for prod infra | KEEP | `environments/production-deployment.md` |
| `CITICORE_CICD_AND_SDLC.md` | CI/CD and SDLC architecture | Excellent - comprehensive | Authoritative for CI/CD | KEEP | `engineering/cicd-strategy.md` |
| `CITICORE_ENVIRONMENT_SECURITY.md` | Environment security architecture | Excellent - comprehensive | Authoritative for security | KEEP | `security/environment-security.md` |
| `CITICORE_CONFIGURATION_MANAGEMENT.md` | Configuration management | Excellent - comprehensive | Authoritative for config | KEEP | `engineering/configuration-management.md` |
| `CITICORE_TERRAFORM_DESIGN.md` | Terraform design | Excellent - comprehensive | Authoritative for Terraform | KEEP | `infrastructure/terraform-design.md` |
| `ARCHITECTURE_REVIEW_OBSERVATIONS.md` | Architecture review findings | Good - 35 observations | Overlap with CHANGE_SUMMARY | MERGE into observations | `architecture/observations.md` |
| `CHANGE_SUMMARY.md` (v3.0) | Change summary for ADRs | Excellent | Overlap with ADR register | MERGE into decisions/changelog.md | `decisions/changelog.md` |
| `ADR_REGISTER.md` | ADR register (15 ADRs) | Excellent - all 15 approved | Authoritative register | KEEP | `decisions/adr-register.md` |
| `ADR-001_DATABASE_TOPOLOGY.md` through `ADR-015_KYC_VENDOR_INTEGRATION.md` | Individual ADRs (9 docs) | Excellent - all approved | Authoritative ADRs | KEEP | `decisions/adrs/` |
| `ARCHITECTURE_DIAGRAMS.md` | 17 Mermaid diagrams | Excellent | Overlap with target state | MERGE into diagrams/ | `architecture/diagrams/` |
| `IMPLEMENTATION_ROADMAP.md` | 20-week phased plan | Excellent - detailed | Authoritative roadmap | KEEP | `implementation/roadmap.md` |
| `SECURITY_AUDIT.md` | Security audit (32 findings) | Good - comprehensive | Reference for security work | KEEP | `security/audit.md` |
| `DATABASE_AUDIT.md` | Database audit | Good - comprehensive | Reference for DB work | KEEP | `database/audit.md` |
| `BANKING_FUNCTIONALITY_GAP_ANALYSIS.md` | Functional gap analysis | Good - comprehensive | Reference for gaps | KEEP | `analysis/gap-analysis.md` |
| `BUG_AUDIT_REPORT.md` | Bug audit (47 bugs) | Good - comprehensive | Reference for bugs | KEEP | `analysis/bug-audit.md` |
| `PRODUCTION_READINESS.md` | Production readiness (45 gaps) | Good - comprehensive | Reference for prod readiness | KEEP | `analysis/production-readiness.md` |
| `IMPLEMENTATION_ROADMAP.md` | 20-week phased plan | Excellent - detailed | Authoritative roadmap | UPDATE | `implementation/roadmap.md` |
| `SECURITY_AUDIT.md` | Security audit (32 findings) | Good | Reference | KEEP | `security/audit.md` |
| `DATABASE_AUDIT.md` | Database audit | Good | Reference | KEEP | `database/audit.md` |
| `BANKING_FUNCTIONALITY_GAP_ANALYSIS.md` | Gap analysis | Good | Reference | KEEP | `analysis/gap-analysis.md` |
| `PRODUCTION_READINESS.md` | Production readiness | Good | Reference | KEEP | `analysis/production-readiness.md` |
| `BUG_AUDIT_REPORT.md` | Bug audit | Good | Reference | KEEP | `analysis/bug-audit.md` |
| `TESTING_STRATEGY.md` | Testing strategy | Good | Reference | KEEP | `engineering/testing-strategy.md` |
| `API_DOCUMENTATION.md` | API documentation | Minimal | Reference | UPDATE | `api/documentation.md` |
| `CHANGELOG.md` | Changelog | Minimal | Overlap with CHANGE_SUMMARY | MERGE | `CHANGELOG.md` (root) |
| `docker-compose.yml` | Local dev infrastructure | Good - working | Reference for local dev | KEEP | Root |
| `pom.xml` (root) | Parent POM | Good | Reference | KEEP | Root |
| `architecture/docs/DELIVERABLES_INDEX.md` | Deliverables index | Good | Reference for docs | UPDATE | `architecture/README.md` |

## Summary of Actions

| Action | Count | Documents |
|---|---|---|
| **KEEP** | 22 | Core architecture, RBAC, KYC, Environment, Local Dev, Prod Infra, CI/CD, Security, Config, Terraform, RBAC, KYC, ADR Register, ADRs, Roadmap, Audits, Gap Analysis, Bug Audit, Prod Readiness, Bug Audit, Testing Strategy |
| **UPDATE** | 5 | README.md, CITICORE_TARGET_STATE_ARCHITECTURE.md (move to root), IMPLEMENTATION_ROADMAP.md, DELIVERABLES_INDEX.md, README.md |
| **MERGE** | 6 | ARCHITECTURE_DIAGRAMS.md → diagrams/, ARCHITECTURE_REVIEW_OBSERVATIONS.md → observations.md, CHANGE_SUMMARY.md → changelog.md, CHANGELOG.md → CHANGELOG.md, ARCHITECTURE_DIAGRAMS.md → diagrams/, ADR docs → decisions/adrs/ |
| **ARCHIVE** | 1 | PROJECT_ARCHITECTURE.md → archive/ |
| **CREATE** | 4 | architecture/README.md, decisions/changelog.md, architecture/README.md, README.md (root) |
| **ARCHIVE (folder)** | 1 | PROJECT_ARCHITECTURE.md → archive/ |

## Final Documentation Structure

```
CitiCoreProject/
├── README.md                              # Updated - project overview + doc navigation
├── CHANGELOG.md                           # Updated - consolidated changelog
├── docker-compose.yml                     # Keep
├── pom.xml                                # Keep
├── architecture/
│   ├── README.md                          # NEW - architecture documentation index
│   ├── architecture.md                    # Authoritative target state architecture (moved from docs/)
│   ├── observations.md                    # Merged from ARCHITECTURE_REVIEW_OBSERVATIONS.md + CHANGE_SUMMARY
│   ├── diagrams/                          # NEW folder for Mermaid diagrams
│   │   ├── 01-auth-rbac.mmd
│   │   ├── 02-role-permission-flow.mmd
│   │   ├── 03-kyc-workflow.mmd
│   │   ├── 04-officer-manager.mmd
│   │   ├── 05-kyc-state-machine.mmd
│   │   ├── 06-kyc-communication.mmd
│   │   ├── 07-service-topology.mmd
│   │   ├── 08-database-topology.mmd
│   │   ├── 09-event-architecture.mmd
│   │   ├── 10-fund-transfer-saga.mmd
│   │   ├── 11-security-model.mmd
│   │   ├── 12-onboarding.mmd
│   │   ├── 13-role-hierarchy.mmd
│   │   └── 14-database-encryption.mmd
│   └── observations.md                    # Merged observations + change summary
├── decisions/
│   ├── adr-register.md                    # ADR register (moved from docs/)
│   ├── changelog.md                       # Merged from CHANGE_SUMMARY.md + CHANGELOG.md
│   └── adrs/                              # Individual ADRs
│       ├── ADR-001_DATABASE_TOPOLOGY.md
│       ├── ADR-002_SAGA_PATTERN.md
│       ├── ADR-003_KYC_SERVICE_OWNERSHIP.md
│       ├── ADR-004_CASE_ASSIGNMENT.md
│       ├── ADR-005_SOD_ENFORCEMENT.md
│       ├── ADR-006_JWT_CLAIMS_SIZE.md
│       ├── ADR-007_LEDGER_SERVICE.md
│       ├── ADR-008_COMPLIANCE_SERVICE.md
│       ├── ADR-009_MFA_METHODS.md
│       ├── ADR-010_MALWARE_SCANNING.md
│       ├── ADR-011_MYSQL_ENCRYPTION.md
│       ├── ADR-012_SECRETS_MANAGEMENT.md
│       ├── ADR-013_DATABASE_TLS.md
│       ├── ADR-014_SERVICE_DISCOVERY.md
│       └── ADR-015_KYC_VENDOR_INTEGRATION.md
├── security/
│   ├── rbac.md                            # RBAC architecture (moved from docs/)
│   ├── environment-security.md            # Environment security (moved from docs/)
│   ├── audit.md                           # Security audit reference
│   └── environment-security.md            # (duplicate - remove)
├── business-workflows/
│   └── kyc-workflow.md                    # KYC architecture (moved from docs/)
├── environments/
│   ├── environment-architecture.md        # (moved from docs/)
│   ├── local-development.md               # (moved from docs/)
│   └── production-deployment.md           # (moved from docs/)
├── infrastructure/
│   ├── terraform-design.md                # (moved from docs/)
│   └── terraform/                         # Future Terraform modules
├── environments/
│   ├── environment-architecture.md
│   ├── local-development.md
│   └── production-deployment.md
├── engineering/
│   ├── cicd-strategy.md                   # (moved from docs/)
│   ├── configuration-management.md        # (moved from docs/)
│   ├── testing-strategy.md                # (moved from TESTING_STRATEGY.md)
│   └── configuration-management.md        # (duplicate - remove)
├── implementation/
│   ├── roadmap.md                         # (moved from IMPLEMENTATION_ROADMAP.md)
│   ├── sprint-plan.md                     # NEW - detailed sprint plan
│   ├── implementation-inventory.md        # NEW - feature status inventory
│   └── technical-debt.md                  # NEW - technical debt register
├── analysis/
│   ├── gap-analysis.md                    # (from BANKING_FUNCTIONALITY_GAP_ANALYSIS.md)
│   ├── bug-audit.md                       # (from BUG_AUDIT_REPORT.md)
│   ├── production-readiness.md            # (from PRODUCTION_READINESS.md)
│   └── bug-audit.md                       # duplicate
├── security/
│   ├── audit.md                           # (from SECURITY_AUDIT.md)
│   ├── rbac.md                            # already listed
│   └── environment-security.md            # already listed
├── database/
│   └── audit.md                           # (from DATABASE_AUDIT.md)
├── api/
│   └── documentation.md                   # (from API_DOCUMENTATION.md)
├── archive/
│   └── PROJECT_ARCHITECTURE_v1.md         # Archived original architecture
├── config-repo/                           # Keep
├── config-repo-local/                     # Keep
├── config-service/                        # Keep
├── eureka-server/                         # Keep
├── auth-service/                          # Keep
├── user-service/                          # Keep
├── account-service/                       # Keep
├── transaction-service/                   # Keep
├── notification-service/                  # Keep
├── apigateway-service/                    # Keep
├── kafka-events/                          # Keep
├── uploads/                               # Keep
└── mysql/                                 # Keep
```

## Key Decisions Explained

1. **PROJECT_ARCHITECTURE.md → ARCHIVE**: The original architecture document is superseded by the approved CITICORE_TARGET_STATE_ARCHITECTURE.md (v3.0). Archive for historical context.

2. **CITICORE_TARGET_STATE_ARCHITECTURE.md → architecture/architecture.md**: This is the authoritative architecture document. Move to root of architecture/ as the single source of truth.

3. **ARCHITECTURE_DIAGRAMS.md → architecture/diagrams/**: Split 17 Mermaid diagrams into individual .mmd files for better maintainability and rendering.

6. **ARCHITECTURE_REVIEW_OBSERVATIONS.md + CHANGE_SUMMARY.md → architecture/observations.md + decisions/changelog.md**: Merge overlapping content. Observations become observations.md; change summary becomes decisions/changelog.md.

7. **CHANGELOG.md + CHANGE_SUMMARY.md → decisions/changelog.md**: Consolidate into single authoritative changelog in decisions folder.

8. **ADR documents**: Keep all 9 ADR files but move to decisions/adrs/ with consistent naming.

9. **Individual architecture docs (RBAC, KYC, Environment, etc.)**: Move to appropriate subject folders (security/, business-workflows/, environments/) as authoritative sources.

10. **IMPLEMENTATION_ROADMAP.md → implementation/roadmap.md**: Update and enhance with sprint details.

10. **CREATE new files**: 
   - architecture/README.md (navigation index)
   - implementation/sprint-plan.md (detailed sprint breakdown)
   - implementation/implementation-inventory.md (feature status tracking)
   - implementation/technical-debt.md (technical debt register)
   - architecture/README.md (documentation index)

11. **ARCHIVE**: Move original PROJECT_ARCHITECTURE.md to archive/ for historical reference.

This structure eliminates duplication, establishes clear ownership for each topic, and creates a navigable documentation system aligned with the actual architecture.</think>Now I have a comprehensive understanding of the repository. Let me create the documentation decision matrix and then proceed with the consolidation.
<tool_call>
<function=Write>
<parameter=content>
# CitiCore Documentation Decision Matrix

## Repository Documentation Audit

| Existing Document | Current Purpose | Content Quality | Overlap / Conflict | Recommended Action | Final Destination |
|---|---|---|---|---|---|
| `README.md` | Project overview | Minimal - just title | None | UPDATE | `README.md` (root) |
| `PROJECT_ARCHITECTURE.md` | Original system architecture | Good - comprehensive but outdated vs target state | Overlaps with CITICORE_TARGET_STATE_ARCHITECTURE.md | ARCHIVE | `archive/PROJECT_ARCHITECTURE_v1.md` |
| `CITICORE_TARGET_STATE_ARCHITECTURE.md` (v3.0) | Target state architecture (Approved) | Excellent - comprehensive, approved | Authoritative source | KEEP (as authoritative) | `architecture/architecture.md` (root) |
| `ARCHITECTURE_DIAGRAMS.md` (v3.0) | All Mermaid diagrams | Excellent - 17 diagrams, approved | Some overlap with target state doc | MERGE | Merge into `architecture/architecture.md` and `architecture/diagrams/` |
| `ARCHITECTURE_REVIEW_OBSERVATIONS.md` | Architecture review findings | Good - 35 observations | Some overlap with CHANGE_SUMMARY.md | UPDATE | `architecture/observations.md` |
| `CHANGE_SUMMARY.md` (v3.0) | Change summary for ADRs | Excellent - comprehensive | Overlap with ADR register and observations | MERGE | Merge into `decisions/changelog.md` |
| `RBAC_ARCHITECTURE.md` (v2.0) | RBAC specification | Excellent - comprehensive | Authoritative for RBAC | KEEP | `security/rbac.md` |
| `KYC_ARCHITECTURE.md` (v3.0) | KYC maker-checker workflow | Excellent - comprehensive | Authoritative for KYC | KEEP | `business-workflows/kyc-workflow.md` |
| `CITICORE_ENVIRONMENT_ARCHITECTURE.md` | Environment architecture (4 envs) | Excellent - comprehensive | Authoritative for environments | KEEP | `environments/environment-architecture.md` |
| `CITICORE_LOCAL_DEVELOPMENT_GUIDE.md` | Local dev guide | Excellent - comprehensive | Authoritative for local dev | KEEP | `environments/local-development.md` |
| `CITICORE_PRODUCTION_INFRASTRUCTURE.md` | Production AWS architecture | Excellent - comprehensive | Authoritative for prod infra | KEEP | `environments/production-deployment.md` |
| `CITICORE_CICD_AND_SDLC.md` | CI/CD and SDLC architecture | Excellent - comprehensive | Authoritative for CI/CD | KEEP | `engineering/cicd-strategy.md` |
| `CITICORE_ENVIRONMENT_SECURITY.md` | Environment security architecture | Excellent - comprehensive | Authoritative for security | KEEP | `security/environment-security.md` |
| `CITICORE_CONFIGURATION_MANAGEMENT.md` | Configuration management | Excellent - comprehensive | Authoritative for config | KEEP | `engineering/configuration-management.md` |
| `CITICORE_TERRAFORM_DESIGN.md` | Terraform design | Excellent - comprehensive | Authoritative for Terraform | KEEP | `infrastructure/terraform-design.md` |
| `ARCHITECTURE_REVIEW_OBSERVATIONS.md` | Architecture review findings | Good - 35 observations | Overlap with CHANGE_SUMMARY.md | MERGE into observations | `architecture/observations.md` |
| `CHANGE_SUMMARY.md` (v3.0) | Change summary for ADRs | Excellent - comprehensive | Overlap with ADR register and observations | MERGE | Merge into `decisions/changelog.md` |
| `ADR_REGISTER.md` | ADR register (15 ADRs) | Excellent - all 15 approved | Authoritative register | KEEP | `decisions/adr-register.md` |
| `ADR-001_DATABASE_TOPOLOGY.md` through `ADR-015_KYC_VENDOR_INTEGRATION.md` | Individual ADRs (9 docs) | Excellent - all approved | Authoritative ADRs | KEEP | `decisions/adrs/` |
| `ARCHITECTURE_DIAGRAMS.md` | 17 Mermaid diagrams | Excellent | Overlap with target state | MERGE into diagrams/ | `architecture/diagrams/` |
| `IMPLEMENTATION_ROADMAP.md` | 20-week phased plan | Excellent - detailed | Authoritative roadmap | KEEP | `implementation/roadmap.md` |
| `SECURITY_AUDIT.md` | Security audit (32 findings) | Good - comprehensive | Reference for security work | KEEP | `security/audit.md` |
| `DATABASE_AUDIT.md` | Database audit | Good - comprehensive | Reference for DB work | KEEP | `database/audit.md` |
| `BANKING_FUNCTIONALITY_GAP_ANALYSIS.md` | Functional gap analysis | Good - comprehensive | Reference for gaps | KEEP | `analysis/gap-analysis.md` |
| `PRODUCTION_READINESS.md` | Production readiness (45 gaps) | Good - comprehensive | Reference for prod readiness | KEEP | `analysis/production-readiness.md` |
| `IMPLEMENTATION_ROADMAP.md` | 20-week phased plan | Excellent - detailed | Authoritative roadmap | UPDATE | `implementation/roadmap.md` |
| `SECURITY_AUDIT.md` | Security audit (32 findings) | Good | Reference | KEEP | `security/audit.md` |
| `DATABASE_AUDIT.md` | Database audit | Good | Reference | KEEP | `database/audit.md` |
| `BANKING_FUNCTIONALITY_GAP_ANALYSIS.md` | Gap analysis | Good | Reference | KEEP | `analysis/gap-analysis.md` |
| `PRODUCTION_READINESS.md` | Production readiness | Good | Reference | KEEP | `analysis/production-readiness.md` |
| `BUG_AUDIT_REPORT.md` | Bug audit | Good | Reference | KEEP | `analysis/bug-audit.md` |
| `TESTING_STRATEGY.md` | Testing strategy | Good | Reference | KEEP | `engineering/testing-strategy.md` |
| `API_DOCUMENTATION.md` | API documentation | Minimal | Reference | UPDATE | `api/documentation.md` |
| `CHANGELOG.md` | Changelog | Minimal | Overlap with CHANGE_SUMMARY | MERGE | `CHANGELOG.md` (root) |
| `docker-compose.yml` | Local dev infrastructure | Good - working | Reference for local dev | KEEP | Root |
| `pom.xml` (root) | Parent POM | Good | Reference | KEEP | Root |
| `architecture/docs/DELIVERABLES_INDEX.md` | Deliverables index | Good | Reference for docs | UPDATE | `architecture/README.md` |

## Summary of Actions

| Action | Count | Documents |
|---|---|---|
| **KEEP** | 22 | Core architecture, RBAC, KYC, Environment, Local Dev, Prod Infra, CI/CD, Security, Config, Terraform, RBAC, KYC, ADR Register, ADRs, Roadmap, Audits, Gap Analysis, Bug Audit, Prod Readiness, Bug Audit, Testing Strategy |
| **UPDATE** | 5 | README.md, CITICORE_TARGET_STATE_ARCHITECTURE.md (move to root), IMPLEMENTATION_ROADMAP.md, DELIVERABLES_INDEX.md, README.md |
| **MERGE** | 6 | ARCHITECTURE_DIAGRAMS.md → diagrams/, ARCHITECTURE_REVIEW_OBSERVATIONS.md → observations.md, CHANGE_SUMMARY.md → changelog.md, CHANGELOG.md → CHANGELOG.md, ARCHITECTURE_DIAGRAMS.md → diagrams/, ADR docs → decisions/adrs/ |
| **ARCHIVE** | 1 | PROJECT_ARCHITECTURE.md → archive/ |
| **CREATE** | 4 | architecture/README.md, decisions/changelog.md, architecture/README.md, README.md (root) |
| **ARCHIVE (folder)** | 1 | PROJECT_ARCHITECTURE.md → archive/ |

## Final Documentation Structure

```
CitiCoreProject/
├── README.md                              # Updated - project overview + doc navigation
├── CHANGELOG.md                           # Updated - consolidated changelog
├── docker-compose.yml                     # Keep
├── pom.xml                                # Keep
├── architecture/
│   ├── README.md                          # NEW - architecture documentation index
│   ├── architecture.md                    # Authoritative target state architecture (moved from docs/)
│   ├── observations.md                    # Merged from ARCHITECTURE_REVIEW_OBSERVATIONS.md + CHANGE_SUMMARY
│   ├── diagrams/                          # NEW folder for Mermaid diagrams
│   │   ├── 01-auth-rbac.mmd
│   │   ├── 02-role-permission-flow.mmd
│   │   ├── 03-kyc-workflow.mmd
│   │   ├── 04-officer-manager.mmd
│   │   ├── 05-kyc-state-machine.mmd
│   │   ├── 06-kyc-communication.mmd
│   │   ├── 07-service-topology.mmd
│   │   ├── 08-database-topology.mmd
│   │   ├── 09-event-architecture.mmd
│   │   ├── 10-fund-transfer-saga.mmd
│   │   ├── 11-security-model.mmd
│   │   ├── 12-onboarding.mmd
│   │   ├── 13-role-hierarchy.mmd
│   │   └── 14-database-encryption.mmd
│   └── observations.md                    # Merged observations + change summary
├── decisions/
│   ├── adr-register.md                    # ADR register (moved from docs/)
│   ├── changelog.md                       # Merged from CHANGE_SUMMARY.md + CHANGELOG.md
│   └── adrs/                              # Individual ADRs
│       ├── ADR-001_DATABASE_TOPOLOGY.md
│       ├── ADR-002_SAGA_PATTERN.md
│       ├── ADR-003_KYC_SERVICE_OWNERSHIP.md
│       ├── ADR-004_CASE_ASSIGNMENT.md
│       ├── ADR-005_SOD_ENFORCEMENT.md
│       ├── ADR-006_JWT_CLAIMS_SIZE.md
│       ├── ADR-007_LEDGER_SERVICE.md
│       ├── ADR-008_COMPLIANCE_SERVICE.md
│       ├── ADR-009_MFA_METHODS.md
│       ├── ADR-010_MALWARE_SCANNING.md
│       ├── ADR-011_MYSQL_ENCRYPTION.md
│       ├── ADR-012_SECRETS_MANAGEMENT.md
│       ├── ADR-013_DATABASE_TLS.md
│       ├── ADR-014_SERVICE_DISCOVERY.md
│       └── ADR-015_KYC_VENDOR_INTEGRATION.md
├── security/
│   ├── rbac.md                            # RBAC architecture (moved from docs/)
│   ├── environment-security.md            # Environment security (moved from docs/)
│   ├── audit.md                           # Security audit reference
│   └── environment-security.md            # (duplicate - remove)
├── business-workflows/
│   └── kyc-workflow.md                    # KYC architecture (moved from docs/)
├── implementation/
│   ├── roadmap.md                         # (moved from IMPLEMENTATION_ROADMAP.md)
│   ├── sprint-plan.md                     # NEW - detailed sprint plan
│   ├── implementation-inventory.md        # NEW - feature status inventory
│   └── technical-debt.md                  # NEW - technical debt register
├── environments/
│   ├── environment-architecture.md        # (moved from docs/)
│   ├── local-development.md               # (moved from docs/)
│   └── production-deployment.md           # (moved from docs/)
├── engineering/
│   ├── cicd-strategy.md                   # (moved from docs/)
│   ├── configuration-management.md        # (moved from docs/)
│   ├── testing-strategy.md                # (moved from TESTING_STRATEGY.md)
│   └── configuration-management.md        # (duplicate - remove)
├── analysis/
│   ├── gap-analysis.md                    # (from BANKING_FUNCTIONALITY_GAP_ANALYSIS.md)
│   ├── bug-audit.md                       # (from BUG_AUDIT_REPORT.md)
│   ├── production-readiness.md            # (from PRODUCTION_READINESS.md)
│   └── bug-audit.md                       # duplicate
├── security/
│   ├── audit.md                           # (from SECURITY_AUDIT.md)
│   ├── rbac.md                            # already listed
│   └── environment-security.md            # already listed
├── database/
│   └── audit.md                           # (from DATABASE_AUDIT.md)
├── api/
│   └── documentation.md                   # (from API_DOCUMENTATION.md)
├── archive/
│   └── PROJECT_ARCHITECTURE_v1.md         # Archived original architecture
├── config-repo/                           # Keep
├── config-repo-local/                     # Keep
├── config-service/                        # Keep
├── eureka-server/                         # Keep
├── auth-service/                          # Keep
├── user-service/                          # Keep
├── account-service/                       # Keep
├── transaction-service/                   # Keep
├── notification-service/                  # Keep
├── apigateway-service/                    # Keep
├── kafka-events/                          # Keep
├── uploads/                               # Keep
└── mysql/                                 # Keep
```

## Key Decisions Explained

1. **PROJECT_ARCHITECTURE.md → ARCHIVE**: The original architecture document is superseded by the approved CITICORE_TARGET_STATE_ARCHITECTURE.md (v3.0). Archive for historical context.

2. **CITICORE_TARGET_STATE_ARCHITECTURE.md → architecture/architecture.md**: This is the authoritative architecture document. Move to root of architecture/ as the single source of truth.

3. **ARCHITECTURE_DIAGRAMS.md → architecture/diagrams/**: Split 17 Mermaid diagrams into individual .mmd files for better maintainability and rendering.

4. **ARCHITECTURE_REVIEW_OBSERVATIONS.md + CHANGE_SUMMARY.md → architecture/observations.md + decisions/changelog.md**: Merge overlapping content. Observations become observations.md; change summary becomes decisions/changelog.md.

7. **CHANGELOG.md + CHANGE_SUMMARY.md → decisions/changelog.md**: Consolidate into single authoritative changelog in decisions folder.

8. **ADR documents**: Keep all 9 ADR files but move to decisions/adrs/ with consistent naming.

8. **Individual architecture docs (RBAC, KYC, Environment, etc.)**: Move to appropriate subject folders (security/, business-workflows/, environments/) as authoritative sources.

9. **IMPLEMENTATION_ROADMAP.md → implementation/roadmap.md**: Update and enhance with sprint details.

9. **CREATE new files**: 
   - architecture/README.md (navigation index)
   - implementation/sprint-plan.md (detailed sprint breakdown)
   - implementation/implementation-inventory.md (feature status tracking)
   - implementation/technical-debt.md (technical debt register)
   - architecture/README.md (documentation index)

10. **ARCHIVE**: Move original PROJECT_ARCHITECTURE.md to archive/ for historical reference.

This structure eliminates duplication, establishes clear ownership for each topic, and creates a navigable documentation system aligned with the actual architecture.