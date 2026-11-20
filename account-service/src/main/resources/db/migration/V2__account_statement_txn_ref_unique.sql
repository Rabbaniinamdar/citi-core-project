-- V2: Account Statement txnRef Unique Per Account
-- Adds composite unique constraint on (account_number, txn_ref)

-- First, drop existing single-column unique constraint if it exists
ALTER TABLE account_statements
    DROP INDEX IF EXISTS uk_statement_txn_ref;

-- Add composite unique constraint (account + txn_ref)
ALTER TABLE account_statements
    ADD CONSTRAINT uk_statement_account_txn_ref UNIQUE (account_number, txn_ref);

-- Add index for performance on account_number lookups
CREATE INDEX IF NOT EXISTS idx_statement_account_txn_ref
    ON account_statements (account_number, txn_ref);