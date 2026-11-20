-- V1: Idempotency Key User Scoping
-- Adds auth_user_id column and composite unique constraint to idempotency_keys table

ALTER TABLE idempotency_keys
    ADD COLUMN auth_user_id BIGINT NOT NULL AFTER id;

-- Drop the old single-column unique constraint
ALTER TABLE idempotency_keys
    DROP INDEX uk_idempotency_key;

-- Add composite unique constraint (user + key)
ALTER TABLE idempotency_keys
    ADD CONSTRAINT uk_idempotency_key_user UNIQUE (auth_user_id, idempotency_key);

-- Add indexes for performance
CREATE INDEX idx_idempotency_key ON idempotency_keys (idempotency_key);
CREATE INDEX idx_idempotency_user ON idempotency_keys (auth_user_id);