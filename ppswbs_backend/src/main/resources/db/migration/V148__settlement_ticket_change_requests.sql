-- V148: 003.3 T117 (FR-045a, decided 2026-11-14). An online change that fails only on a rule a Manager may lift
-- (reschedule quota, the used allowance, the 24 h window) is stored as a request in the Staff/Manager queue instead of
-- being refused. PENDING moves to APPROVED, REJECTED or EXPIRED; approval equals a Manager-granted allowance (FR-054)
-- followed by one counted change. The buyer proof is consumed when the request is stored and kept for audit.
CREATE TABLE settlement_ticket_change_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_reference VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    ticket_code VARCHAR(64) NOT NULL,
    group_code VARCHAR(64) NOT NULL,
    location_reference BIGINT NULL,
    change_type VARCHAR(32) NOT NULL,
    target_session_references VARCHAR(512) NOT NULL,
    blocked_rules VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    price_increase_amount_vnd BIGINT NOT NULL DEFAULT 0,
    buyer_confirmed_increase TINYINT(1) NOT NULL DEFAULT 0,
    requester_account_id BIGINT NULL,
    requester_role VARCHAR(32) NULL,
    channel VARCHAR(32) NULL,
    buyer_proof VARCHAR(128) NULL,
    reason VARCHAR(1000) NULL,
    expires_at DATETIME(6) NOT NULL,
    decision_reason VARCHAR(1000) NULL,
    decided_by_account_id BIGINT NULL,
    decided_at DATETIME(6) NULL,
    decision_idempotency_key VARCHAR(128) NULL,
    applied_change_reference VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stcr_reference (request_reference),
    UNIQUE KEY uk_stcr_idempotency (ticket_code, idempotency_key),
    KEY idx_stcr_status_created (status, created_at),
    KEY idx_stcr_ticket_status (ticket_code, status),
    KEY idx_stcr_expiry (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
