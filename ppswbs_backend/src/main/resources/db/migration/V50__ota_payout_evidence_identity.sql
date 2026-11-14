ALTER TABLE ota_payout_ledger_entries
    ADD COLUMN public_reference CHAR(36) NULL,
    ADD COLUMN evidence_type VARCHAR(24) NULL,
    ADD COLUMN allocation_reference VARCHAR(128) NULL;

UPDATE ota_payout_ledger_entries
SET public_reference = UUID()
WHERE public_reference IS NULL;

ALTER TABLE ota_payout_ledger_entries
    MODIFY COLUMN public_reference CHAR(36) NOT NULL,
    ADD CONSTRAINT uq_ota_payout_ledger_public_reference UNIQUE (public_reference),
    ADD CONSTRAINT uq_ota_payout_allocation_reference UNIQUE (allocation_reference);
