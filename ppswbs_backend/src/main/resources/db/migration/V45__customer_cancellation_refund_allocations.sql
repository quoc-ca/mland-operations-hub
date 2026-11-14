CREATE TABLE settlement_customer_cancellation_snapshots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    snapshot_reference VARCHAR(128) NOT NULL,
    case_reference VARCHAR(128) NOT NULL,
    booking_reference VARCHAR(128) NOT NULL,
    policy_revision VARCHAR(64) NOT NULL,
    refund_percent SMALLINT NOT NULL,
    earliest_session_reference VARCHAR(128) NULL,
    earliest_session_start TIMESTAMP(6) NULL,
    paid_deposit_amount_vnd BIGINT NOT NULL,
    refund_amount_vnd BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    eligibility_outcome VARCHAR(16) NOT NULL,
    rejection_reason VARCHAR(64) NULL,
    requested_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_settlement_customer_cancel_snapshot_ref (snapshot_reference),
    UNIQUE KEY uq_settlement_customer_cancel_case (case_reference),
    UNIQUE KEY uq_settlement_customer_cancel_booking (booking_reference),
    CONSTRAINT fk_settlement_customer_cancel_case
        FOREIGN KEY (case_reference) REFERENCES settlement_cases (case_reference),
    CONSTRAINT ck_settlement_customer_cancel_percent CHECK (refund_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_settlement_customer_cancel_amounts CHECK (
        paid_deposit_amount_vnd >= 0 AND refund_amount_vnd >= 0
    ),
    CONSTRAINT ck_settlement_customer_cancel_outcome CHECK (
        eligibility_outcome IN ('ELIGIBLE', 'REJECTED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE settlement_refund_allocations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    allocation_reference VARCHAR(128) NOT NULL,
    case_reference VARCHAR(128) NOT NULL,
    original_payment_reference VARCHAR(128) NOT NULL,
    original_provider_transaction_number VARCHAR(15) NOT NULL,
    original_provider_create_date CHAR(14) NOT NULL,
    original_amount_vnd BIGINT NOT NULL,
    allocated_amount_vnd BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    operation_reference VARCHAR(128) NOT NULL,
    provider_refund_transaction_number VARCHAR(15) NULL,
    lifecycle_status VARCHAR(24) NOT NULL,
    provider_response_code VARCHAR(8) NULL,
    provider_transaction_type VARCHAR(4) NULL,
    provider_transaction_status VARCHAR(4) NULL,
    provider_accepted_at TIMESTAMP(6) NULL,
    last_query_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_settlement_refund_allocation_ref (allocation_reference),
    UNIQUE KEY uq_settlement_refund_operation_ref (operation_reference),
    UNIQUE KEY uq_settlement_refund_case_payment (case_reference, original_payment_reference),
    KEY ix_settlement_refund_polling (lifecycle_status, provider_accepted_at, last_query_at),
    CONSTRAINT fk_settlement_refund_allocation_case
        FOREIGN KEY (case_reference) REFERENCES settlement_cases (case_reference),
    CONSTRAINT ck_settlement_refund_allocation_amounts CHECK (
        original_amount_vnd > 0 AND allocated_amount_vnd > 0 AND allocated_amount_vnd <= original_amount_vnd
    ),
    CONSTRAINT ck_settlement_refund_lifecycle CHECK (
        lifecycle_status IN ('PREPARED', 'ACCEPTED', 'UNKNOWN', 'VERIFIED', 'REJECTED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
