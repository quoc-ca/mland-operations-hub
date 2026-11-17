-- V139: per-ticket session changes and manager change allowances (003.3 T101, FR-044, FR-045, FR-045a, FR-054, FR-055).
CREATE TABLE settlement_ticket_changes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    change_reference VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    ticket_code VARCHAR(64) NOT NULL,
    group_code VARCHAR(64) NOT NULL,
    change_type VARCHAR(32) NOT NULL,
    from_session_references VARCHAR(512) NULL,
    to_session_references VARCHAR(512) NULL,
    counted TINYINT(1) NOT NULL DEFAULT 1,
    price_increase_amount_vnd BIGINT NOT NULL DEFAULT 0,
    buyer_confirmed_increase TINYINT(1) NOT NULL DEFAULT 0,
    applied TINYINT(1) NOT NULL DEFAULT 1,
    requester_account_id BIGINT NULL,
    requester_role VARCHAR(32) NULL,
    channel VARCHAR(32) NULL,
    buyer_proof VARCHAR(128) NULL,
    reason VARCHAR(1000) NULL,
    balance_due_snapshot BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stc_reference (change_reference),
    UNIQUE KEY uk_stc_idempotency (ticket_code, idempotency_key),
    KEY idx_stc_ticket_code (ticket_code),
    KEY idx_stc_group_code (group_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

