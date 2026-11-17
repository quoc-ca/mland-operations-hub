-- 003.3 T089: the Loyalty outcome scheduler must stop rescanning folios whose Loyalty outcome
-- is already decided (granted, no-show, valid cancellation, or not applicable such as a Guest).
-- NULL means "not decided yet". Existing folios start as NULL and are decided on the next run.
ALTER TABLE settlement_final_bills
    ADD COLUMN loyalty_processed_at TIMESTAMP(6) NULL AFTER frozen_at,
    ADD KEY ix_settlement_final_bills_loyalty_pending (lifecycle_status, loyalty_processed_at, id);
