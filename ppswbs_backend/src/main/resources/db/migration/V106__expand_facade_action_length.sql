-- Expand facade_action to 255 chars so idempotency claims for long REST paths do not fail with truncation
ALTER TABLE facade_command_idempotency_records
    MODIFY COLUMN facade_action VARCHAR(255) NOT NULL;

