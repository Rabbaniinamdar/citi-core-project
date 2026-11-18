# CitiCore Banking Platform - Architecture Diagrams (Mermaid)

This document contains all Mermaid diagrams for the CitiCore architecture, updated with RBAC and KYC Maker-Checker workflow and all 8 approved ADRs.

---

## 1. Authentication and RBAC Architecture

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'primaryColor': '#1e3a8a', 'fontSize': '12px'}}}%%
graph TB
    subgraph IDP ["Identity Provider"]
        AUTH[Auth Service<br/>Port 8081]
        DB_AUTH[(citicore_authdb)]
    end
    
    subgraph RBAC_ENGINE ["RBAC Engine"]
        ROLES[Role Definitions<br/>9 System Roles]
        PERMS[Permission Catalog<br/>28 Granular Permissions]
        MATRIX[Role-Permission Matrix]
        EVALUATOR[PermissionEvaluator<br/>SpEL + ABAC]
    end
    
    subgraph ENFORCEMENT ["Authorization Enforcement Layers"]
        GW[API Gateway<br/>JWT Validation + Coarse RBAC]
        FILTER[JwtAuthenticationFilter<br/>Token Validation + SecurityContext]
        ANNOTATION[@PreAuthorize<br/>Method-Level RBAC]
        LOGIC[Service Logic<br/>Ownership + ABAC + SoD]
        DATA[Repository Queries<br/>Row-Level Scoping]
    end
    
    subgraph CLAIMS ["JWT Claims"]
        TOKEN[Access Token<br/>15min HS256]
        REFRESH[Refresh Token<br/>7 days Opaque]
        CLAIMS[Claims: sub, email, roles, permissions,<br/>kycStatus, riskScore, assignedCases, authorizedCases]
    end
    
    subgraph REVOCATION ["Revocation Strategy"]
        CACHE[Redis Cache<br/>15min TTL + Invalidation]
        BLOCKLIST[Emergency Blocklist<br/>Immediate]
        REFRESH_REVOKE[Refresh Token Revocation<br/>Immediate]
    end
    
    AUTH --> DB_AUTH
    AUTH --> TOKEN
    AUTH --> REFRESH
    TOKEN --> CLAIMS
    ROLES --> MATRIX
    PERMS --> MATRIX
    MATRIX --> EVALUATOR
    GW --> FILTER
    FILTER --> ANNOTATION
    ANNOTATION --> LOGIC
    LOGIC --> DATA
    EVALUATOR --> ANNOTATION
    CLAIMS --> GW
    CLAIMS --> FILTER
    CACHE --> EVALUATOR
    BLOCKLIST --> GW
    REFRESH_REVOKE --> AUTH
```

---

## 2. Role-to-Permission Authorization Flow

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'primaryColor': '#1e3a8a', 'fontSize': '11px'}}}%%
sequenceDiagram
    participant User
    participant Client
    participant GW as API Gateway
    participant AUTH as Auth Service
    participant KYC as KYC Workflow Service
    participant REDIS as Redis Cache
    participant DB as Database
    
    Note over User,DB: 1. Login & Token Generation
    User->>Client: Credentials + MFA
    Client->>GW: POST /auth/login
    GW->>AUTH: Forward
    AUTH->>DB: Validate credentials
    AUTH->>DB: Fetch UserRole + Permissions
    AUTH->>AUTH: Compute JWT Claims (roles + permissions + case assignments)
    AUTH-->>GW: JWT Access + Refresh Token
    GW-->>Client: Tokens
    
    Note over User,DB: 2. Resource Access Request
    Client->>GW: GET /api/v1/kyc/cases/KYC-001\nAuthorization: Bearer <token>
    GW->>GW: Validate JWT (sig, exp, claims)
    GW->>GW: Extract roles, permissions, userId
    GW->>GW: Coarse RBAC: Check endpoint permission
    GW->>REDIS: Check blocklist
    REDIS-->>GW: Not blocked
    GW->>KYC: Forward + Headers\nX-User-Id, X-User-Roles, X-User-Permissions
    
    Note over User,DB: 3. Service-Level Authorization
    KYC->>KYC: JwtAuthenticationFilter validates
    KYC->>KYC: Set SecurityContext
    KYC->>KYC: @PreAuthorize("hasPermission(#caseId, 'KYC_CASE', 'APPROVE')")
    KYC->>EVALUATOR: PermissionEvaluator.hasPermission()
    EVALUATOR->>REDIS: Get cached permissions
    alt Cache Hit
        REDIS-->>EVALUATOR: Permissions + Case Assignments
    else Cache Miss
        EVALUATOR->>DB: Fetch KycCaseAssignment
        DB-->>EVALUATOR: Assignment data
        EVALUATOR->>REDIS: Cache permissions (15min)
    end
    EVALUATOR->>EVALUATOR: Check SoD: managerId != reviewingOfficerId
    EVALUATOR->>EVALUATOR: Check scope: authorizedManagerId == userId
    EVALUATOR-->>KYC: true/false
    KYC->>KYC: Execute business logic
    KYC-->>GW: Response
    GW-->>Client: Response
```

---

## 3. KYC End-to-End Workflow

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'primaryColor': '#1e3a8a', 'fontSize': '10px'}}}%%
flowchart TD
    subgraph CUSTOMER_JOURNEY ["Customer Journey"]
        C1[Register & Verify Email]
        C2[Complete Profile]
        C3[Upload KYC Documents\nPAN, Aadhaar, Address, Selfie]
        C4[Submit KYC Application]
        C5[View Status: SUBMITTED]
        C6{Info Requested?}
        C7[Provide Additional Info]
        C8[View Final Decision\nAPPROVED / REJECTED]
        C9[If APPROVED: Open Account]
    end
    
    subgraph KYC_WORKFLOW ["KYC Workflow Service"]
        W1[Create KycCase\nStatus: SUBMITTED]
        W2[Auto-Assign to Officer\nStatus: PENDING_REVIEW]
        W3[Officer Claims Case\nStatus: UNDER_REVIEW]
        W4[Officer Reviews Documents\nPerforms Checks]
        W5[Officer Records Findings]
        W6{All Checks Pass?}
        W7[Request More Info\nStatus: MORE_INFO_REQUIRED]
        W8[Recommend Approval\nStatus: RECOMMENDED_APPROVAL]
        W9[Recommend Rejection\nStatus: RECOMMENDED_REJECTION]
        W10[Auto-Forward to Manager\nStatus: PENDING_MANAGER_APPROVAL]
        W11{Manager Action}
        W12[Approve\nStatus: APPROVED]
        W13[Reject\nStatus: REJECTED]
        W14[Return for Review\nStatus: UNDER_REVIEW]
        W15[Escalate\nStatus: ESCALATED]
        W16[Publish KYC_APPROVED Event]
    end
    
    subgraph OFFICER_ACTIONS ["KYC Officer (Maker)"]
        O1[Login & View Assigned Cases]
        O2[Select Case]
        O3[Review Profile & Documents]
        O4[Perform Verification:\n- Document Authenticity\n- OCR Data Match\n- Face Match\n- Sanctions Screen]
        O5[Record Findings per Document]
        O6[Decide: Request Info / Recommend]
    end
    
    subgraph MANAGER_ACTIONS ["KYC Manager (Checker)"]
        M1[Login & View Pending Approval]
        M2[Select Case]
        M3[Review Officer Findings]
        M4{SoD Check:\nManager != Officer?}
        M5[BLOCK: Self-Approval Prohibited]
        M6[Decide: Approve/Reject/Return/Escalate]
        M7[Record Decision Reason]
    end
    
    subgraph INTEGRATIONS ["Downstream Integrations"]
        I1[Notification Service\nEmails to Customer/Officer/Manager]
        I2[Account Service\nEnable Account Creation]
        I3[Compliance Service\nAudit Trail + Escalations]
        I4[Event Bus\nKafka Topics]
    end
    
    C1 --> C2 --> C3 --> C4 --> C5
    C4 --> W1
    W1 --> W2
    W2 --> W3
    W3 --> O1
    O1 --> O2 --> O3 --> O4 --> O5 --> O6
    O6 -->|Request Info| W7
    W7 --> C6
    C6 -->|Yes| C7
    C7 --> W3
    O6 -->|Recommend Approve| W8
    O6 -->|Recommend Reject| W9
    W8 --> W10
    W9 --> W10
    W10 --> M1
    M1 --> M2 --> M3 --> M4
    M4 -->|Yes| M5
    M4 -->|No| M6
    M6 -->|Approve| W12
    M6 -->|Reject| W13
    M6 -->|Return| W14
    M6 -->|Escalate| W15
    W14 --> W3
    W15 --> Compliance[Compliance Resolves] --> W3
    W12 --> W16
    W16 --> I1
    W16 --> I2
    W16 --> I3
    W13 --> I1
    W13 --> I3
    I2 --> C9
    C5 --> C8
    W13 --> C8
```

---

## 4. KYC Officer and KYC Manager Interaction

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'primaryColor': '#1e3a8a', 'fontSize': '11px'}}}%%
sequenceDiagram
    participant Officer as KYC Officer
    participant GW as API Gateway
    participant KYC as KYC Workflow Service
    participant USER as User Service
    participant KAFKA as Kafka
    participant NOTIF as Notification Service
    participant Manager as KYC Manager
    participant ACC as Account Service
    
    Note over Officer,ACC: Phase 1: Case Assignment
    KYC->>KYC: Auto-assign case KYC-20261118-001 to Officer
    KYC->>KAFKA: Publish KYC_CASE_ASSIGNED
    KAFKA->>NOTIF: Notify Officer
    
    Note over Officer,ACC: Phase 2: Officer Review
    Officer->>GW: GET /kyc/cases/assigned
    GW->>KYC: Forward (RBAC: KYC_OFFICER + assigned)
    KYC->>USER: Feign: Get customer profile + documents
    USER-->>KYC: Profile + S3 Document URLs
    KYC-->>GW: Case list
    GW-->>Officer: Assigned cases
    
    Officer->>GW: GET /kyc/cases/KYC-001
    GW->>KYC: Forward (RBAC: assigned + UNDER_REVIEW)
    KYC-->>GW: Case details + documents
    GW-->>Officer: Full case view
    
    Officer->>GW: POST /kyc/cases/KYC-001/findings\n{findings: [{doc: PAN, status: VERIFIED}, ...]}
    GW->>KYC: Forward (RBAC: assigned)
    KYC->>KYC: Validate & save findings
    KYC->>KAFKA: Publish KYC_CASE_REVIEWED
    KAFKA->>NOTIF: Log review completion
    
    Officer->>GW: POST /kyc/cases/KYC-001/recommend\n{recommendation: APPROVE, remarks: "All clear"}
    GW->>KYC: Forward (RBAC: assigned)
    KYC->>KYC: Validate complete findings
    KYC->>KYC: Set status=RECOMMENDED_APPROVAL
    KYC->>KYC: Set reviewingOfficerId=Officer
    KYC->>KAFKA: Publish KYC_CASE_RECOMMENDED
    KAFKA->>NOTIF: Notify Manager(s)
    
    Note over Officer,ACC: Phase 3: Manager Decision
    Manager->>GW: GET /kyc/cases/pending-approval
    GW->>KYC: Forward (RBAC: KYC_MANAGER + authorized)
    KYC->>KYC: Filter: authorizedManagerId=Manager AND reviewingOfficerId!=Manager
    KYC-->>GW: Authorized cases only
    GW-->>Manager: Pending approval cases
    
    Manager->>GW: GET /kyc/cases/KYC-001/review
    GW->>KYC: Forward (RBAC: authorized)
    KYC->>KYC: Fetch case + all findings + recommendation
    KYC-->>GW: Complete review package
    GW-->>Manager: Officer findings + recommendation
    
    Manager->>GW: POST /kyc/cases/KYC-001/approve\n{approvalReason: "Verified all docs"}
    GW->>KYC: Forward (RBAC: KYC_MANAGER + authorized)
    KYC->>KYC: **SoD CHECK: Manager != reviewingOfficerId** ✅
    KYC->>KYC: Set status=APPROVED, decidingManagerId=Manager
    KYC->>KAFKA: Publish KYC_CASE_APPROVED
    KAFKA->>NOTIF: Notify Customer (approved)
    KAFKA->>ACC: KYC_APPROVED event
    ACC->>ACC: Allow account creation for customer
    KAFKA->>Compliance: Audit trail
```

---

## 5. KYC State-Transition Diagram

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'primaryColor': '#1e3a8a', 'fontSize': '10px'}}}%%
stateDiagram-v2
    [*] --> DRAFT : Customer starts KYC
    
    DRAFT --> SUBMITTED : Customer submits\nPOST /users/kyc/submit\n[CUSTOMER: owner]
    
    SUBMITTED --> PENDING_REVIEW : Auto-assign or Manager assigns\n[SYSTEM / KYC_MANAGER]
    
    PENDING_REVIEW --> UNDER_REVIEW : Officer starts review\n[KYC_OFFICER: assigned]
    
    UNDER_REVIEW --> MORE_INFO_REQUIRED : Officer requests info\n[KYC_OFFICER: assigned]\nSpecific docs identified
    
    MORE_INFO_REQUIRED --> RESUBMITTED : Customer provides info\n[CUSTOMER: owner]\nAll requested items provided
    
    RESUBMITTED --> UNDER_REVIEW : Auto-transition\n[SYSTEM]
    
    UNDER_REVIEW --> RECOMMENDED_APPROVAL : Officer recommends approve\n[KYC_OFFICER: assigned]\nAll checks passed, findings recorded
    
    UNDER_REVIEW --> RECOMMENDED_REJECTION : Officer recommends reject\n[KYC_OFFICER: assigned]\nDeficiency found, findings recorded
    
    RECOMMENDED_APPROVAL --> PENDING_MANAGER_APPROVAL : Auto-forward\n[SYSTEM]
    
    RECOMMENDED_REJECTION --> PENDING_MANAGER_APPROVAL : Auto-forward\n[SYSTEM]
    
    PENDING_MANAGER_APPROVAL --> APPROVED : Manager approves\n[KYC_MANAGER: authorized, ≠ reviewer]\nRecords approval reason
    
    PENDING_MANAGER_APPROVAL --> REJECTED : Manager rejects\n[KYC_MANAGER: authorized, ≠ reviewer]\nRecords rejection reason
    
    PENDING_MANAGER_APPROVAL --> UNDER_REVIEW : Manager returns\n[KYC_MANAGER: authorized]\nIncomplete, needs more work
    
    PENDING_MANAGER_APPROVAL --> ESCALATED : Manager escalates\n[KYC_MANAGER: authorized]\nExceptional case
    
    ESCALATED --> UNDER_REVIEW : Compliance resolves\n[COMPLIANCE_OFFICER]\nEscalation resolved
    
    APPROVED --> [*] : KYC Complete\nPublish KYC_APPROVED\nEnable account creation
    
    REJECTED --> [*] : KYC Complete\nPublish KYC_REJECTED\nCustomer can reapply
    
    note right of DRAFT
        Customer fills application
        Uploads documents
        Can save as draft
    end note
    
    note right of UNDER_REVIEW
        Officer performs:
        - Document authenticity
        - OCR data extraction
        - Face match (selfie)
        - Sanctions/PEP screen
        Records findings per doc
    end note
    
    note right of PENDING_MANAGER_APPROVAL
        Manager reviews:
        - Officer findings
        - Recommendation
        - Risk indicators
        SoD enforced: manager ≠ officer
    end note
    
    note right of APPROVED
        Triggers:
        - Customer notification
        - Account Service enablement
        - Compliance audit log
    end note
```

---

## 6. KYC Service Communication and Audit Flow

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'primaryColor': '#1e3a8a', 'fontSize': '10px'}}}%%
graph TB
    subgraph CLIENTS ["Clients"]
        CUST[Customer App]
        OFF[Officer Portal]
        MGR[Manager Portal]
        COMP[Compliance Portal]
    end
    
    subgraph GATEWAY ["API Gateway"]
        GW[Spring Cloud Gateway\nJWT + RBAC + Rate Limit]
    end
    
    subgraph SERVICES ["Microservices"]
        KYC[KYC Workflow Service\nPort 8087]
        USER[User Service\nPort 8082]
        AUTH[Auth Service\nPort 8081]
        NOTIF[Notification Service\nPort 8085]
        ACC[Account Service\nPort 8083]
        COMPLIANCE[Compliance Service\nPort 8088]
    end
    
    subgraph DATA ["Data Layer"]
        DB_KYC[(citicore_kycdb\nCases, Assignments, History, Findings)]
        DB_USER[(citicore_userdb\nProfiles, Documents)]
        DB_AUTH[(citicore_authdb\nUsers, Roles)]
        REDIS[(Redis Cache\nAssignments, Permissions, SLA)]
        S3[(AWS S3 SSE-KMS\nKYC Documents)]
        KAFKA[(Kafka Cluster\n3 Brokers, RF=3)]
    end
    
    subgraph AUDIT ["Audit & Monitoring"]
        AUDIT_DB[(Audit Logs\nImmutable + Hash Chained)]
        GRAF[Grafana Dashboards\nKYC KPIs, SLA, SoD]
        ALERT[AlertManager\nSLA Breach, SoD Violation]
    end
    
    CUST --> GW
    OFF --> GW
    MGR --> GW
    COMP --> GW
    
    GW --> KYC
    GW --> USER
    GW --> AUTH
    
    KYC --> DB_KYC
    KYC --> REDIS
    KYC --> KAFKA
    KYC -.->|Feign| USER
    KYC -.->|Feign| AUTH
    KYC -.->|Feign| NOTIF
    
    USER --> DB_USER
    USER --> S3
    USER --> KAFKA
    
    AUTH --> DB_AUTH
    AUTH --> KAFKA
    
    NOTIF --> KAFKA
    
    ACC -.->|Feign| KYC
    ACC --> KAFKA
    
    COMPLIANCE --> KAFKA
    COMPLIANCE --> DB_KYC
    
    KAFKA --> KYC
    KAFKA --> NOTIF
    KAFKA --> ACC
    KAFKA --> COMPLIANCE
    
    KYC -.->|Audit Events| AUDIT_DB
    KYC -.->|Metrics| GRAF
    KYC -.->|Alerts| ALERT
    
    style KYC fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px
    style DB_KYC fill:#fff3e0,stroke:#ef6c00,stroke-width:2px
    style AUDIT_DB fill:#fce4ec,stroke:#c2185b,stroke-width:2px
    style KAFKA fill:#e3f2fd,stroke:#1565c0,stroke-width:2px
```

---

## 7. Complete Service Topology (Production - AWS EKS)

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'primaryColor': '#1e3a8a', 'fontSize': '10px'}}}%%
graph TB
    subgraph EXTERNAL ["External Clients"]
        CLIENT[Web/Mobile App]
        ADMIN[Admin Portal]
        EXT_API[External APIs]
    end

    subgraph GATEWAY ["API Gateway Layer"]
        GW[API Gateway<br/>Port 8080<br/>Spring Cloud Gateway]
    end

    subgraph DISCOVERY ["Service Discovery & Config"]
        K8S_DNS[Kubernetes DNS (CoreDNS)<br/>Production Service Discovery]
        CONFIG[Config Server<br/>Port 8888]
        EUREKA[Eureka Server<br/>Port 8761<br/><i>Local Development Only</i>]
    end

    subgraph CORE ["Core Banking Services"]
        AUTH[Auth Service<br/>Port 8081]
        USER[User Service<br/>Port 8082]
        KYC[KYC Workflow Service<br/>Port 8087<br/><b>NEW</b>]
        ACC[Account Service<br/>Port 8083]
        TXN[Transaction Service<br/>Port 8084]
        NOTIF[Notification Service<br/>Port 8085]
        LEDGER[Ledger Service<br/>Port 8086<br/><i>Planned</i>]
        COMPLIANCE[Compliance Service<br/>Port 8088<br/><i>Planned</i>]
    end

    subgraph DATA ["AWS Data Layer"]
        RDS[(AWS RDS MySQL 8.0<br/>Multi-Schema, KMS Encrypted)]
        REDIS[(Redis 7<br/>ElastiCache, Cache & Rate Limit)]
        KAFKA[Kafka 3.8<br/>MSK, Event Bus]
        S3[(AWS S3<br/>SSE-KMS, KYC Documents)]
    end

    subgraph SECURITY ["Security Services"]
        SECRETS[AWS Secrets Manager<br/>DB Credentials, JWT Secret]
        KMS[AWS KMS<br/>RDS Encryption + Field Encryption]
    end

    CLIENT --> GW
    ADMIN --> GW
    EXT_API --> GW

    GW --> K8S_DNS
    GW --> CONFIG
    GW --> AUTH
    GW --> USER
    GW --> KYC
    GW --> ACC
    GW --> TXN
    GW --> NOTIF
    GW -.-> LEDGER
    GW -.-> COMPLIANCE

    AUTH --> K8S_DNS
    AUTH --> CONFIG
    AUTH --> RDS
    AUTH --> KAFKA
    AUTH --> REDIS
    AUTH --> SECRETS
    AUTH -.-> KMS

    USER --> K8S_DNS
    USER --> CONFIG
    USER --> RDS
    USER --> KAFKA
    USER --> S3
    USER --> SECRETS

    KYC --> K8S_DNS
    KYC --> CONFIG
    KYC --> RDS
    KYC --> KAFKA
    KYC --> USER
    KYC --> AUTH
    KYC --> S3
    KYC --> SECRETS

    ACC --> K8S_DNS
    ACC --> CONFIG
    ACC --> RDS
    ACC --> KAFKA
    ACC --> REDIS
    ACC --> USER
    ACC --> KYC
    ACC --> SECRETS

    TXN --> K8S_DNS
    TXN --> CONFIG
    TXN --> RDS
    TXN --> KAFKA
    TXN --> ACC
    TXN --> SECRETS

    NOTIF --> K8S_DNS
    NOTIF --> CONFIG
    NOTIF --> KAFKA
    NOTIF --> SECRETS

    LEDGER --> K8S_DNS
    LEDGER --> CONFIG
    LEDGER --> RDS
    LEDGER --> KAFKA
    LEDGER --> ACC
    LEDGER --> TXN
    LEDGER --> SECRETS

    COMPLIANCE --> K8S_DNS
    COMPLIANCE --> CONFIG
    COMPLIANCE --> RDS
    COMPLIANCE --> KAFKA
    COMPLIANCE --> KYC
    COMPLIANCE --> USER
    COMPLIANCE --> SECRETS

    EUREKA -.->|Local Dev Only| AUTH
    EUREKA -.->|Local Dev Only| USER
    EUREKA -.->|Local Dev Only| KYC
    EUREKA -.->|Local Dev Only| ACC
    EUREKA -.->|Local Dev Only| TXN
    EUREKA -.->|Local Dev Only| NOTIF
```

---

## 8. Database Topology (AWS RDS - Single Instance, Multi-Schema)

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'primaryColor': '#1e3a8a', 'fontSize': '10px'}}}%%
graph TB
    subgraph RDS_MYSQL ["AWS RDS for MySQL 8.0 (Single Deployment, KMS Encrypted)"]
        subgraph SCHEMAS ["Service Schemas (Separate Credentials)"]
            AUTH_SCHEMA[(citicore_authdb<br/>users, roles, user_roles, refresh_tokens, login_attempts)]
            KYC_SCHEMA[(citicore_kycdb<br/>kyc_cases, kyc_case_assignments,<br/>kyc_case_history, kyc_review_findings,<br/>kyc_document_refs)]
            ACC_SCHEMA[(citicore_account<br/>accounts, account_statements,<br/>account_outbox, account_sequences,<br/>account_holds)]
            TXN_SCHEMA[(citicore_transactiondb<br/>transactions, outbox_events,<br/>idempotency_keys, daily_limits)]
            USER_SCHEMA[(citicore_userdb<br/>user_profiles, kyc_documents)]
            LEDGER_SCHEMA[(citicore_ledgerdb<br/>ledger_accounts, journal_entries,<br/>journal_lines, ledger_balances<br/><i>Planned</i>)]
            COMPLIANCE_SCHEMA[(citicore_compliancedb<br/>sanctions, pep, monitoring,<br/>sar, reports<br/><i>Planned</i>)]
        end
        
        subgraph ENCRYPTION ["Encryption Layer"]
            KMS_RDS[AWS KMS<br/>Customer-Managed Key<br/>RDS Storage Encryption]
            KMS_FIELD[AWS KMS<br/>Field Encryption Key<br/>Envelope Encryption]
        end
    end

    subgraph REDIS_CLUSTER ["Redis Cluster (ElastiCache)"]
        CACHE[(Cache: Balance, Session, Rate Limit,<br/>Case Assignments, User Permissions)]
    end

    subgraph KAFKA_CLUSTER ["Kafka Cluster (MSK)"]
        TOPICS[Topics: 3 partitions, RF=3<br/>transfer-requested, debit-success,<br/>credit-success, reversal, kyc, kyc-case,<br/>kyc-notification, otp, account-events, DLQs]
    end

    subgraph S3_STORAGE ["AWS S3 (SSE-KMS Encrypted)"]
        KYC_DOCS_QUARANTINE[(Quarantine Bucket<br/>Unscanned Uploads)]
        KYC_DOCS_CLEAN[(Clean Bucket<br/>Scanned Documents)]
    end

    AUTH_SVC[Auth Service] --> AUTH_SCHEMA
    USER_SVC[User Service] --> USER_SCHEMA
    KYC_SVC[KYC Workflow Service] --> KYC_SCHEMA
    KYC_SVC --> CACHE
    ACC_SVC[Account Service] --> ACC_SCHEMA
    ACC_SVC --> CACHE
    TXN_SVC[Transaction Service] --> TXN_SCHEMA
    LEDGER_SVC[Ledger Service] --> LEDGER_SCHEMA
    COMPLIANCE_SVC[Compliance Service] --> COMPLIANCE_SCHEMA
    ALL_SVC[All Services] --> KAFKA_CLUSTER
    USER_SVC --> KYC_DOCS_QUARANTINE
    KYC_SVC --> KYC_DOCS_CLEAN
    
    KMS_RDS -.->|Transparent| RDS_MYSQL
    KMS_FIELD -.->|Envelope| SCHEMAS
```

---

## 9. Event-Driven Architecture (Async Flow)

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'primaryColor': '#1e3a8a', 'fontSize': '10px'}}}%%
graph TB
    subgraph SYNC ["Synchronous (Feign/REST)"]
        CLIENT[Client] --> GW[API Gateway]
        GW --> AUTH[Auth Service]
        GW --> USER[User Service]
        GW --> KYC[KYC Workflow Service]
        GW --> ACC[Account Service]
        GW --> TXN[Transaction Service]
        
        TXN -.->|validateTransfer| ACC
        ACC -.->|getKycStatus| KYC
        ACC -.->|getProfile| USER
        KYC -.->|getProfile| USER
        KYC -.->|verifyRoles| AUTH
    end
    
    subgraph ASYNC ["Asynchronous (Kafka)"]
        KAFKA[(Kafka Event Bus)]
        
        AUTH -->|otp-verification-topic| KAFKA
        AUTH -->|user-created-topic| KAFKA
        USER -->|kyc-topic| KAFKA
        USER -->|kyc-submission-topic| KAFKA
        USER -->|kyc-document-topic| KAFKA
        
        KYC -->|kyc-case-topic| KAFKA
        KYC -->|kyc-notification-topic| KAFKA
        
        ACC -->|account-events-topic| KAFKA
        ACC -->|debit-success-topic| KAFKA
        ACC -->|debit-failed-topic| KAFKA
        ACC -->|credit-success-topic| KAFKA
        ACC -->|credit-failed-topic| KAFKA
        ACC -->|reversal-success-topic| KAFKA
        ACC -->|balance-hold-topic| KAFKA
        
        TXN -->|transfer-requested-topic| KAFKA
        
        KAFKA -->|transfer-requested-topic| ACC
        KAFKA -->|debit-success-topic| ACC
        KAFKA -->|reversal-topic| ACC
        KAFKA -->|kyc-case-topic| ACC
        
        KAFKA -->|credit-success-topic| TXN
        KAFKA -->|credit-failed-topic| TXN
        KAFKA -->|reversal-success-topic| TXN
        
        KAFKA -->|credit-success-topic| NOTIF[Notification Service]
        KAFKA -->|credit-failed-topic| NOTIF
        KAFKA -->|reversal-success-topic| NOTIF
        KAFKA -->|kyc-topic| NOTIF
        KAFKA -->|kyc-case-topic| NOTIF
        KAFKA -->|kyc-notification-topic| NOTIF
        KAFKA -->|otp-verification-topic| NOTIF
        
        KAFKA -->|kyc-case-topic| COMPLIANCE[Compliance Service]
        KAFKA -.->|Planned| LEDGER[Ledger Service]
    end
```

---

## 10. Fund Transfer Saga (Hybrid: Orchestration for Transfers)

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'primaryColor': '#1e3a8a', 'fontSize': '10px'}}}%%
stateDiagram-v2
    [*] --> INITIATED: Client POST /transfer
    INITIATED --> DEBIT_REQUESTED: Save Transaction + Outbox
    DEBIT_REQUESTED --> DEBIT_PROCESSING: Transaction Service orchestrates debit
    DEBIT_PROCESSING --> DEBIT_SUCCESS: Account Service debits sender
    DEBIT_PROCESSING --> DEBIT_FAILED: Insufficient balance / Account invalid
    
    DEBIT_SUCCESS --> CREDIT_REQUESTED: Transaction Service orchestrates credit
    CREDIT_REQUESTED --> CREDIT_PROCESSING: Account Service credits receiver
    CREDIT_PROCESSING --> CREDIT_SUCCESS: Credit completed
    CREDIT_PROCESSING --> CREDIT_FAILED: Receiver account invalid / Closed
    
    CREDIT_SUCCESS --> COMPLETED: Mark CREDIT_SUCCESS, Notify
    COMPLETED --> [*]: Saga Complete
    
    CREDIT_FAILED --> REVERSAL_TRIGGERED: Transaction Service initiates reversal
    REVERSAL_TRIGGERED --> REVERSAL_PROCESSING: Account Service credits sender
    REVERSAL_PROCESSING --> REVERSAL_SUCCESS: Reversal completed
    REVERSAL_SUCCESS --> REVERSED: Mark REVERSED, Notify
    REVERSED --> [*]: Saga Complete (Compensated)
    
    DEBIT_FAILED --> FAILED: Mark FAILED, Notify
    FAILED --> [*]: Saga Complete (Failed)
    
    note right of INITIATED
        Transaction Service = Orchestrator
        State persisted in citicore_transactiondb
    end note
    
    note right of CREDIT_FAILED
        Explicit compensation via reversal-topic
        Idempotent reversal processing
    end note
```

### Hybrid Saga Pattern Summary

| Domain | Pattern | Owner | Rationale |
|--------|---------|-------|-----------|
| **Fund Transfers** | **Orchestration** | Transaction Service | Financial consistency; centralized state; explicit compensation |
| **Notifications** | **Choreography** | Notification Service | Independent; fire-and-forget; no compensation |
| **Audit Events** | **Async Consumption** | Compliance Service | Independent; idempotent; no saga participation |

---

## 11. Security Architecture - Authorization Model

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'primaryColor': '#1e3a8a', 'fontSize': '11px'}}}%%
graph TB
    subgraph RBAC ["Role-Based Access Control"]
        ROLES[Roles: CUSTOMER, KYC_OFFICER, KYC_MANAGER, SUPPORT_AGENT, OPERATIONS_OFFICER, COMPLIANCE_OFFICER, ADMIN, AUDITOR, SERVICE]
        PERMISSIONS[Permissions: 28 granular permissions\n(See RBAC_ARCHITECTURE.md)]
        ROLE_PERM[Role-Permission Mapping\n(See Section 4 Matrix)]
    end
    
    subgraph ABAC ["Attribute-Based Access Control"]
        ATTRS[Attributes: userId, accountOwnership, kycCaseAssignment, kycCaseAuthorization, kycStatus, riskScore, caseScope]
        POLICIES[Policies: Ownership, KYC Case Assignment, KYC Case Authorization, Separation of Duties, KYC Required, Daily Limits]
    end
    
    subgraph SOD ["Separation of Duties"]
        SOD_ENGINE[SoD Enforcement Engine\nKYC Workflow Service]
        SOD_RULES[Rules: Officer ≠ Manager for same case\nNo self-assignment\nCompliance independence]
    end
    
    subgraph ENFORCEMENT ["Enforcement Points"]
        GATEWAY[API Gateway: JWT validation, RBAC coarse check, Rate limiting]
        FILTER[JwtAuthenticationFilter: Token validation, SecurityContext, Permission extraction]
        ANNOTATION[@PreAuthorize: Method-level RBAC + SpEL]
        SERVICE[Service Logic: ABAC ownership checks, Business rules, SoD]
        KYC_ENFORCE[KYC Workflow Engine: State machine, Maker-Checker enforcement, Case assignment]
    end
    
    ROLES --> PERMISSIONS
    PERMISSIONS --> ROLE_PERM
    ATTRS --> POLICIES
    SOD_RULES --> SOD_ENGINE
    GATEWAY --> FILTER
    FILTER --> ANNOTATION
    ANNOTATION --> SERVICE
    SERVICE --> KYC_ENFORCE
    SOD_ENGINE --> KYC_ENFORCE
    POLICIES --> SERVICE
```

---

## 12. Customer Onboarding with KYC Workflow

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'primaryColor': '#1e3a8a', 'fontSize': '9px'}}}%%
flowchart TD
    START([Customer Visits App]) --> REGISTER[POST /auth/register]
    REGISTER --> VALIDATE{Valid Input?}
    VALIDATE -->|No| ERROR1[Return 400]
    VALIDATE -->|Yes| SAVE_USER[Save User (PENDING)]
    SAVE_USER --> GEN_OTP[Generate 8-char OTP]
    GEN_OTP --> SAVE_OTP[Save OTP (10min expiry)]
    SAVE_OTP --> PUBLISH_OTP[Publish otp-verification-topic]
    PUBLISH_OTP --> EMAIL_OTP[Notification: Send Email]
    EMAIL_OTP --> RETURN[Return 201]
    RETURN --> VERIFY_UI[Customer Enters OTP]
    VERIFY_UI --> VERIFY_API[POST /auth/verify]
    VERIFY_API --> CHECK_OTP{OTP Valid?}
    CHECK_OTP -->|No| ERROR2[Return 400]
    CHECK_OTP -->|Yes| ACTIVATE[User: ACTIVE, verified]
    ACTIVATE --> ISSUE_TOKENS[Generate JWT + Refresh]
    ISSUE_TOKENS --> RETURN_TOKENS[Return Tokens]
    RETURN_TOKENS --> PROFILE[Complete Profile]
    PROFILE --> KYC_UPLOAD[Upload KYC Docs]
    KYC_UPLOAD --> KYC_SUBMIT[Submit KYC Application]
    KYC_SUBMIT --> KYC_PENDING[KYC Status: SUBMITTED]
    KYC_PENDING --> KYC_WF_CREATE[KYC Workflow: Create Case]
    KYC_WF_CREATE --> AUTO_ASSIGN[Auto-assign to Officer]
    AUTO_ASSIGN --> NOTIFY_OFFICER[Notify Officer]
    NOTIFY_OFFICER --> OFFICER_REVIEW[Officer Reviews]
    OFFICER_REVIEW --> REVIEW_ACTION{Officer Action}
    REVIEW_ACTION -->|Request Info| REQUEST_INFO[Status: MORE_INFO_REQUIRED]
    REQUEST_INFO --> CUSTOMER_RESPOND[Customer Provides Info]
    CUSTOMER_RESPOND --> RESUBMITTED[Status: RESUBMITTED]
    RESUBMITTED --> OFFICER_REVIEW
    REVIEW_ACTION -->|Recommend Approve| RECOMMEND_APPROVE[Status: RECOMMENDED_APPROVAL]
    REVIEW_ACTION -->|Recommend Reject| RECOMMEND_REJECT[Status: RECOMMENDED_REJECTION]
    RECOMMEND_APPROVE --> MANAGER_REVIEW[Manager Reviews]
    RECOMMEND_REJECT --> MANAGER_REVIEW
    MANAGER_REVIEW --> MANAGER_ACTION{Manager Action}
    MANAGER_ACTION -->|Approve| APPROVED[Status: APPROVED]
    MANAGER_ACTION -->|Reject| REJECTED[Status: REJECTED]
    MANAGER_ACTION -->|Return| RETURN_REVIEW[Status: UNDER_REVIEW]
    MANAGER_ACTION -->|Escalate| ESCALATED[Status: ESCALATED]
    RETURN_REVIEW --> OFFICER_REVIEW
    ESCALATED --> COMPLIANCE_RESOLVE[Compliance Resolves]
    COMPLIANCE_RESOLVE --> OFFICER_REVIEW
    APPROVED --> KYC_APPROVED_EVENT[Publish KYC_APPROVED]
    KYC_APPROVED_EVENT --> ACCOUNT_CREATE[Customer Creates Account]
    REJECTED --> REAPPLY[Customer Can Reapply]
    REAPPLY --> KYC_UPLOAD
    ACCOUNT_CREATE --> KYC_CHECK[Feign KYC Status]
    KYC_CHECK -->|Not Approved| ERROR3[Return 400: KYC Required]
    KYC_CHECK -->|Approved| CREATE_ACC_DB[Create Account]
    CREATE_ACC_DB --> PUBLISH_ACC[Publish ACCOUNT_CREATED]
    PUBLISH_ACC --> LEDGER_POST[Ledger: Journal Entry]
    LEDGER_POST --> SUCCESS[Account Created]
    SUCCESS --> END([Onboarding Complete])
```

---

## 13. Role Hierarchy and Permission Inheritance

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'primaryColor': '#1e3a8a', 'fontSize': '11px'}}}%%
graph TB
    subgraph ROLE_HIERARCHY ["Role Hierarchy (Permission Inheritance)"]
        ADMIN[ROLE_ADMIN<br/>Super Role<br/>All 28 Permissions]
        
        COMPLIANCE[ROLE_COMPLIANCE_OFFICER<br/>12 Permissions]
        KYC_MGR[ROLE_KYC_MANAGER<br/>11 Permissions]
        OPERATIONS[ROLE_OPERATIONS_OFFICER<br/>10 Permissions]
        SUPPORT[ROLE_SUPPORT_AGENT<br/>6 Permissions]
        AUDITOR[ROLE_AUDITOR<br/>5 Permissions]
        
        KYC_OFF[ROLE_KYC_OFFICER<br/>8 Permissions]
        
        CUSTOMER[ROLE_CUSTOMER<br/>13 Permissions<br/><i>Independent</i>]
        SERVICE[ROLE_SERVICE<br/>1 Permission<br/><i>Independent</i>]
    end
    
    ADMIN --> COMPLIANCE
    ADMIN --> KYC_MGR
    ADMIN --> OPERATIONS
    ADMIN --> SUPPORT
    ADMIN --> AUDITOR
    
    KYC_MGR --> KYC_OFF
    
    style ADMIN fill:#ffebee,stroke:#c62828,stroke-width:2px
    style KYC_MGR fill:#fff3e0,stroke:#ef6c00,stroke-width:2px
    style KYC_OFF fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px
    style COMPLIANCE fill:#e3f2fd,stroke:#1565c0,stroke-width:2px
    style OPERATIONS fill:#f3e5f5,stroke:#7b1fa2,stroke-width:2px
    style SUPPORT fill:#fce4ec,stroke:#c2185b,stroke-width:2px
    style AUDITOR fill:#eceff1,stroke:#455a64,stroke-width:2px
    style CUSTOMER fill:#e0f2f1,stroke:#00695c,stroke-width:2px
    style SERVICE fill:#f5f5f5,stroke:#757575,stroke-width:2px
```

---

## 14. Database Encryption Architecture (RDS KMS + App-Level Envelope)

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'primaryColor': '#1e3a8a', 'fontSize': '10px'}}}%%
graph TB
    subgraph RDS ["AWS RDS MySQL 8.0"]
        STORAGE[Storage Layer<br/>Tables, Indexes, Logs, Backups]
        
        subgraph SCHEMAS ["Service Schemas"]
            AUTH_SCHEMA[citicore_authdb]
            KYC_SCHEMA[citicore_kycdb]
            ACC_SCHEMA[citicore_account]
            TXN_SCHEMA[citicore_transactiondb]
            USER_SCHEMA[citicore_userdb]
        end
    end
    
    subgraph LAYER1 ["Layer 1: Transparent Storage Encryption"]
        KMS1[AWS KMS<br/>Customer-Managed Key<br/>citicore-rds-encryption-key]
        RDS_ENC[RDS Storage Encryption<br/>AES-256<br/>Covers: Data, Backups, Snapshots,<br/>Read Replicas, Redo Logs]
    end
    
    subgraph LAYER2 ["Layer 2: Application-Level Envelope Encryption"]
        KMS2[AWS KMS<br/>Field Encryption Key<br/>citicore-field-encryption-key]
        APP_ENC[FieldEncryptionService<br/>AES-256-GCM<br/>Envelope Encryption]
        
        subgraph ENCRYPTED_FIELDS ["Selective Field Encryption"]
            PAN[pan_number<br/>AES-256-GCM]
            AADHAAR[aadhaar_number<br/>AES-256-GCM]
            PASSPORT[passport_number<br/>AES-256-GCM]
            ACCOUNT[account_number<br/>AES-256-GCM]
            NOMINEE[nominee_name<br/>AES-256-GCM]
        end
    end
    
    subgraph SEARCH ["Search Support"]
        HASH[SHA-256 Hash Columns<br/>pan_hash, aadhaar_hash<br/>For Exact-Match Lookups]
    end
    
    KMS1 -.->|Encrypt/Decrypt| RDS_ENC
    RDS_ENC -.->|Transparent| STORAGE
    RDS_ENC -.->|Transparent| SCHEMAS
    
    KMS2 -.->|GenerateDataKey/Decrypt| APP_ENC
    APP_ENC -.->|Encrypt/Decrypt| ENCRYPTED_FIELDS
    APP_ENC -.->|Hash for Index| HASH
    
    style KMS1 fill:#fff3e0,stroke:#ef6c00,stroke-width:2px
    style KMS2 fill:#fff3e0,stroke:#ef6c00,stroke-width:2px
    style RDS_ENC fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px
    style APP_ENC fill:#e3f2fd,stroke:#1565c0,stroke-width:2px
```

---

## 15. Service Discovery Architecture

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'primaryColor': '#1e3a8a', 'fontSize': '10px'}}}%%
graph TB
    subgraph PROD ["Production (AWS EKS)"]
        K8S_DNS[Kubernetes DNS (CoreDNS)<br/>Service Discovery]
        
        subgraph SERVICES ["Kubernetes Services"]
            AUTH_SVC[auth-service<br/>ClusterIP / Headless]
            USER_SVC[user-service<br/>ClusterIP / Headless]
            KYC_SVC[kyc-workflow-service<br/>ClusterIP / Headless]
            ACC_SVC[account-service<br/>ClusterIP / Headless]
            TXN_SVC[transaction-service<br/>ClusterIP / Headless]
        end
        
        COREDNS[CoreDNS<br/>Cluster Domain:<br/>svc.cluster.local]
        ENDPOINTS[EndpointSlices<br/>Pod IP Tracking]
    end
    
    subgraph LOCAL ["Local Development (Docker Compose)"]
        EUREKA[Eureka Server<br/>Port 8761]
        
        subgraph LOCAL_SERVICES ["Services with Eureka Client"]
            L_AUTH[auth-service]
            L_USER[user-service]
            L_KYC[kyc-workflow-service]
            L_ACC[account-service]
            L_TXN[transaction-service]
        end
    end
    
    subgraph RESOLUTION ["Name Resolution"]
        PROD_DNS[auth-service.citicore-prod.svc.cluster.local<br/>→ Pod IPs (headless) / ClusterIP (LB)]
        LOCAL_DNS[Eureka Registry<br/>HTTP Heartbeat]
    end
    
    PROD_DNS -.-> K8S_DNS
    K8S_DNS --> COREDNS
    COREDNS --> ENDPOINTS
    ENDPOINTS --> SERVICES
    
    LOCAL_DNS -.-> EUREKA
    EUREKA --> LOCAL_SERVICES
    
    style K8S_DNS fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px
    style EUREKA fill:#fff3e0,stroke:#ef6c00,stroke-width:2px
    style COREDNS fill:#e3f2fd,stroke:#1565c0,stroke-width:2px
```

---

## 16. KYC Document Malware Scanning Flow

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'primaryColor': '#1e3a8a', 'fontSize': '10px'}}}%%
sequenceDiagram
    participant Customer
    participant GW as API Gateway
    participant USER as User Service
    participant S3_Q as S3 Quarantine Bucket
    participant DB as KYC Document Metadata
    participant KAFKA as Kafka
    participant SCANNER as Scanner Service (S3 Object Lambda + ClamAV)
    participant S3_C as S3 Clean Bucket
    participant KYC as KYC Workflow Service
    
    Customer->>GW: POST /users/kyc/upload {file, documentType}
    GW->>USER: Forward (authenticated)
    USER->>USER: Validate file type, size, document type
    USER->>S3_Q: Upload to Quarantine Bucket (SSE-KMS)
    S3_Q-->>USER: S3 Key + Version ID
    USER->>DB: Save KycDocument (status=QUARANTINE)
    USER->>KAFKA: Publish kyc-document-topic
    
    KAFKA->>SCANNER: Trigger malware scan (S3 Event)
    KAFKA->>KYC: Consume document event
    KYC->>KYC: Link document to case (if exists)
    
    SCANNER->>S3_Q: Download object from Quarantine
    SCANNER->>SCANNER: Stream to ClamAV daemon
    SCANNER->>SCANNER: Parse result (CLEAN/INFECTED/ERROR)
    
    SCANNER-->>USER: ScanResult via Kafka (document-scan-result)
    
    alt INFECTED or ERROR
        USER->>S3_Q: Delete object from Quarantine
        USER->>DB: Update status=FAILED
        USER->>KAFKA: Publish document-scan-failed
        GW-->>Customer: 400 Document rejected
    else CLEAN
        USER->>S3_Q: Copy to Clean Bucket
        USER->>S3_Q: Delete from Quarantine
        USER->>DB: Update status=VERIFIED, clean bucket/key
        USER->>KAFKA: Publish document-verified
        GW-->>Customer: 200 Document accepted
        KYC->>KYC: Document available for review
    end
    
    Note over SCANNER: Fail-Closed:\nAny scanner error → ERROR → Document rejected
```

---

## 17. KYC Vendor Integration Architecture

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'primaryColor': '#1e3a8a', 'fontSize': '10px'}}}%%
graph TB
    subgraph KYC_WORKFLOW ["KYC Workflow Service"]
        KYC_CORE[KYC Core Logic\nMaker-Checker Workflow]
        PROVIDER_IF[KycVerificationProvider Interface]
    end
    
    subgraph PROVIDERS ["Verification Providers (Spring Profiles)"]
        MOCK[MockKycProvider<br/>Profile: mock<br/>Deterministic, Testable]
        JUMIO[JumioKycAdapter<br/>Profile: jumio<br/>Netverify, BAM]
        ONFIDO[OnfidoKycAdapter<br/>Profile: onfido<br/>Document + Face]
        HYPERVERGE[HyperVergeKycAdapter<br/>Profile: hyperverge<br/>India-Optimized]
    end
    
    subgraph INDIA_DOCS ["India Market Documents"]
        PAN[PAN Card<br/>OCR + Checksum + NSDL]
        AADHAAR[Aadhaar Card<br/>OCR + Verhoeff + UIDAI]
        SELFIE[Selfie<br/>Face Match + Liveness]
        ADDRESS[Utility Bill<br/>OCR + Date Validation]
    end
    
    subgraph WORKFLOW ["Maker-Checker Flow"]
        OFFICER[KYC Officer\nReviews Vendor Result]
        MANAGER[KYC Manager\nFinal Decision]
        SOD[SoD: Officer ≠ Manager]
    end
    
    KYC_CORE --> PROVIDER_IF
    PROVIDER_IF --> MOCK
    PROVIDER_IF -.-> JUMIO
    PROVIDER_IF -.-> ONFIDO
    PROVIDER_IF -.-> HYPERVERGE
    
    MOCK --> INDIA_DOCS
    JUMIO -.-> INDIA_DOCS
    ONFIDO -.-> INDIA_DOCS
    HYPERVERGE -.-> INDIA_DOCS
    
    PROVIDER_IF --> OFFICER
    OFFICER --> MANAGER
    SOD -.-> OFFICER
    SOD -.-> MANAGER
    
    style MOCK fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px
    style JUMIO fill:#fff3e0,stroke:#ef6c00,stroke-width:2px
    style ONFIDO fill:#fff3e0,stroke:#ef6c00,stroke-width:2px
    style HYPERVERGE fill:#fff3e0,stroke:#ef6c00,stroke-width:2px
    style SOD fill:#ffebee,stroke:#c62828,stroke-width:2px
```

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-11-17 | Architecture Team | Initial diagrams |
| 2.0 | 2026-11-18 | Architecture Team | **Major Update**: Added RBAC architecture, KYC maker-checker workflow, KYC state machine, Officer/Manager interaction, Updated service topology, Updated database topology, Updated event flows, Added authorization model, Added onboarding workflow |
| 3.0 | 2026-11-18 | Architecture Team | **All 8 ADRs Approved**: Updated service topology for AWS EKS, RDS single-instance multi-schema, Hybrid saga orchestration, Database encryption layers, Service discovery (K8s DNS vs Eureka), Malware scanning flow, KYC vendor integration, Role hierarchy |

---

**END OF DOCUMENT**