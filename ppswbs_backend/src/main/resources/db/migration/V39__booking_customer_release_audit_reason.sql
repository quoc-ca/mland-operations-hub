ALTER TABLE booking_audit_evidence
    ADD COLUMN action_reason VARCHAR(1000) NULL AFTER manager_reason;
