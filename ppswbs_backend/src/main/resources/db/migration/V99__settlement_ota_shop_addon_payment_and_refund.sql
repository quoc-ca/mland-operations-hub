ALTER TABLE payment_owner_registrations DROP CHECK ck_payment_owner_kind;
ALTER TABLE payment_owner_registrations ADD CONSTRAINT ck_payment_owner_kind
    CHECK (payment_kind IN ('DEPOSIT','FINAL_PAYMENT','SHOP_ADDON','REFUND','RETAIL'));

CREATE TABLE settlement_addon_payment_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attempt_reference VARCHAR(128) NOT NULL,
    bill_reference VARCHAR(128) NOT NULL,
    channel_booking_reference VARCHAR(128) NOT NULL,
    operation_reference VARCHAR(128) NOT NULL,
    merchant_reference VARCHAR(128) NOT NULL,
    amount_vnd BIGINT NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    provider_create_date VARCHAR(14) NOT NULL,
    payment_url_snapshot VARCHAR(2048) NULL,
    lifecycle_status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_settlement_addon_attempt_ref (attempt_reference),
    UNIQUE KEY uq_settlement_addon_attempt_operation (operation_reference),
    UNIQUE KEY uq_settlement_addon_attempt_merchant (merchant_reference),
    KEY ix_settlement_addon_attempt_bill (bill_reference),
    KEY ix_settlement_addon_attempt_booking (channel_booking_reference),
    KEY ix_settlement_addon_attempt_expiry (expires_at),
    CONSTRAINT fk_settlement_addon_attempt_bill FOREIGN KEY (bill_reference)
        REFERENCES settlement_addon_bills (bill_reference),
    CONSTRAINT ck_settlement_addon_attempt_amount CHECK (amount_vnd > 0),
    CONSTRAINT ck_settlement_addon_attempt_currency CHECK (currency_code = 'VND'),
    CONSTRAINT ck_settlement_addon_attempt_status CHECK (lifecycle_status IN
        ('PENDING','UNKNOWN','VERIFIED_PAID','VERIFIED_UNPAID','EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE settlement_addon_refunds (
    id BIGINT NOT NULL AUTO_INCREMENT,
    refund_reference VARCHAR(128) NOT NULL,
    case_reference VARCHAR(128) NOT NULL,
    bill_reference VARCHAR(128) NOT NULL,
    channel_booking_reference VARCHAR(128) NOT NULL,
    amount_vnd BIGINT NOT NULL,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'VND',
    reason VARCHAR(255) NOT NULL,
    manager_actor_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_settlement_addon_refund_ref (refund_reference),
    UNIQUE KEY uq_settlement_addon_refund_case (case_reference),
    KEY ix_settlement_addon_refund_bill (bill_reference),
    KEY ix_settlement_addon_refund_booking (channel_booking_reference),
    CONSTRAINT fk_settlement_addon_refund_bill FOREIGN KEY (bill_reference)
        REFERENCES settlement_addon_bills (bill_reference),
    CONSTRAINT ck_settlement_addon_refund_amount CHECK (amount_vnd > 0),
    CONSTRAINT ck_settlement_addon_refund_currency CHECK (currency_code = 'VND')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

