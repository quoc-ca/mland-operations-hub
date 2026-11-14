-- Hibernate maps BookingLatePaymentEvidence.currencyCode as VARCHAR(3).
-- The original table used CHAR(3), which fails schema validation on MySQL.
-- This is metadata-only: existing values are preserved.
ALTER TABLE booking_late_payment_evidence
    MODIFY COLUMN currency_code VARCHAR(3) NOT NULL;
