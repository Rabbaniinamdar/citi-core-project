# CitiCore Banking Platform - Database & Data Consistency Audit

## Executive Summary

This audit evaluates the database design, data integrity, and consistency mechanisms across the CitiCore microservices. The platform uses **MySQL 8.0** with separate databases per service, but lacks critical financial data integrity controls.

**Overall Rating: MEDIUM-HIGH RISK** - Significant gaps in financial ledger integrity, constraints, and audit capabilities.

---

## 1. Database Topology

```
┌─────────────────────────────────────────────────────────────────────┐
│                         MYSQL 8.0 INSTANCE                          │
├─────────────┬─────────────┬─────────────┬─────────────────────────┤
│ citicore_   │ citicore_   │ citicore_   │ citicore_               │
│ authdb      │ account     │ transactiondb│ userdb                 │
├─────────────┼─────────────┼─────────────┼─────────────────────────┤
│ users       │ accounts    │ transactions│ user_profiles          │
│ roles       │ account_    │ outbox_     │ kyc_documents          │
│ user_roles  │ statements  │ events      │                        │
│ refresh_    │ account_    │ idempotency_│                        │
│ tokens      │ outbox      │ keys        │                        │
│             │ sequences   │             │                        │
└─────────────┴─────────────┴─────────────┴─────────────────────────┘
```

**Account Service:** Primary/Replica setup (same host, different connection pools)

---

## 2. Schema Analysis by Service

### 2.1 Auth Service (citicore_authdb)

```sql
-- users table
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,  -- BCrypt hash
    email_verified BOOLEAN DEFAULT FALSE,
    status ENUM('PENDING','ACTIVE','SUSPENDED','CLOSED') DEFAULT 'PENDING',
    verification_code VARCHAR(6),
    verification_code_expires_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- roles table
CREATE TABLE roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL  -- ROLE_CUSTOMER, ROLE_ADMIN
);

-- user_roles (many-to-many)
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (role_id) REFERENCES roles(id)
);

-- refresh_tokens
CREATE TABLE refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(255) UNIQUE NOT NULL,
    user_id BIGINT NOT NULL,
    revoked BOOLEAN DEFAULT FALSE,
    expiry_date DATETIME NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_refresh_token_user (user_id),
    INDEX idx_refresh_token_expiry (expiry_date)
);
```

**Issues Found:**
| Issue | Severity | Description |
|-------|----------|-------------|
| No `created_at`/`updated_at` on users | Medium | Missing audit timestamps |
| `verification_code` only 6 chars | High | Weak entropy (see SEC-002) |
| No index on `email_verified` | Low | Query performance |
| Refresh token no `created_at` | Medium | Cannot audit token age |

---

### 2.2 Account Service (citicore_account) - PRIMARY/REPLICA

```sql
-- accounts table
CREATE TABLE accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_number VARCHAR(20) UNIQUE NOT NULL,  -- CITI + 12 digits
    auth_user_id BIGINT NOT NULL,
    account_type ENUM('SAVINGS','CURRENT') NOT NULL,
    balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    status ENUM('ACTIVE','INACTIVE','SUSPENDED','CLOSED') DEFAULT 'ACTIVE',
    nominee_name VARCHAR(255),
    is_default BOOLEAN DEFAULT FALSE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_accounts_user (auth_user_id),
    INDEX idx_accounts_number (account_number)
);

-- account_statements
CREATE TABLE account_statements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_number VARCHAR(20) NOT NULL,
    transaction_type ENUM('DEBIT','CREDIT') NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    balance_after_txn DECIMAL(15,2) NOT NULL,
    txn_ref VARCHAR(100) UNIQUE NOT NULL,  -- PROBLEM: global unique
    description VARCHAR(500),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_statement_account (account_number),
    INDEX idx_statement_date (created_at)
);

-- account_outbox
CREATE TABLE account_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(36) UNIQUE NOT NULL,  -- UUID
    account_number VARCHAR(20) NOT NULL,   -- Partition key
    topic VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status ENUM('PENDING','SENT','FAILED') DEFAULT 'PENDING',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_outbox_status (status),
    INDEX idx_outbox_account (account_number)
);

-- account_sequences
CREATE TABLE account_sequences (
    id BIGINT PRIMARY KEY DEFAULT 1,
    next_val BIGINT NOT NULL DEFAULT 1
);
```

**Critical Issues Found:**

| Issue | Severity | Description |
|-------|----------|-------------|
| **Single balance field** | **CRITICAL** | No available vs ledger balance separation |
| **No double-entry ledger** | **CRITICAL** | AccountStatement is single-entry, not GL |
| `txn_ref` unique globally | High | Collision across accounts (BUG-019) |
| No foreign key on `auth_user_id` | High | Referential integrity to auth-service missing |
| No check constraint `balance >= 0` | High | Negative balances possible via bugs |
| No check constraint `balance >= min_balance` | Medium | Business rule not enforced at DB level |
| `account_number` not FK in statements | Medium | Orphan statements possible |
| Primary/Replica same host | High | No true HA - single point of failure |

**Missing Indexes:**
```sql
-- For validateTransfer (frequent)
CREATE INDEX idx_accounts_user_type ON accounts(auth_user_id, account_type);

-- For statement queries by user (via account)
CREATE INDEX idx_statements_user_date ON account_statements(
    (SELECT auth_user_id FROM accounts WHERE accounts.account_number = account_statements.account_number),
    created_at
);

-- For outbox polling (critical path)
CREATE INDEX idx_outbox_status_created ON account_outbox(status, created_at);
```

---

### 2.3 Transaction Service (citicore_transactiondb)

```sql
-- transactions
CREATE TABLE transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    txn_ref VARCHAR(100) UNIQUE NOT NULL,
    auth_user_id BIGINT NOT NULL,
    from_account VARCHAR(20) NOT NULL,
    to_account VARCHAR(20) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    type ENUM('TRANSFER','DEPOSIT','WITHDRAWAL') NOT NULL,
    status ENUM('INITIATED','DEBIT_SUCCESS','CREDIT_SUCCESS','FAILED','REVERSED') NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_txn_user (auth_user_id),
    INDEX idx_txn_status (status),
    INDEX idx_txn_from (from_account),
    INDEX idx_txn_to (to_account)
);

-- outbox_events
CREATE TABLE outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(36) UNIQUE NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,  -- txnRef
    topic VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status ENUM('PENDING','SENT','FAILED') DEFAULT 'PENDING',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_outbox_status (status),
    INDEX idx_outbox_aggregate (aggregate_id)
);

-- idempotency_keys
CREATE TABLE idempotency_keys (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    idempotency_key VARCHAR(100) UNIQUE NOT NULL,  -- PROBLEM: not user-scoped
    txn_ref VARCHAR(100) NOT NULL,
    status VARCHAR(20) DEFAULT 'INITIATED',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

**Critical Issues Found:**

| Issue | Severity | Description |
|-------|----------|-------------|
| **Idempotency key not user-scoped** | **CRITICAL** | Cross-user collision (BUG-008) |
| No FK on `auth_user_id` | High | Referential integrity missing |
| No FK on `from_account`/`to_account` | High | No referential integrity to accounts |
| `type` enum doesn't match account-service | Medium | Inconsistent (TRANSFER vs DEBIT/CREDIT) |
| No `updated_at` | Medium | Cannot track status changes |
| No index on `(auth_user_id, created_at)` | Medium | History query performance |

---

### 2.4 User Service (citicore_userdb)

```sql
-- user_profiles
CREATE TABLE user_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    auth_user_id BIGINT UNIQUE NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    phone_number VARCHAR(20) UNIQUE,
    date_of_birth DATE,
    address VARCHAR(500),
    city VARCHAR(100),
    state VARCHAR(100),
    pincode VARCHAR(10),
    kyc_status ENUM('PENDING','UNDER_REVIEW','APPROVED','REJECTED') DEFAULT 'PENDING',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- kyc_documents
CREATE TABLE kyc_documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    auth_user_id BIGINT NOT NULL,
    document_type VARCHAR(50) NOT NULL,  -- PAN, AADHAAR, PASSPORT
    s3_key VARCHAR(500) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100),
    file_size BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_doc_type (auth_user_id, document_type),
    FOREIGN KEY (auth_user_id) REFERENCES user_profiles(auth_user_id)
);
```

**Issues Found:**

| Issue | Severity | Description |
|-------|----------|-------------|
| No FK to auth-service users | High | `auth_user_id` not enforced |
| `phone_number` unique but nullable | Medium | Multiple NULLs allowed in unique index |
| No document verification status | Medium | Cannot track OCR/verification result |
| No index on `kyc_status` | Low | Admin query performance |

---

## 3. Financial Data Integrity Assessment

### 3.1 Monetary Precision

| Table | Column | Type | Assessment |
|-------|--------|------|------------|
| accounts | balance | DECIMAL(15,2) | ✅ Adequate (999 trillion max) |
| account_statements | amount | DECIMAL(15,2) | ✅ Adequate |
| account_statements | balance_after_txn | DECIMAL(15,2) | ✅ Adequate |
| transactions | amount | DECIMAL(15,2) | ✅ Adequate |

**Verdict:** Precision adequate for INR. For multi-currency, need DECIMAL(19,4) + currency code.

### 3.2 Double-Entry Bookkeeping - **MAJOR GAP**

**Current:** Single-entry (AccountStatement records one side per row)
```java
// AccountService.debit()
statementRepository.save(new AccountStatement.Builder()
    .txnRef(txnRef)
    .accountNumber(accNo)
    .transactionType(TransactionType.DEBIT)  // Only DEBIT recorded
    .amount(amount)
    .balanceAfterTxn(newBalance)
    .build());

// AccountService.credit() - SEPARATE TRANSACTION
statementRepository.save(new AccountStatement.Builder()
    .txnRef(txnRef + "_CREDIT")  // Different txnRef!
    .accountNumber(toAccount)
    .transactionType(TransactionType.CREDIT)  // Only CREDIT recorded
    .amount(amount)
    .balanceAfterTxn(newBalance)
    .build());
```

**Required:** General Ledger with balanced journal entries
```sql
CREATE TABLE ledger_accounts (
    code VARCHAR(20) PRIMARY KEY,  -- 1000-CASH, 2000-DEPOSITS
    name VARCHAR(100) NOT NULL,
    type ENUM('ASSET','LIABILITY','EQUITY','INCOME','EXPENSE') NOT NULL,
    currency CHAR(3) DEFAULT 'INR',
    is_control_account BOOLEAN DEFAULT FALSE
);

CREATE TABLE journal_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    txn_ref VARCHAR(100) NOT NULL,  -- Business transaction
    entry_date DATETIME NOT NULL,
    description VARCHAR(500),
    status ENUM('POSTED','REVERSED') DEFAULT 'POSTED',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_txn_ref (txn_ref)
);

CREATE TABLE journal_lines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    journal_id BIGINT NOT NULL,
    account_code VARCHAR(20) NOT NULL,
    debit DECIMAL(19,4) NOT NULL DEFAULT 0,  -- >= 0
    credit DECIMAL(19,4) NOT NULL DEFAULT 0,  -- >= 0
    FOREIGN KEY (journal_id) REFERENCES journal_entries(id),
    FOREIGN KEY (account_code) REFERENCES ledger_accounts(code),
    CHECK ((debit > 0 AND credit = 0) OR (debit = 0 AND credit > 0)),  -- Exactly one side
    CHECK (debit >= 0 AND credit >= 0)
);

-- Critical: Journal must balance
CREATE TRIGGER trg_journal_balance
BEFORE INSERT ON journal_entries
FOR EACH ROW
BEGIN
    DECLARE total_debit DECIMAL(19,4);
    DECLARE total_credit DECIMAL(19,4);
    SELECT SUM(debit), SUM(credit) INTO total_debit, total_credit
    FROM journal_lines WHERE journal_id = NEW.id;
    IF total_debit != total_credit THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Journal entry must balance';
    END IF;
END;
```

### 3.3 Account Balance Consistency

**Current Risk:** No database-level verification that `account.balance` matches sum of statements.

**Required:** Periodic reconciliation job
```sql
-- Daily reconciliation query
SELECT a.account_number, 
       a.balance as account_balance,
       COALESCE(SUM(CASE WHEN s.transaction_type = 'CREDIT' THEN s.amount ELSE -s.amount END), 0) as computed_balance
FROM accounts a
LEFT JOIN account_statements s ON a.account_number = s.account_number
GROUP BY a.account_number, a.balance
HAVING a.balance != computed_balance;
```

---

## 4. Transaction Isolation & Concurrency

### 4.1 Current Isolation Levels

| Operation | Method | Isolation | Lock Type |
|-----------|--------|-----------|-----------|
| Debit | `AccountService.debit()` | Default (READ_COMMITTED) | PESSIMISTIC_WRITE (SELECT FOR UPDATE) |
| Credit | `AccountService.credit()` | Default (READ_COMMITTED) | PESSIMISTIC_WRITE (SELECT FOR UPDATE) |
| Transfer Initiate | `TransactionService.transfer()` | Default (READ_COMMITTED) | Row lock on transaction + outbox |
| Balance Read | `AccountService.getBalance()` | READ_COMMITTED | None (Redis cache) |
| Statement Read | `AccountService.getStatement()` | READ_COMMITTED | None (Replica) |

### 4.2 Concurrency Issues

| Scenario | Current Behavior | Risk |
|----------|------------------|------|
| Two simultaneous debits | Serialized by SELECT FOR UPDATE | ✅ Safe |
| Debit + Credit same account | Serialized by SELECT FOR UPDATE | ✅ Safe |
| Balance read during debit | Reads replica (stale) or cache | ⚠️ Stale read possible |
| Transfer validation | Non-locking read (Feign) | ⚠️ TOCTOU - balance may change |
| Two transfers same sender | Saga: first wins, second fails at debit | ✅ Safe (idempotency) |

**Critical Gap:** `validateTransfer` uses **non-locking read** (TOCTOU vulnerability)
```java
// TransactionService.transfer() line 113
accountFeignClient.validateTransfer(req.getFromAccount(), req.getAmount());
// ... later ...
// DebitConsumer does locking debit - but balance may have changed!
```

**Fix:** Remove pre-validation OR make it locking (but adds latency)

---

## 5. Referential Integrity Analysis

### 5.1 Missing Foreign Keys

| Child Table | Column | Parent Table | Status |
|-------------|--------|--------------|--------|
| accounts | auth_user_id | auth.users | ❌ Missing (cross-db) |
| account_statements | account_number | accounts.account_number | ❌ Missing |
| transactions | auth_user_id | auth.users | ❌ Missing (cross-db) |
| transactions | from_account | accounts.account_number | ❌ Missing (cross-db) |
| transactions | to_account | accounts.account_number | ❌ Missing (cross-db) |
| user_profiles | auth_user_id | auth.users | ❌ Missing (cross-db) |
| kyc_documents | auth_user_id | user_profiles.auth_user_id | ✅ Present |
| refresh_tokens | user_id | users.id | ✅ Present |
| user_roles | user_id | users.id | ✅ Present |
| user_roles | role_id | roles.id | ✅ Present |

**Reason:** Cross-database foreign keys not supported in MySQL. Requires application-level integrity or single database.

**Recommendation:** 
1. **Option A:** Single database with schemas (preferred for integrity)
2. **Option B:** Application-level validation + reconciliation jobs
3. **Option C:** Use database links (not recommended)

---

## 6. Indexing Strategy Review

### 6.1 Current Indexes

| Table | Index | Columns | Type | Assessment |
|-------|-------|---------|------|------------|
| accounts | PRIMARY | id | Clustered | ✅ |
| accounts | idx_accounts_user | auth_user_id | Secondary | ✅ |
| accounts | idx_accounts_number | account_number | Unique | ✅ |
| account_statements | PRIMARY | id | Clustered | ✅ |
| account_statements | idx_statement_account | account_number | Secondary | ✅ |
| account_statements | idx_statement_date | created_at | Secondary | ✅ |
| account_statements | uk_txn_ref | txn_ref | Unique | ⚠️ Global unique problematic |
| account_outbox | PRIMARY | id | Clustered | ✅ |
| account_outbox | uk_event_id | event_id | Unique | ✅ |
| account_outbox | idx_outbox_status | status | Secondary | ⚠️ Low cardinality |
| account_outbox | idx_outbox_account | account_number | Secondary | ✅ |
| transactions | PRIMARY | id | Clustered | ✅ |
| transactions | uk_txn_ref | txn_ref | Unique | ✅ |
| transactions | idx_txn_user | auth_user_id | Secondary | ✅ |
| transactions | idx_txn_status | status | Secondary | ⚠️ Low cardinality |
| transactions | idx_txn_from | from_account | Secondary | ✅ |
| transactions | idx_txn_to | to_account | Secondary | ✅ |
| outbox_events | PRIMARY | id | Clustered | ✅ |
| outbox_events | uk_event_id | event_id | Unique | ✅ |
| outbox_events | idx_outbox_status | status | Secondary | ⚠️ Low cardinality |
| outbox_events | idx_outbox_aggregate | aggregate_id | Secondary | ✅ |
| idempotency_keys | PRIMARY | id | Clustered | ✅ |
| idempotency_keys | uk_idempotency_key | idempotency_key | Unique | ⚠️ Not user-scoped |
| user_profiles | PRIMARY | id | Clustered | ✅ |
| user_profiles | uk_auth_user_id | auth_user_id | Unique | ✅ |
| user_profiles | uk_email | email | Unique | ✅ |
| user_profiles | uk_phone | phone_number | Unique | ⚠️ Nullable unique |
| kyc_documents | PRIMARY | id | Clustered | ✅ |
| kyc_documents | uk_user_doc_type | (auth_user_id, document_type) | Unique | ✅ |

### 6.2 Missing Critical Indexes

```sql
-- Account Service
CREATE INDEX idx_accounts_user_type_status ON accounts(auth_user_id, account_type, status);
CREATE INDEX idx_outbox_status_created ON account_outbox(status, created_at);  -- Polling optimization
CREATE INDEX idx_statements_account_date ON account_statements(account_number, created_at DESC);

-- Transaction Service  
CREATE INDEX idx_txn_user_created ON transactions(auth_user_id, created_at DESC);  -- History pagination
CREATE INDEX idx_txn_from_created ON transactions(from_account, created_at);
CREATE INDEX idx_outbox_status_created ON outbox_events(status, created_at);
CREATE UNIQUE INDEX uk_idempotency_user_key ON idempotency_keys(auth_user_id, idempotency_key);  -- Fix BUG-008

-- Auth Service
CREATE INDEX idx_refresh_token_user_expiry ON refresh_tokens(user_id, expiry_date);
CREATE INDEX idx_users_status ON users(status);
```

---

## 7. Database Migration & Versioning

### 7.1 Current State
```yaml
# All services use:
spring.jpa.hibernate.ddl-auto: update
```
**Problems:**
- No version control for schema changes
- No rollback capability
- Production schema drift guaranteed
- Data loss risk on column rename/type change
- No audit trail of schema changes

### 7.2 Required: Flyway Migration Strategy

```
src/main/resources/db/migration/
├── V1__initial_schema.sql
├── V2__add_kyc_tables.sql
├── V3__add_outbox_tables.sql
├── V4__add_ledger_tables.sql
├── V5__fix_idempotency_user_scope.sql
├── V6__add_balance_constraints.sql
└── V7__add_reconciliation_views.sql
```

**Configuration:**
```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    validate-on-migrate: true
  jpa:
    hibernate:
      ddl-auto: validate  # NOT update!
```

---

## 8. Connection Pool Configuration

### 8.1 Current State
**No explicit HikariCP configuration** - using Spring Boot defaults:
- `maximum-pool-size: 10`
- `minimum-idle: 10`
- `connection-timeout: 30s`
- `idle-timeout: 10m`
- `max-lifetime: 30m`

### 8.2 Recommended Per-Service Tuning

| Service | Max Pool | Min Idle | Rationale |
|---------|----------|----------|-----------|
| Auth | 20 | 5 | Bursty login traffic |
| User | 15 | 5 | Moderate profile/KYC traffic |
| Account | 50 | 20 | High throughput (balance, statements, transfers) |
| Transaction | 30 | 10 | Saga orchestration + polling |
| Notification | 10 | 2 | Low DB usage (mostly Kafka consumer) |

**Account Service (Primary/Replica):**
```yaml
spring:
  datasource:
    primary:
      hikari:
        maximum-pool-size: 30
        minimum-idle: 10
        connection-timeout: 5000
    replica:
      hikari:
        maximum-pool-size: 20
        minimum-idle: 5
        connection-timeout: 5000
```

---

## 9. Data Retention & Archival

### 9.1 Current State
**No retention policy** - all data kept forever.

### 9.2 Required Retention Schedule (Regulatory)

| Data Type | Retention | Archival Strategy |
|-----------|-----------|-------------------|
| Financial Transactions | 10 years (RBI) | Partition by year, move to cold storage |
| Account Statements | 10 years | Compressed Parquet in S3 |
| KYC Documents | 10 years post-closure | S3 Glacier |
| Audit Logs | 7 years (SOX) | Immutable append-only store |
| Login/Access Logs | 2 years | Elasticsearch ILM |
| OTP/Verification Codes | 24 hours | Auto-purge job |
| Refresh Tokens | 7 days (expiry) | Auto-cleanup on expiry |
| Kafka Events | 7 days (retention.ms) | Compact topics for key lookup |

---

## 10. Backup & Disaster Recovery

### 10.1 Current State
**No backup strategy** - only Docker volume persistence.

### 10.2 Required Backup Plan

| Component | Frequency | Retention | RPO | RTO |
|-----------|-----------|-----------|-----|-----|
| MySQL (All DBs) | Daily full + 15-min binlog | 30 days | 15 min | 2 hours |
| Redis | RDB snapshot 6-hourly | 7 days | 6 hours | 30 min |
| Kafka | MirrorMaker to DR cluster | 7 days | Near-zero | 1 hour |
| S3 (KYC) | Cross-region replication | Infinite | Near-zero | Immediate |
| Config Repo | Git backup | Infinite | N/A | N/A |

**Recovery Testing:** Quarterly restore drills mandatory.

---

## 11. Summary of Required Changes

### P0 - Critical (Immediate)
1. **Implement Double-Entry General Ledger** - New tables, triggers, reconciliation
2. **Fix Idempotency Key User Scoping** - Add `auth_user_id`, unique constraint
3. **Add Balance Check Constraints** - `balance >= 0`, `balance >= min_balance`
4. **Fix `txn_ref` Unique Constraint** - Change to `(account_number, txn_ref)`
5. **Add Cross-Service Referential Integrity** - Application-level or single DB

### P1 - High (Sprint 1-2)
6. **Configure Flyway Migrations** - Disable `ddl-auto: update`
7. **Add Missing Indexes** - Especially outbox polling, history queries
8. **Tune Connection Pools** - Per-service HikariCP config
9. **Add `updated_at` to Transactions** - Track status changes
10. **Implement Reconciliation Jobs** - Daily balance verification

### P2 - Medium (Sprint 3-4)
11. **Partition Large Tables** - `account_statements`, `transactions` by month
12. **Add Database-Level Audit Triggers** - Immutable change log
13. **Implement Data Archival** - Yearly partition move to cold storage
14. **Configure Automated Backups** - With point-in-time recovery
15. **Add Read Replica (True HA)** - Separate host/AZ

### P3 - Low (Quarter)
16. **Multi-Currency Support** - `currency` column, exchange rates
17. **Column-Level Encryption** - PII fields
18. **Advanced Monitoring** - Query performance, deadlock alerts

---

## 12. Schema Migration Scripts (Priority Fixes)

### Fix 1: Idempotency Key User Scoping
```sql
-- V5__fix_idempotency_user_scope.sql
ALTER TABLE idempotency_keys 
ADD COLUMN auth_user_id BIGINT NOT NULL AFTER id;

-- Populate from transactions (one-time)
UPDATE idempotency_keys ik
JOIN transactions t ON ik.txn_ref = t.txn_ref
SET ik.auth_user_id = t.auth_user_id;

ALTER TABLE idempotency_keys 
DROP INDEX uk_idempotency_key,
ADD UNIQUE INDEX uk_idempotency_user_key (auth_user_id, idempotency_key);
```

### Fix 2: Statement txn_ref Per-Account Unique
```sql
-- V6__fix_statement_txn_ref_unique.sql
ALTER TABLE account_statements 
DROP INDEX uk_txn_ref,
ADD UNIQUE INDEX uk_account_txn_ref (account_number, txn_ref);
```

### Fix 3: Balance Constraints
```sql
-- V7__add_balance_constraints.sql
ALTER TABLE accounts 
ADD CONSTRAINT chk_balance_non_negative CHECK (balance >= 0),
ADD CONSTRAINT chk_savings_min_balance CHECK (
    account_type != 'SAVINGS' OR balance >= 1000
),
ADD CONSTRAINT chk_current_min_balance CHECK (
    account_type != 'CURRENT' OR balance >= 5000
);
```

### Fix 4: General Ledger Tables
```sql
-- V8__add_general_ledger.sql
CREATE TABLE ledger_accounts (
    code VARCHAR(20) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type ENUM('ASSET','LIABILITY','EQUITY','INCOME','EXPENSE') NOT NULL,
    currency CHAR(3) DEFAULT 'INR',
    is_control_account BOOLEAN DEFAULT FALSE,
    parent_code VARCHAR(20),
    FOREIGN KEY (parent_code) REFERENCES ledger_accounts(code)
);

CREATE TABLE journal_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    txn_ref VARCHAR(100) NOT NULL,
    entry_date DATETIME NOT NULL,
    description VARCHAR(500),
    status ENUM('POSTED','REVERSED') DEFAULT 'POSTED',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_txn_ref (txn_ref)
);

CREATE TABLE journal_lines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    journal_id BIGINT NOT NULL,
    account_code VARCHAR(20) NOT NULL,
    debit DECIMAL(19,4) NOT NULL DEFAULT 0,
    credit DECIMAL(19,4) NOT NULL DEFAULT 0,
    FOREIGN KEY (journal_id) REFERENCES journal_entries(id) ON DELETE CASCADE,
    FOREIGN KEY (account_code) REFERENCES ledger_accounts(code),
    CHECK ((debit > 0 AND credit = 0) OR (debit = 0 AND credit > 0)),
    CHECK (debit >= 0 AND credit >= 0)
);

-- Trigger to enforce balanced journal
DELIMITER //
CREATE TRIGGER trg_journal_balance
BEFORE INSERT ON journal_entries
FOR EACH ROW
BEGIN
    DECLARE v_debit DECIMAL(19,4);
    DECLARE v_credit DECIMAL(19,4);
    SELECT COALESCE(SUM(debit),0), COALESCE(SUM(credit),0) 
    INTO v_debit, v_credit
    FROM journal_lines WHERE journal_id = NEW.id;
    IF v_debit != v_credit THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Journal entry must balance: debits != credits';
    END IF;
END//
DELIMITER ;
```

---

## 13. Monitoring Queries

```sql
-- Daily: Check for unbalanced journals (should return 0 rows)
SELECT je.txn_ref, 
       SUM(jl.debit) as total_debit, 
       SUM(jl.credit) as total_credit
FROM journal_entries je
JOIN journal_lines jl ON je.id = jl.journal_id
GROUP BY je.txn_ref
HAVING total_debit != total_credit;

-- Daily: Account balance vs statement reconciliation
SELECT a.account_number, a.balance, 
       COALESCE(SUM(CASE WHEN s.transaction_type='CREDIT' THEN s.amount ELSE -s.amount END),0) as computed
FROM accounts a
LEFT JOIN account_statements s ON a.account_number = s.account_number
GROUP BY a.account_number, a.balance
HAVING a.balance != computed;

-- Hourly: Outbox lag
SELECT status, COUNT(*), MIN(created_at) as oldest
FROM account_outbox
GROUP BY status;

-- Hourly: Long-running transactions
SELECT * FROM information_schema.INNODB_TRX 
WHERE trx_started < DATE_SUB(NOW(), INTERVAL 1 MINUTE);
```