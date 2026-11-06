ALTER TABLE payment_owner_registrations
    ADD COLUMN provider_create_date CHAR(14) NULL AFTER currency_code;

ALTER TABLE settlement_evidence_conclusions
    ADD COLUMN provider_transaction_number VARCHAR(15) NULL AFTER provider_transaction_reference,
    ADD COLUMN original_provider_create_date CHAR(14) NULL AFTER provider_transaction_number;

CREATE INDEX ix_settlement_evidence_booking_conclusion
    ON settlement_evidence_conclusions (booking_reference, conclusion, id);
