ALTER TABLE payment_ingress_receipts
    ADD COLUMN response_code CHAR(2) NULL,
    ADD COLUMN transaction_status CHAR(2) NULL,
    ADD COLUMN terminal_outcome BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN amount_vnd BIGINT NULL,
    ADD COLUMN currency_code CHAR(3) NULL,
    ADD COLUMN provider_transaction_number VARCHAR(15) NULL,
    ADD KEY idx_pir_terminal_merchant (merchant_reference, terminal_outcome);

ALTER TABLE booking_payment_attempts
    ADD COLUMN provider_create_date CHAR(14) NULL;

CREATE TABLE booking_late_payment_evidence (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    booking_id BIGINT UNSIGNED NOT NULL,
    booking_code VARCHAR(32) NOT NULL,
    merchant_reference VARCHAR(128) NOT NULL,
    evidence_reference VARCHAR(128) NOT NULL,
    provider_transaction_reference VARCHAR(64) NULL,
    amount_vnd BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    verified_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_blpe_booking FOREIGN KEY (booking_id) REFERENCES workshop_bookings (id),
    CONSTRAINT uq_blpe_merchant_reference UNIQUE (merchant_reference),
    CONSTRAINT uq_blpe_evidence_reference UNIQUE (evidence_reference),
    CONSTRAINT ck_blpe_amount CHECK (amount_vnd > 0),
    CONSTRAINT ck_blpe_currency CHECK (currency_code = 'VND'),
    KEY idx_blpe_booking (booking_code, verified_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
