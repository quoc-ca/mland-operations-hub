-- V137: per-ticket customer cancellation records (003.3 T099, FR-041, FR-042).
CREATE TABLE settlement_ticket_cancellations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    cancellation_reference VARCHAR(64) NOT NULL,
    group_request_reference VARCHAR(64) NOT NULL,
    ticket_code VARCHAR(64) NOT NULL,
    group_code VARCHAR(64) NOT NULL,
    requester_account_id BIGINT NULL,
    requester_role VARCHAR(32) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    buyer_proof VARCHAR(128) NULL,
    reason VARCHAR(1000) NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    first_session_start DATETIME(6) NOT NULL,
    benefit_outcome VARCHAR(32) NOT NULL,
    points_returned BIGINT NOT NULL DEFAULT 0,
    voucher_restored TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stc_reference (cancellation_reference),
    UNIQUE KEY uk_stc_ticket_code (ticket_code),
    KEY idx_stc_group_code (group_code),
    KEY idx_stc_group_request_ref (group_request_reference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

