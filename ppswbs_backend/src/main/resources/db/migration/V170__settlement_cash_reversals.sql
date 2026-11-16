-- 003.3 T144 (FR-036, FR-053): Manager-only negative cash entry that corrects a CASH collection of a closed day.
-- The closed day's cash close and the original collection row are never edited; the reversal is dated the
-- current Vietnam working day (the next day once the counted amount was submitted).
CREATE TABLE settlement_cash_reversals (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reversal_reference VARCHAR(128) NOT NULL,
    ticket_code VARCHAR(128) NOT NULL,
    bill_reference VARCHAR(128) NOT NULL,
    collection_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    amount_vnd BIGINT NOT NULL,
    cash_business_day DATE NOT NULL,
    original_cash_business_day DATE NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    recorded_by_account_id BIGINT NOT NULL,
    recorded_at TIMESTAMP(6) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_settlement_cash_reversal_ref (reversal_reference),
    UNIQUE KEY uq_settlement_cash_reversal_collection (collection_id),
    UNIQUE KEY uq_settlement_cash_reversal_idem (ticket_code, idempotency_key),
    KEY ix_settlement_cash_reversal_bill (bill_reference),
    KEY ix_settlement_cash_reversal_day (branch_id, cash_business_day),
    CONSTRAINT fk_settlement_cash_reversal_collection FOREIGN KEY (collection_id)
        REFERENCES settlement_folio_collections (id),
    CONSTRAINT ck_settlement_cash_reversal_amount CHECK (amount_vnd < 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
