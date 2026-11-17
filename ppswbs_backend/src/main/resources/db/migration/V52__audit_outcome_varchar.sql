-- V52__audit_outcome_varchar.sql
-- Change account_audit_events.outcome from ENUM to VARCHAR(16) per FE-001 approved baseline

ALTER TABLE account_audit_events
    MODIFY COLUMN outcome VARCHAR(16) NOT NULL;

