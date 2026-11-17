CREATE TABLE settlement_final_bills (
    id BIGINT NOT NULL AUTO_INCREMENT,
    bill_reference VARCHAR(128) NOT NULL,
    booking_reference VARCHAR(128) NOT NULL,
    post_promotion_amount_vnd BIGINT NOT NULL,
    accepted_deposit_amount_vnd BIGINT NOT NULL,
    remaining_amount_vnd BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    lifecycle_status VARCHAR(24) NOT NULL,
    frozen_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_settlement_bill_reference (bill_reference),
    UNIQUE KEY uq_settlement_bill_booking (booking_reference),
    CONSTRAINT ck_settlement_bill_amounts CHECK (post_promotion_amount_vnd >= 0 AND accepted_deposit_amount_vnd >= 0 AND remaining_amount_vnd >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE settlement_charges (
    id BIGINT NOT NULL AUTO_INCREMENT,
    charge_reference VARCHAR(128) NOT NULL,
    bill_reference VARCHAR(128) NOT NULL,
    source_category VARCHAR(32) NOT NULL,
    amount_vnd BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    lifecycle_status VARCHAR(24) NOT NULL,
    reason_reference VARCHAR(255) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_settlement_charge_reference (charge_reference),
    KEY ix_settlement_charge_bill (bill_reference),
    CONSTRAINT fk_settlement_charge_bill FOREIGN KEY (bill_reference) REFERENCES settlement_final_bills (bill_reference),
    CONSTRAINT ck_settlement_charge_amount CHECK (amount_vnd >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE settlement_cases (
    id BIGINT NOT NULL AUTO_INCREMENT,
    case_reference VARCHAR(128) NOT NULL,
    booking_reference VARCHAR(128) NOT NULL,
    case_type VARCHAR(32) NOT NULL,
    lifecycle_status VARCHAR(32) NOT NULL,
    reason_reference VARCHAR(255) NULL,
    correlation_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_settlement_case_reference_v30 (case_reference),
    KEY ix_settlement_case_booking (booking_reference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE settlement_evidence_conclusions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    evidence_reference VARCHAR(128) NOT NULL,
    booking_reference VARCHAR(128) NOT NULL,
    provider_transaction_reference VARCHAR(255) NULL,
    conclusion VARCHAR(32) NOT NULL,
    amount_vnd BIGINT NULL,
    currency_code CHAR(3) NOT NULL,
    operation_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_settlement_evidence_reference (evidence_reference),
    UNIQUE KEY uq_settlement_evidence_operation (operation_reference),
    CONSTRAINT ck_settlement_evidence_amount CHECK (amount_vnd IS NULL OR amount_vnd >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE settlement_refund_policy_snapshots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    snapshot_reference VARCHAR(128) NOT NULL,
    booking_reference VARCHAR(128) NOT NULL,
    policy_revision VARCHAR(64) NOT NULL,
    collected_amount_vnd BIGINT NOT NULL,
    refundable_amount_vnd BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    captured_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_settlement_policy_snapshot (snapshot_reference),
    KEY ix_settlement_policy_booking (booking_reference),
    CONSTRAINT ck_settlement_policy_amounts CHECK (collected_amount_vnd >= 0 AND refundable_amount_vnd >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE settlement_business_audits (
    id BIGINT NOT NULL AUTO_INCREMENT,
    audit_reference VARCHAR(128) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_reference VARCHAR(128) NOT NULL,
    action_code VARCHAR(64) NOT NULL,
    actor_reference VARCHAR(128) NULL,
    correlation_reference VARCHAR(128) NOT NULL,
    evidence_reference VARCHAR(128) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_settlement_audit_reference (audit_reference),
    KEY ix_settlement_audit_aggregate (aggregate_type, aggregate_reference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE settlement_idempotency_outcomes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    operation_reference VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    outcome_status VARCHAR(32) NOT NULL,
    outcome_reference VARCHAR(128) NULL,
    correlation_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_settlement_idempotency_operation (operation_reference),
    KEY ix_settlement_idempotency_fingerprint (request_fingerprint)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
