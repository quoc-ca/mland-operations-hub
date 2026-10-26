-- V6__workshop_operations.sql
-- Schema migration for Feature 003.2 Workshop Operations

CREATE TABLE workshop_branches (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reference VARCHAR(36) NOT NULL,
    code VARCHAR(64) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    address_snapshot VARCHAR(1000) NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_wb_reference (reference),
    UNIQUE KEY uq_wb_code (code),
    KEY idx_wb_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE workshop_slots (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reference VARCHAR(36) NOT NULL,
    branch_id BIGINT UNSIGNED NOT NULL,
    label VARCHAR(100) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    default_capacity INT NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_ws_reference (reference),
    CONSTRAINT fk_ws_branch FOREIGN KEY (branch_id) REFERENCES workshop_branches (id),
    KEY idx_ws_branch_active (branch_id, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE workshop_sessions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reference VARCHAR(36) NOT NULL,
    branch_id BIGINT UNSIGNED NOT NULL,
    slot_id BIGINT UNSIGNED NOT NULL,
    workshop_date DATE NOT NULL,
    scheduled_start_at DATETIME(6) NOT NULL,
    scheduled_end_at DATETIME(6) NOT NULL,
    check_in_opens_at DATETIME(6) NOT NULL,
    check_in_opening_source ENUM('DEFAULT', 'MANAGER_SET') NOT NULL DEFAULT 'DEFAULT',
    capacity INT NOT NULL,
    state ENUM('DRAFT', 'PUBLISHED', 'SALES_PAUSED', 'IN_PROGRESS', 'ATTENDANCE_FINALIZED', 'CANCELLATION_REQUESTED', 'CANCELLED') NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 0,
    cancellation_handoff_reference VARCHAR(128) NULL DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_session_reference (reference),
    CONSTRAINT fk_session_branch FOREIGN KEY (branch_id) REFERENCES workshop_branches (id),
    CONSTRAINT fk_session_slot FOREIGN KEY (slot_id) REFERENCES workshop_slots (id),
    UNIQUE KEY uq_session_branch_date_slot (branch_id, workshop_date, slot_id),
    KEY idx_session_branch_state (branch_id, state),
    KEY idx_session_date (workshop_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE session_staff_assignments (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id BIGINT UNSIGNED NOT NULL,
    personnel_id BIGINT UNSIGNED NOT NULL,
    assigned_by_id BIGINT UNSIGNED NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    assigned_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    revoked_at DATETIME(6) NULL DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ssa_session FOREIGN KEY (session_id) REFERENCES workshop_sessions (id),
    KEY idx_ssa_session_personnel (session_id, personnel_id, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE session_operational_leads (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id BIGINT UNSIGNED NOT NULL,
    manager_personnel_id BIGINT UNSIGNED NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    reason_code VARCHAR(80) NOT NULL,
    reason_note VARCHAR(500) NULL DEFAULT NULL,
    evidence_reference VARCHAR(255) NULL DEFAULT NULL,
    assigned_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    revoked_at DATETIME(6) NULL DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_sol_session FOREIGN KEY (session_id) REFERENCES workshop_sessions (id),
    KEY idx_sol_session_active (session_id, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE capacity_holds (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reference VARCHAR(36) NOT NULL,
    session_id BIGINT UNSIGNED NOT NULL,
    booking_reference VARCHAR(64) NOT NULL,
    requested_ticket_count INT NOT NULL,
    state ENUM('ACTIVE', 'CONFIRMED', 'RELEASED', 'EXPIRED') NOT NULL DEFAULT 'ACTIVE',
    expires_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_ch_reference (reference),
    CONSTRAINT fk_ch_session FOREIGN KEY (session_id) REFERENCES workshop_sessions (id),
    KEY idx_ch_session_state (session_id, state),
    KEY idx_ch_booking (booking_reference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE attendance_tickets (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reference VARCHAR(36) NOT NULL,
    booking_reference VARCHAR(64) NOT NULL,
    ticket_sequence INT NOT NULL,
    ticket_code VARCHAR(32) NOT NULL,
    qr_reference VARCHAR(64) NOT NULL,
    validity_state ENUM('ACTIVE', 'CANCELLED', 'REVOKED', 'REFUNDED') NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_at_reference (reference),
    UNIQUE KEY uq_at_code (ticket_code),
    UNIQUE KEY uq_at_qr (qr_reference),
    KEY idx_at_booking (booking_reference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ticket_session_entitlements (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ticket_id BIGINT UNSIGNED NOT NULL,
    session_id BIGINT UNSIGNED NOT NULL,
    attendance_state ENUM('NOT_CHECKED_IN', 'CHECKED_IN', 'NO_SHOW') NOT NULL DEFAULT 'NOT_CHECKED_IN',
    checked_in_at DATETIME(6) NULL DEFAULT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_tse_ticket FOREIGN KEY (ticket_id) REFERENCES attendance_tickets (id),
    CONSTRAINT fk_tse_session FOREIGN KEY (session_id) REFERENCES workshop_sessions (id),
    UNIQUE KEY uq_tse_ticket_session (ticket_id, session_id),
    KEY idx_tse_session_state (session_id, attendance_state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE attendance_actions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ticket_id BIGINT UNSIGNED NOT NULL,
    session_id BIGINT UNSIGNED NOT NULL,
    action_type ENUM('CHECK_IN', 'CHECK_IN_OVERRIDE', 'REVERSAL', 'FINALIZATION', 'CORRECTION') NOT NULL,
    actor_id BIGINT UNSIGNED NOT NULL,
    channel VARCHAR(32) NOT NULL DEFAULT 'ONSITE_SCAN',
    reason_code VARCHAR(80) NULL DEFAULT NULL,
    reason_note VARCHAR(500) NULL DEFAULT NULL,
    evidence_reference VARCHAR(255) NULL DEFAULT NULL,
    occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_aa_ticket FOREIGN KEY (ticket_id) REFERENCES attendance_tickets (id),
    CONSTRAINT fk_aa_session FOREIGN KEY (session_id) REFERENCES workshop_sessions (id),
    KEY idx_aa_ticket_session (ticket_id, session_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE operations_audit_events (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id BIGINT UNSIGNED NULL DEFAULT NULL,
    actor_id BIGINT UNSIGNED NULL DEFAULT NULL,
    action VARCHAR(80) NOT NULL,
    outcome ENUM('SUCCESS', 'FAILURE') NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    metadata JSON NULL DEFAULT NULL,
    occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_oae_session (session_id, occurred_at),
    KEY idx_oae_actor (actor_id, occurred_at),
    KEY idx_oae_occurred_at (occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cancellation_handoffs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id BIGINT UNSIGNED NOT NULL,
    requester_id BIGINT UNSIGNED NOT NULL,
    reason_code VARCHAR(80) NOT NULL,
    reason_note VARCHAR(500) NULL DEFAULT NULL,
    evidence_reference VARCHAR(255) NULL DEFAULT NULL,
    cancellation_case_reference VARCHAR(128) NULL DEFAULT NULL,
    status ENUM('REQUESTED', 'RESOLVED') NOT NULL DEFAULT 'REQUESTED',
    requested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    resolved_at DATETIME(6) NULL DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ch_handoff_session FOREIGN KEY (session_id) REFERENCES workshop_sessions (id),
    KEY idx_ch_handoff_session (session_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
