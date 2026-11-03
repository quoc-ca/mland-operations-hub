ALTER TABLE workshop_sessions
    ADD COLUMN attendance_finalized_at DATETIME(6) NULL;

UPDATE workshop_sessions
SET attendance_finalized_at = updated_at
WHERE state = 'ATTENDANCE_FINALIZED'
  AND attendance_finalized_at IS NULL;

CREATE INDEX idx_settlement_final_bill_status_id
    ON settlement_final_bills (lifecycle_status, id);
