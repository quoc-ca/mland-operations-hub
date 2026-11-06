-- V6__account_schema_alignment.sql
-- Schema alignment for Feature FE-001 Account Management per data-model.md

ALTER TABLE accounts
    ADD COLUMN email_verified_at DATETIME(6) NULL DEFAULT NULL AFTER login_email,
    ADD COLUMN resume_status ENUM('ACTIVE', 'PENDING_SETUP') NULL DEFAULT NULL AFTER status,
    ADD COLUMN revocation_pending TINYINT(1) NOT NULL DEFAULT 0 AFTER resume_status;

ALTER TABLE account_profiles
    MODIFY COLUMN full_name VARCHAR(400) NULL DEFAULT NULL;

ALTER TABLE internal_account_provisionings
    ADD COLUMN request_digest VARCHAR(64) NULL DEFAULT NULL AFTER idempotency_key,
    ADD COLUMN intended_email VARCHAR(320) NULL DEFAULT NULL AFTER request_digest,
    ADD COLUMN intended_role VARCHAR(32) NULL DEFAULT NULL AFTER intended_email,
    ADD COLUMN intended_locations JSON NULL DEFAULT NULL AFTER intended_role;
