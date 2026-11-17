CREATE TABLE settlement_refund_policy_revisions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    policy_reference VARCHAR(128) NOT NULL,
    effective_at TIMESTAMP(6) NOT NULL,
    whole_booking_refund_percent INT NOT NULL,
    created_by_account_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_settlement_policy_reference (policy_reference),
    UNIQUE KEY uq_settlement_policy_effective_at (effective_at),
    CONSTRAINT ck_settlement_policy_percent CHECK (whole_booking_refund_percent BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
