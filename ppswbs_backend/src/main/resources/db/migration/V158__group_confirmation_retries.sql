-- V158: durable confirmation retry of a booking group whose verified payment arrived inside the hold but whose
-- confirmation failed on a dependency (003.1 FR-038, FR-039, FR-040, CHK013, T145; decided 2026-11-15).
-- Append-only: the group stays PENDING_PAYMENT and the attempt SUCCESS (verified evidence only); the wait is the
-- WAITING row. No raw provider payload, signature or contact data is stored. No applied migration is edited.

CREATE TABLE group_confirmation_retries (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    group_id BIGINT UNSIGNED NOT NULL,
    attempt_id BIGINT UNSIGNED NOT NULL,
    verified_evidence_reference VARCHAR(128) NOT NULL,
    merchant_reference VARCHAR(64) NOT NULL,
    provider_transaction_reference VARCHAR(64) NULL DEFAULT NULL,
    amount_vnd BIGINT NOT NULL,
    received_at DATETIME(6) NOT NULL,
    retry_status VARCHAR(24) NOT NULL,
    retries_made INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME(6) NULL DEFAULT NULL,
    last_failure_code VARCHAR(64) NULL DEFAULT NULL,
    late_payment_review_reference VARCHAR(64) NULL DEFAULT NULL,
    correlation_reference VARCHAR(128) NULL DEFAULT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_gcr_evidence (verified_evidence_reference),
    KEY idx_gcr_group (group_id),
    KEY idx_gcr_due (retry_status, next_retry_at),
    CONSTRAINT fk_gcr_group FOREIGN KEY (group_id) REFERENCES booking_groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_gcr_attempt FOREIGN KEY (attempt_id) REFERENCES group_payment_attempts (id) ON DELETE CASCADE,
    CONSTRAINT ck_gcr_amount CHECK (amount_vnd > 0),
    CONSTRAINT ck_gcr_status CHECK (retry_status IN ('WAITING', 'CONFIRMED', 'HANDED_TO_LATE_REVIEW')),
    CONSTRAINT ck_gcr_retries CHECK (retries_made >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
