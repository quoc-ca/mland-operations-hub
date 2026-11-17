-- Hibernate maps provider timestamps as String columns. Keep the stored
-- YYYYMMDDHHmmss values unchanged while aligning MySQL metadata to VARCHAR.
ALTER TABLE booking_payment_attempts
    MODIFY COLUMN provider_create_date VARCHAR(14) NULL;

ALTER TABLE payment_owner_registrations
    MODIFY COLUMN provider_create_date VARCHAR(14) NULL;

ALTER TABLE settlement_evidence_conclusions
    MODIFY COLUMN original_provider_create_date VARCHAR(14) NULL;

ALTER TABLE settlement_final_payment_attempts
    MODIFY COLUMN provider_create_date VARCHAR(14) NOT NULL;

ALTER TABLE settlement_refund_allocations
    MODIFY COLUMN original_provider_create_date VARCHAR(14) NOT NULL;
