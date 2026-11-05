CREATE TABLE payment_owner_registrations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    merchant_reference VARCHAR(128) NOT NULL,
    booking_reference VARCHAR(128) NOT NULL,
    payment_kind VARCHAR(24) NOT NULL,
    amount_vnd BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    lifecycle_status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id), UNIQUE KEY uq_payment_owner_merchant (merchant_reference),
    CONSTRAINT ck_payment_owner_kind CHECK (payment_kind IN ('DEPOSIT','FINAL_PAYMENT','REFUND')),
    CONSTRAINT ck_payment_owner_amount CHECK (amount_vnd > 0),
    CONSTRAINT ck_payment_owner_currency CHECK (currency_code = 'VND')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE payment_ingress_receipts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    delivery_reference VARCHAR(256) NOT NULL,
    merchant_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id), UNIQUE KEY uq_payment_ingress_delivery (delivery_reference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
