-- V201__r_inv_internal_account_invitations.sql
-- Revision R-INV: Internal Account Invitations and Pre-existing Synthetic Pending Quarantine (T106)

CREATE TABLE IF NOT EXISTS internal_account_invitations (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED NOT NULL,
    invitation_version INT NOT NULL DEFAULT 1,
    secret_digest VARCHAR(64) NOT NULL,
    key_version VARCHAR(32) NOT NULL DEFAULT 'v1',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NOT NULL,
    use_state VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    used_at DATETIME(6) NULL DEFAULT NULL,
    delivery_state VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    notification_request_id BIGINT NULL DEFAULT NULL,
    created_by_admin_id BIGINT UNSIGNED NULL DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_iai_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    UNIQUE KEY uq_iai_account_version (account_id, invitation_version),
    KEY idx_iai_secret_digest (secret_digest),
    KEY idx_iai_account_status (account_id, use_state, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Quarantine pre-existing synthetic internal-* pending rows for ADMIN reconciliation, never auto-bind by email
UPDATE accounts
SET status = 'SUSPENDED', resume_status = 'PENDING_SETUP'
WHERE firebase_uid LIKE 'internal-%' AND status = 'PENDING_SETUP';
