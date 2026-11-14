CREATE TABLE booking_deposit_reconciliations (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    attempt_id BIGINT UNSIGNED NOT NULL,
    lifecycle_status VARCHAR(32) NOT NULL,
    next_query_at DATETIME(6) NULL,
    last_query_at DATETIME(6) NULL,
    verified_evidence_reference VARCHAR(128) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_bdr_attempt UNIQUE (attempt_id),
    CONSTRAINT fk_bdr_attempt FOREIGN KEY (attempt_id) REFERENCES booking_payment_attempts(id),
    INDEX ix_bdr_due (lifecycle_status, next_query_at)
);

INSERT INTO booking_deposit_reconciliations
    (attempt_id, lifecycle_status, next_query_at, created_at, updated_at)
SELECT id,
       CASE WHEN status = 'SUCCESS' THEN 'CONFIRMED' ELSE 'PENDING' END,
       CASE WHEN status = 'SUCCESS' THEN NULL ELSE expires_at END,
       CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM booking_payment_attempts;
