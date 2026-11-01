ALTER TABLE ota_payout_ledger_entries
    ADD COLUMN payout_reference VARCHAR(200) NULL;

CREATE INDEX idx_ota_payout_ledger_position_payout_ref
    ON ota_payout_ledger_entries (payout_position_id, payout_reference);
