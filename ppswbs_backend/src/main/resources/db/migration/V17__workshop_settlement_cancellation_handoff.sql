CREATE TABLE settlement_cancellation_cases (
    id BIGINT NOT NULL AUTO_INCREMENT,
    case_reference VARCHAR(128) NOT NULL,
    session_reference VARCHAR(128) NOT NULL,
    affected_booking_references JSON NOT NULL,
    cancellation_reason_reference VARCHAR(128) NOT NULL,
    operation_reference VARCHAR(128) NOT NULL,
    correlation_reference VARCHAR(128) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_settlement_case_reference (case_reference),
    UNIQUE KEY uq_settlement_case_operation (operation_reference)
) ENGINE=InnoDB;

CREATE TABLE settlement_handoff_idempotency (
    id BIGINT NOT NULL AUTO_INCREMENT,
    operation_reference VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    lifecycle_status VARCHAR(24) NOT NULL,
    cancellation_case_reference VARCHAR(128) NULL,
    rejection_reason VARCHAR(255) NULL,
    correlation_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_settlement_handoff_operation (operation_reference),
    KEY ix_settlement_handoff_case (cancellation_case_reference)
) ENGINE=InnoDB;
