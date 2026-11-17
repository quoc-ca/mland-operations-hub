CREATE TABLE settlement_booking_mland_refund_policy_snapshots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    snapshot_reference VARCHAR(128) NOT NULL,
    booking_reference VARCHAR(128) NOT NULL,
    policy_reference VARCHAR(128) NOT NULL,
    refund_percent SMALLINT NOT NULL,
    confirmed_at TIMESTAMP(6) NOT NULL,
    captured_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_settlement_mland_policy_snapshot_ref (snapshot_reference),
    UNIQUE KEY uq_settlement_mland_policy_booking (booking_reference),
    CONSTRAINT ck_settlement_mland_policy_percent CHECK (refund_percent BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE settlement_mland_remedy_snapshots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    snapshot_reference VARCHAR(128) NOT NULL,
    case_reference VARCHAR(128) NOT NULL,
    booking_reference VARCHAR(128) NOT NULL,
    policy_reference VARCHAR(128) NOT NULL,
    cancellation_handoff_reference VARCHAR(128) NOT NULL,
    replacement_outcome VARCHAR(16) NOT NULL,
    refund_percent SMALLINT NOT NULL,
    confirmed_at TIMESTAMP(6) NOT NULL,
    collected_amount_vnd BIGINT NOT NULL,
    refundable_amount_vnd BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    captured_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_settlement_mland_remedy_snapshot_ref (snapshot_reference),
    UNIQUE KEY uq_settlement_mland_remedy_case (case_reference),
    UNIQUE KEY uq_settlement_mland_remedy_booking (booking_reference),
    CONSTRAINT fk_settlement_mland_remedy_case
        FOREIGN KEY (case_reference) REFERENCES settlement_cases (case_reference),
    CONSTRAINT ck_settlement_mland_remedy_values CHECK (
        refund_percent BETWEEN 0 AND 100
        AND collected_amount_vnd >= 0
        AND refundable_amount_vnd >= 0
        AND refundable_amount_vnd <= collected_amount_vnd
    ),
    CONSTRAINT ck_settlement_mland_remedy_outcome CHECK (replacement_outcome IN ('UNAVAILABLE', 'DECLINED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
