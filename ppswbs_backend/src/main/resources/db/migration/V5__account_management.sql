-- V5__account_management.sql
-- Schema migration for Feature FE-001 Account Management

CREATE TABLE accounts (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    firebase_uid VARCHAR(128) NOT NULL,
    login_email VARCHAR(320) NULL DEFAULT NULL,
    role ENUM('MEMBER', 'STAFF', 'MANAGER', 'ADMIN') NOT NULL DEFAULT 'MEMBER',
    status ENUM('ACTIVE', 'PENDING_SETUP', 'SUSPENDED') NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    session_revocation_confirmed_at DATETIME(6) NULL DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_accounts_firebase_uid (firebase_uid),
    KEY idx_accounts_email (login_email),
    KEY idx_accounts_role_status (role, status),
    KEY idx_accounts_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE account_profiles (
    account_id BIGINT UNSIGNED NOT NULL,
    full_name VARCHAR(400) NOT NULL,
    phone VARCHAR(30) NULL DEFAULT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (account_id),
    CONSTRAINT fk_account_profiles_account FOREIGN KEY (account_id) REFERENCES accounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE account_location_assignments (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED NOT NULL,
    location_id BIGINT UNSIGNED NOT NULL,
    assigned_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    active TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    CONSTRAINT fk_ala_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    UNIQUE KEY uq_ala_account_location_active (account_id, location_id, active),
    KEY idx_ala_account_active (account_id, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE internal_account_provisionings (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    status ENUM('INVITATION_SENT', 'SETUP_COMPLETE', 'INVITATION_FAILED') NOT NULL DEFAULT 'INVITATION_SENT',
    invitation_last_sent_at DATETIME(6) NULL DEFAULT NULL,
    created_by_admin_id BIGINT UNSIGNED NOT NULL,
    firebase_setup_link_issued_at DATETIME(6) NULL DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_iap_idempotency_key (idempotency_key),
    CONSTRAINT fk_iap_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT fk_iap_admin FOREIGN KEY (created_by_admin_id) REFERENCES accounts (id),
    KEY idx_iap_account (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE account_audit_events (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    actor_account_id BIGINT UNSIGNED NULL DEFAULT NULL,
    target_account_id BIGINT UNSIGNED NULL DEFAULT NULL,
    action VARCHAR(80) NOT NULL,
    outcome ENUM('SUCCESS', 'FAILURE') NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    metadata JSON NULL DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_aae_target (target_account_id, occurred_at),
    KEY idx_aae_actor (actor_account_id, occurred_at),
    KEY idx_aae_occurred_at (occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
