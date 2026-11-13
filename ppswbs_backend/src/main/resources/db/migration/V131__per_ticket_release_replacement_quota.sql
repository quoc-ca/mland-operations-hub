-- 003.2 Phase 14: per-ticket cancellation (T091, FR-031), per-ticket session replacement (T094, FR-034)
-- and the per-session reschedule quota (T095, FR-035).

-- FR-035: NULL override = the default floor(25% x capacity), at least 1, recomputed from the current capacity.
ALTER TABLE workshop_sessions
    ADD COLUMN reschedule_quota_override INT NULL,
    ADD COLUMN reschedule_quota_used INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_ws_reschedule_quota_override CHECK (reschedule_quota_override IS NULL OR reschedule_quota_override >= 0),
    ADD CONSTRAINT ck_ws_reschedule_quota_used CHECK (reschedule_quota_used >= 0);

-- FR-031: one row per released (ticket, session) seat. The entitlement row itself is removed, so this keeps
-- which future sessions a cancelled ticket held; past sessions and their attendance are never touched.
CREATE TABLE ticket_entitlement_releases (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ticket_id BIGINT UNSIGNED NOT NULL,
    session_id BIGINT UNSIGNED NOT NULL,
    cancellation_case_reference VARCHAR(128) NOT NULL,
    operation_reference VARCHAR(128) NOT NULL,
    correlation_reference VARCHAR(64) NULL,
    released_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ter_ticket_session (ticket_id, session_id),
    KEY idx_ter_session (session_id),
    CONSTRAINT fk_ter_ticket FOREIGN KEY (ticket_id) REFERENCES attendance_tickets (id),
    CONSTRAINT fk_ter_session FOREIGN KEY (session_id) REFERENCES workshop_sessions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- FR-034: one row per moved (ticket, source -> target) pair; quota_consumed is false for Mland replacements.
CREATE TABLE ticket_session_replacements (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ticket_id BIGINT UNSIGNED NOT NULL,
    source_session_id BIGINT UNSIGNED NOT NULL,
    target_session_id BIGINT UNSIGNED NOT NULL,
    replacement_type VARCHAR(32) NOT NULL,
    quota_consumed BOOLEAN NOT NULL,
    operation_reference VARCHAR(128) NOT NULL,
    correlation_reference VARCHAR(64) NULL,
    replaced_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_tsr_ticket (ticket_id),
    KEY idx_tsr_target (target_session_id),
    CONSTRAINT fk_tsr_ticket FOREIGN KEY (ticket_id) REFERENCES attendance_tickets (id),
    CONSTRAINT fk_tsr_source FOREIGN KEY (source_session_id) REFERENCES workshop_sessions (id),
    CONSTRAINT fk_tsr_target FOREIGN KEY (target_session_id) REFERENCES workshop_sessions (id),
    CONSTRAINT ck_tsr_type CHECK (replacement_type IN ('CUSTOMER_ADVANCE', 'CUSTOMER_LATE_ARRIVAL', 'MLAND')),
    CONSTRAINT ck_tsr_quota CHECK (replacement_type <> 'MLAND' OR quota_consumed = FALSE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
