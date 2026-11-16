-- V156: excess or duplicate money on a CONFIRMED booking group (003.1 FR-013a, CHK001, T124; decided 2026-11-15).
-- Append-only: a second verified success with a different merchant reference is recorded in full and refunded
-- 100% by 003.3. The group, tickets, capacity and benefits are not touched. No raw provider payload, signature or
-- contact data is stored.

CREATE TABLE group_excess_payments (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    group_id BIGINT UNSIGNED NOT NULL,
    verified_evidence_reference VARCHAR(128) NOT NULL,
    merchant_reference VARCHAR(64) NOT NULL,
    provider_transaction_reference VARCHAR(64) NULL DEFAULT NULL,
    amount_vnd BIGINT NOT NULL,
    received_at DATETIME(6) NOT NULL,
    refund_request_reference VARCHAR(64) NULL DEFAULT NULL,
    refund_outcome ENUM('ACCEPTED', 'PRIOR_EQUIVALENT', 'REJECTED') NOT NULL,
    correlation_reference VARCHAR(128) NULL DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_gep_evidence (verified_evidence_reference),
    KEY idx_gep_group (group_id),
    CONSTRAINT fk_gep_group FOREIGN KEY (group_id) REFERENCES booking_groups (id) ON DELETE CASCADE,
    CONSTRAINT ck_gep_amount CHECK (amount_vnd > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
