-- V164: the session(s) a queued customer change replaces (003.3 T130, FR-044/FR-045a, decided 2026-11-15/16).
-- Nullable: PENDING requests stored before this change keep their current expiry calculation (R15); only new
-- requests carry it. Comma-separated session references, one per target, same order. No applied migration is edited.

ALTER TABLE settlement_ticket_change_requests
    ADD COLUMN from_session_references VARCHAR(512) NULL AFTER target_session_references;
