ALTER TABLE ota_payout_ledger_entries
    ADD COLUMN reason VARCHAR(1000) NULL,
    ADD COLUMN related_record_reference VARCHAR(128) NULL;
