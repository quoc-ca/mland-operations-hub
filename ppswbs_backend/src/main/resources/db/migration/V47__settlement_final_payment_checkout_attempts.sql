CREATE TABLE settlement_final_payment_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attempt_reference VARCHAR(128) NOT NULL,
    bill_reference VARCHAR(128) NOT NULL,
    booking_reference VARCHAR(128) NOT NULL,
    operation_reference VARCHAR(128) NOT NULL,
    merchant_reference VARCHAR(128) NOT NULL,
    amount_vnd BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    provider_create_date CHAR(14) NOT NULL,
    payment_url_snapshot VARCHAR(2048) NULL,
    lifecycle_status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_settlement_final_attempt_ref (attempt_reference),
    UNIQUE KEY uq_settlement_final_attempt_operation (operation_reference),
    UNIQUE KEY uq_settlement_final_attempt_merchant (merchant_reference),
    KEY ix_settlement_final_attempt_booking_created (booking_reference, created_at, id),
    KEY ix_settlement_final_attempt_expiry (expires_at),
    CONSTRAINT fk_settlement_final_attempt_bill FOREIGN KEY (bill_reference)
        REFERENCES settlement_final_bills (bill_reference),
    CONSTRAINT ck_settlement_final_attempt_amount CHECK (amount_vnd > 0),
    CONSTRAINT ck_settlement_final_attempt_currency CHECK (currency_code = 'VND'),
    CONSTRAINT ck_settlement_final_attempt_status CHECK (lifecycle_status IN
        ('PENDING','UNKNOWN','VERIFIED_PAID','VERIFIED_UNPAID'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
