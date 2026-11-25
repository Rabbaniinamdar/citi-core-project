-- V3: Balance Non-Negative Constraint
-- Adds database-level CHECK constraint to enforce non-negative balances
-- This implements P0-14: Database-Level Balance Constraints

-- First, check for any existing negative balances (should be none in production)
-- If any exist, they must be remediated before this constraint can be added
SELECT COUNT(*) AS negative_balance_count
FROM accounts
WHERE balance < 0;

-- Add CHECK constraint: balance >= 0
-- This prevents any account from going into negative balance at the database level
ALTER TABLE `accounts`
    ADD CONSTRAINT `chk_balance_non_negative`
    CHECK (`balance` >= 0);

-- Note: The CHECK constraint name matches the Hibernate annotation:
-- @Check(name = "chk_balance_non_negative", constraints = "balance >= 0")