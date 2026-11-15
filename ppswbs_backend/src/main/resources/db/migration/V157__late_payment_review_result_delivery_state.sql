-- V157: whether the result of a late payment reached the buyer (003.1 FR-040a, T129; decided 2026-11-16).
-- PENDING = no delivery failure seen; RESEND_SCHEDULED = a result email failed and a resend is queued;
-- MANUAL_CONTACT_REQUIRED = resends exhausted or the result deadline passed, a Manager phones the buyer.
-- A failed email never counts as "reached". Holds no contact data.
ALTER TABLE late_payment_reviews
    ADD COLUMN result_delivery_state ENUM('PENDING', 'RESEND_SCHEDULED', 'MANUAL_CONTACT_REQUIRED')
        NOT NULL DEFAULT 'PENDING' AFTER result_due_at;
