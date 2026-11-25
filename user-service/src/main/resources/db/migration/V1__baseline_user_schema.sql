-- V1: Baseline User Service Schema
-- Creates all tables for user-service based on current entities

-- =============================================================================
-- USER_PROFILES TABLE
-- =============================================================================
CREATE TABLE IF NOT EXISTS `user_profiles` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `auth_user_id` BIGINT NOT NULL,
    `first_name` VARCHAR(100) NOT NULL,
    `last_name` VARCHAR(100) NOT NULL,
    `email` VARCHAR(255) NOT NULL,
    `phone_number` VARCHAR(20) NULL,
    `date_of_birth` DATE NULL,
    `address` TEXT NULL,
    `city` VARCHAR(100) NULL,
    `state` VARCHAR(100) NULL,
    `pincode` VARCHAR(20) NULL,
    `kyc_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_profiles_auth_user_id` (`auth_user_id`),
    UNIQUE KEY `uk_user_profiles_email` (`email`),
    UNIQUE KEY `uk_user_profiles_phone_number` (`phone_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- KYC_DOCUMENTS TABLE
-- =============================================================================
CREATE TABLE IF NOT EXISTS `kyc_documents` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `auth_user_id` BIGINT NOT NULL,
    `document_type` VARCHAR(50) NOT NULL,
    `s3_key` VARCHAR(500) NOT NULL,
    `file_name` VARCHAR(255) NOT NULL,
    `content_type` VARCHAR(100) NULL,
    `file_size` BIGINT NULL,
    `uploaded_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_kyc_documents_auth_user_id` (`auth_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- FLYWAY SCHEMA HISTORY TABLE (managed by Flyway)
-- =============================================================================
-- Flyway will create this table automatically