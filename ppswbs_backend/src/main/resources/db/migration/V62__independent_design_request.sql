-- Flyway Migration V62: Independent Design Request Rebase (Feature 004)
-- Version 60 was already allocated to the workshop settlement add-on bill migration.
-- Keep this migration append-only and uniquely versioned across the repository.
-- Append-only migration

CREATE TABLE design_requests (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    request_reference VARCHAR(64) NOT NULL UNIQUE,
    owner_account_id BIGINT NOT NULL,
    guest_proof_hash VARCHAR(128) NULL,
    ownership_type VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
    description VARCHAR(4000) NULL,
    catalogue_reference VARCHAR(128) NULL,
    request_version INT NOT NULL DEFAULT 1,
    idempotency_key VARCHAR(128) NOT NULL,
    request_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    analysis_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    review_status VARCHAR(32) NOT NULL DEFAULT 'NEED_REVIEW',
    estimated_value BIGINT NULL,
    next_action VARCHAR(64) NOT NULL,
    safe_failure_code VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_dr_ownership_type CHECK (ownership_type IN ('MEMBER', 'GUEST')),
    CONSTRAINT chk_dr_request_status CHECK (request_status IN ('ACTIVE', 'WITHDRAWN', 'COMPLETED')),
    CONSTRAINT chk_dr_analysis_status CHECK (analysis_status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_dr_review_status CHECK (review_status IN ('NOT_REQUIRED', 'NEED_REVIEW', 'ACCEPTED', 'REJECTED', 'CLARIFICATION_REQUIRED')),
    UNIQUE KEY uk_dr_owner_version_idemp (owner_account_id, request_version, idempotency_key),
    INDEX idx_dr_review_queue (review_status, analysis_status, created_at),
    INDEX idx_dr_owner (owner_account_id, created_at)
);

ALTER TABLE image_consents
    ADD COLUMN actor_account_id BIGINT NULL,
    ADD COLUMN design_request_id VARCHAR(36) NULL,
    MODIFY COLUMN booking_code VARCHAR(64) NULL,
    MODIFY COLUMN member_id BIGINT NULL;

CREATE INDEX idx_ic_actor_design ON image_consents (actor_account_id, design_request_id, consent_version);
