-- 003.1 FR-040/FR-040a / T094 (decision 2026-11-13): public review reference, queue ordering by earliest session,
-- Manager decision and the 003.3 bank-refund request it opened. Append-only extension of V113.
ALTER TABLE late_payment_reviews
    ADD COLUMN review_reference CHAR(36) NULL AFTER id,
    ADD COLUMN earliest_session_at DATETIME(6) NULL,
    ADD COLUMN decision VARCHAR(16) NULL,
    ADD COLUMN refund_request_reference VARCHAR(64) NULL,
    ADD COLUMN correlation_reference VARCHAR(64) NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE late_payment_reviews SET review_reference = UUID() WHERE review_reference IS NULL;

ALTER TABLE late_payment_reviews
    MODIFY COLUMN review_reference CHAR(36) NOT NULL,
    ADD UNIQUE KEY uq_lpr_reference (review_reference),
    ADD KEY idx_lpr_manager_queue (outcome, earliest_session_at, result_due_at),
    ADD CONSTRAINT ck_lpr_decision CHECK (decision IS NULL OR decision IN ('CONFIRM', 'REFUND'));
