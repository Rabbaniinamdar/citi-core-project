-- V1: Baseline Auth Service Schema
-- Creates all tables for auth-service based on current entities

-- =============================================================================
-- ROLES TABLE
-- =============================================================================
CREATE TABLE IF NOT EXISTS `roles` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(50) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_roles_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `roles` (`name`) VALUES
    ('ROLE_CUSTOMER'),
    ('ROLE_ADMIN'),
    ('ROLE_MANAGER'),
    ('ROLE_KYC_OFFICER'),
    ('ROLE_KYC_MANAGER'),
    ('ROLE_COMPLIANCE_OFFICER'),
    ('ROLE_OPERATIONS_OFFICER'),
    ('ROLE_SUPPORT_AGENT'),
    ('ROLE_AUDITOR'),
    ('ROLE_SERVICE');

-- =============================================================================
-- USERS TABLE
-- =============================================================================
CREATE TABLE IF NOT EXISTS `users` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `email` VARCHAR(100) NOT NULL,
    `password` VARCHAR(255) NOT NULL,
    `email_verified` BOOLEAN NOT NULL DEFAULT FALSE,
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    `verification_code` VARCHAR(255) NULL,
    `verification_expiration` DATETIME NULL,
    `mfa_enabled` BOOLEAN NOT NULL DEFAULT FALSE,
    `mfa_method` VARCHAR(20) NULL,
    `mfa_enabled_at` DATETIME NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_users_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- USER_ROLES JOIN TABLE
-- =============================================================================
CREATE TABLE IF NOT EXISTS `user_roles` (
    `user_id` BIGINT NOT NULL,
    `role_id` BIGINT NOT NULL,
    PRIMARY KEY (`user_id`, `role_id`),
    CONSTRAINT `fk_user_roles_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_user_roles_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- REFRESH_TOKENS TABLE
-- =============================================================================
CREATE TABLE IF NOT EXISTS `refresh_tokens` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `token` VARCHAR(255) NOT NULL,
    `expiry_date` DATETIME NOT NULL,
    `revoked` BOOLEAN NOT NULL DEFAULT FALSE,
    `user_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_refresh_tokens_token` (`token`),
    CONSTRAINT `fk_refresh_tokens_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- LOGIN_ATTEMPTS TABLE
-- =============================================================================
CREATE TABLE IF NOT EXISTS `login_attempts` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `email` VARCHAR(100) NOT NULL,
    `failed_count` INT NOT NULL DEFAULT 0,
    `locked_until` DATETIME NULL,
    `last_attempt` DATETIME NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_login_attempts_email` (`email`),
    KEY `idx_login_attempts_locked_until` (`locked_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- EMAIL_VERIFICATION_TOKENS TABLE
-- =============================================================================
CREATE TABLE IF NOT EXISTS `email_verification_tokens` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `token` VARCHAR(255) NOT NULL,
    `expiry_date` DATETIME NOT NULL,
    `user_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_email_verification_tokens_token` (`token`),
    CONSTRAINT `fk_email_verification_tokens_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- PASSWORD_RESET_TOKENS TABLE
-- =============================================================================
CREATE TABLE IF NOT EXISTS `password_reset_tokens` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `token` VARCHAR(255) NOT NULL,
    `expiry_date` DATETIME NOT NULL,
    `used` BOOLEAN NOT NULL DEFAULT FALSE,
    `used_at` DATETIME NULL,
    `user_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_password_reset_tokens_token` (`token`),
    CONSTRAINT `fk_password_reset_tokens_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- LOGIN_CHALLENGES TABLE (for MFA)
-- =============================================================================
CREATE TABLE IF NOT EXISTS `login_challenges` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `challenge_id` VARCHAR(64) NOT NULL,
    `otp_hash` VARCHAR(256) NOT NULL,
    `expires_at` DATETIME NOT NULL,
    `attempts` INT NOT NULL DEFAULT 0,
    `max_attempts` INT NOT NULL DEFAULT 3,
    `consumed` BOOLEAN NOT NULL DEFAULT FALSE,
    `purpose` VARCHAR(30) NOT NULL DEFAULT 'LOGIN_MFA',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `consumed_at` DATETIME NULL,
    `resend_count` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_login_challenges_challenge_id` (`challenge_id`),
    KEY `idx_login_challenges_user_id` (`user_id`),
    KEY `idx_login_challenges_expires_at` (`expires_at`),
    CONSTRAINT `fk_login_challenges_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- FLYWAY SCHEMA HISTORY TABLE (managed by Flyway)
-- =============================================================================
-- Flyway will create this table automatically