-- V138: per-ticket Mland cancellation remedy (003.3 T100, FR-043).
CREATE TABLE settlement_ticket_mland_remedies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    remedy_reference VARCHAR(64) NOT NULL,
    ticket_code VARCHAR(64) NOT NULL,
    group_code VARCHAR(64) NOT NULL,
    cancelled_session_reference VARCHAR(64) NOT NULL,
    replacement_session_references VARCHAR(512) NOT NULL,
    replacement_start_at DATETIME(6) NOT NULL,
    answer_deadline_at DATETIME(6) NOT NULL,
    buyer_answer VARCHAR(32) NULL,
    answered_at DATETIME(6) NULL,
    outcome VARCHAR(32) NOT NULL,
    refund_request_reference VARCHAR(64) NULL,
    points_returned BIGINT NOT NULL DEFAULT 0,
    voucher_restored TINYINT(1) NOT NULL DEFAULT 0,
    requester_account_id BIGINT NULL,
    requester_role VARCHAR(32) NULL,
    channel VARCHAR(32) NULL,
    buyer_proof VARCHAR(128) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stmr_reference (remedy_reference),
    KEY idx_stmr_ticket_code (ticket_code),
    KEY idx_stmr_group_code (group_code),
    KEY idx_stmr_deadline_outcome (answer_deadline_at, outcome)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

