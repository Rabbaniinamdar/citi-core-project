-- ─────────────────────────────────────────────────────────────────────────────
-- CitiCore Account Service — MySQL Schema
-- Partitioned tables for account_statements and account_outbox
--
-- WHY PARTITIONING HERE:
--   account_statements grows at ~5M rows/day at scale.
--   Without partitioning: SELECT WHERE account_number = ?
--   scans the entire table → slow at 1B+ rows.
--
--   With RANGE partitioning by created_at (monthly):
--   MySQL's partition pruning only scans the relevant month partitions.
--   A query for "last 3 months" touches 3 partitions instead of all 5 years.
--
-- ─────────────────────────────────────────────────────────────────────────────
-- PARTITION TYPE CHOSEN: RANGE COLUMNS(created_at)
--
-- We partition directly on the DATETIME column.
--
-- Each partition represents one calendar month:
--
--   p_2026_08 → created_at < '2026-09-01'
--   p_2026_09 → created_at < '2026-10-01'
--
-- RANGE COLUMNS keeps the partition boundary type compatible with
-- the DATETIME partition column.
--
-- IMPORTANT:
--   1. created_at must be included in every unique key / primary key.
--   2. Add new partitions monthly or automate partition maintenance.
--   3. Drop old partitions according to the data-retention policy.
-- ─────────────────────────────────────────────────────────────────────────────
-- ─────────────────────────────────────────────────────────────────────────────

-- ─── accounts (not partitioned — small table, one row per account) ───────────
CREATE TABLE IF NOT EXISTS accounts (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    account_number VARCHAR(20)  NOT NULL UNIQUE,
    auth_user_id   BIGINT       NOT NULL,
    account_type   VARCHAR(20)  NOT NULL,   -- SAVINGS / CURRENT
    balance        DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    is_default     BOOLEAN      NOT NULL DEFAULT FALSE,
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    nominee_name   VARCHAR(100),
    is_fd_auto_renew BOOLEAN    NOT NULL DEFAULT FALSE,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_accounts_auth_user_id (auth_user_id),
    INDEX idx_accounts_account_number (account_number)
) ENGINE=InnoDB;

-- ─── account_statements (PARTITIONED by created_at monthly) ──────────────────
-- This is the hottest table — every debit/credit/reversal = one row.
-- Partitioned by RANGE on UNIX_TIMESTAMP(created_at) monthly.
--
-- Partition pruning example:
--   SELECT * FROM account_statements
--   WHERE account_number = 'CITI000000000001'
--   AND created_at >= '2026-06-01' AND created_at < '2026-07-01'
--   → MySQL only scans p_2026_06, skips all other partitions ✅
CREATE TABLE IF NOT EXISTS account_statements
(
    id BIGINT NOT NULL AUTO_INCREMENT,

    txn_ref VARCHAR(100) NOT NULL,

    account_number VARCHAR(20) NOT NULL,

    transaction_type VARCHAR(10) NOT NULL,

    amount DECIMAL(15,2) NOT NULL,

    balance_after_txn DECIMAL(15,2) NOT NULL,

    description VARCHAR(255),

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id, created_at),

    UNIQUE KEY uk_txn_ref (txn_ref, created_at),

    INDEX idx_stmt_account_number (
        account_number,
        created_at
    ),

    INDEX idx_stmt_created_at (
        created_at
    )

)
ENGINE=InnoDB

PARTITION BY RANGE COLUMNS(created_at)
(
    PARTITION p_2025_01 VALUES LESS THAN ('2025-02-01'),
    PARTITION p_2025_02 VALUES LESS THAN ('2025-03-01'),
    PARTITION p_2025_03 VALUES LESS THAN ('2025-04-01'),
    PARTITION p_2025_04 VALUES LESS THAN ('2025-05-01'),
    PARTITION p_2025_05 VALUES LESS THAN ('2025-06-01'),
    PARTITION p_2025_06 VALUES LESS THAN ('2025-07-01'),
    PARTITION p_2025_07 VALUES LESS THAN ('2025-08-01'),
    PARTITION p_2025_08 VALUES LESS THAN ('2025-09-01'),
    PARTITION p_2025_09 VALUES LESS THAN ('2025-10-01'),
    PARTITION p_2025_10 VALUES LESS THAN ('2025-11-01'),
    PARTITION p_2025_11 VALUES LESS THAN ('2025-12-01'),
    PARTITION p_2025_12 VALUES LESS THAN ('2026-01-01'),

    PARTITION p_2026_01 VALUES LESS THAN ('2026-02-01'),
    PARTITION p_2026_02 VALUES LESS THAN ('2026-03-01'),
    PARTITION p_2026_03 VALUES LESS THAN ('2026-04-01'),
    PARTITION p_2026_04 VALUES LESS THAN ('2026-05-01'),
    PARTITION p_2026_05 VALUES LESS THAN ('2026-06-01'),
    PARTITION p_2026_06 VALUES LESS THAN ('2026-07-01'),
    PARTITION p_2026_07 VALUES LESS THAN ('2026-08-01'),
    PARTITION p_2026_08 VALUES LESS THAN ('2026-09-01'),
    PARTITION p_2026_09 VALUES LESS THAN ('2026-10-01'),
    PARTITION p_2026_10 VALUES LESS THAN ('2026-11-01'),
    PARTITION p_2026_11 VALUES LESS THAN ('2026-12-01'),
    PARTITION p_2026_12 VALUES LESS THAN ('2027-01-01'),

    PARTITION p_future VALUES LESS THAN (MAXVALUE)
);

-- ─── account_outbox (PARTITIONED by created_at monthly) ──────────────────────
-- Outbox events are short-lived (published within 5s → SENT/FAILED).
-- Partitioning here enables fast cleanup of old SENT records.
-- DROP PARTITION p_2026_01 removes a month of old records instantly
-- (much faster than DELETE WHERE created_at < '2026-02-01').
CREATE TABLE IF NOT EXISTS account_outbox (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    event_id       VARCHAR(100) NOT NULL,
    account_number VARCHAR(100) NOT NULL,
    topic          VARCHAR(200) NOT NULL,
    payload        TEXT         NOT NULL,
    status         VARCHAR(20)  NOT NULL,  -- PENDING / SENT / FAILED
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id, created_at),

    UNIQUE KEY uk_event_id (event_id, created_at),

    INDEX idx_outbox_status (status, created_at)

) ENGINE=InnoDB

PARTITION BY RANGE COLUMNS(created_at) (
    PARTITION p_2026_01 VALUES LESS THAN ('2026-02-01'),
    PARTITION p_2026_02 VALUES LESS THAN ('2026-03-01'),
    PARTITION p_2026_03 VALUES LESS THAN ('2026-04-01'),
    PARTITION p_2026_04 VALUES LESS THAN ('2026-05-01'),
    PARTITION p_2026_05 VALUES LESS THAN ('2026-06-01'),
    PARTITION p_2026_06 VALUES LESS THAN ('2026-07-01'),
    PARTITION p_2026_07 VALUES LESS THAN ('2026-08-01'),
    PARTITION p_2026_08 VALUES LESS THAN ('2026-09-01'),
    PARTITION p_2026_09 VALUES LESS THAN ('2026-10-01'),
    PARTITION p_2026_10 VALUES LESS THAN ('2026-11-01'),
    PARTITION p_2026_11 VALUES LESS THAN ('2026-12-01'),
    PARTITION p_2026_12 VALUES LESS THAN ('2027-01-01'),

    PARTITION p_future VALUES LESS THAN (MAXVALUE)
);

-- ─── dead_letter_events (not partitioned — small, admin-managed table) ────────
CREATE TABLE IF NOT EXISTS dead_letter_events (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    topic           VARCHAR(200) NOT NULL,
    partition_id    INT,
    offset_value    BIGINT,
    payload         TEXT,
    error_message   TEXT,
    exception_class VARCHAR(300),
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at     DATETIME,
    PRIMARY KEY (id),
    INDEX idx_dlq_status (status)
) ENGINE=InnoDB;


-- ─────────────────────────────────────────────────────────────────────────────
-- MONTHLY MAINTENANCE QUERIES
-- Run these at the start of each month to manage partitions
-- ─────────────────────────────────────────────────────────────────────────────

-- Add next month's partition (run before p_future fills up):
-- ALTER TABLE account_statements
--   REORGANIZE PARTITION p_future INTO (
--     PARTITION p_2027_01 VALUES LESS THAN ('2027-02-01'),
--     PARTITION p_future  VALUES LESS THAN MAXVALUE
--   );

-- Drop old partition (data retention — e.g. keep 3 years):
-- ALTER TABLE account_statements DROP PARTITION p_2023_01;
-- Note: DROP PARTITION is INSTANT — much faster than DELETE

-- Check partition sizes:
-- SELECT
--   PARTITION_NAME,
--   TABLE_ROWS,
--   ROUND(DATA_LENGTH / 1024 / 1024, 2) AS data_mb,
--   ROUND(INDEX_LENGTH / 1024 / 1024, 2) AS index_mb
-- FROM INFORMATION_SCHEMA.PARTITIONS
-- WHERE TABLE_NAME = 'account_statements'
--   AND TABLE_SCHEMA = 'citicore_account'
-- ORDER BY PARTITION_NAME;

-- Verify partition pruning is happening (check partitions column):
-- EXPLAIN SELECT * FROM account_statements
-- WHERE account_number = 'CITI000000000001'
-- AND created_at >= '2026-06-01' AND created_at < '2026-07-01';