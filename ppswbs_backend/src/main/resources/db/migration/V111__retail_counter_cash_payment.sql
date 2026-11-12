-- FE-08 counter cash payment for store-pickup orders (decision 2026-11-12, FR-08-041..FR-08-049).
-- A checkout is paid either by VNPay or by cash at the branch counter; cash checkouts are held until
-- counter_payment_deadline (72 hours after acceptance). Each confirmed cash collection is recorded once
-- per branch order with its Vietnam business date so a future shared branch cash book can include it.

ALTER TABLE retail_grouped_checkouts
    ADD COLUMN payment_method VARCHAR(24) NOT NULL DEFAULT 'VNPAY',
    ADD COLUMN counter_payment_deadline DATETIME(6) NULL,
    ADD CONSTRAINT chk_retail_grouped_checkouts_payment_method
        CHECK (payment_method IN ('VNPAY', 'CASH_AT_COUNTER'));

ALTER TABLE retail_checkout_quotes
    ADD COLUMN payment_method VARCHAR(24) NOT NULL DEFAULT 'VNPAY',
    ADD CONSTRAINT chk_retail_checkout_quotes_payment_method
        CHECK (payment_method IN ('VNPAY', 'CASH_AT_COUNTER'));

CREATE TABLE retail_cash_collections (
    id VARCHAR(36) NOT NULL,
    grouped_checkout_id VARCHAR(36) NOT NULL,
    retail_order_id VARCHAR(36) NOT NULL,
    location_id BIGINT UNSIGNED NOT NULL,
    business_date DATE NOT NULL,
    applied_amount DECIMAL(19,2) NOT NULL,
    amount_received DECIMAL(19,2) NOT NULL,
    change_returned DECIMAL(19,2) NOT NULL,
    confirmed_by_account_id BIGINT UNSIGNED NOT NULL,
    confirmed_at DATETIME(6) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_retail_cash_collections_checkout FOREIGN KEY (grouped_checkout_id) REFERENCES retail_grouped_checkouts (id),
    CONSTRAINT fk_retail_cash_collections_order FOREIGN KEY (retail_order_id) REFERENCES retail_orders (id),
    CONSTRAINT chk_retail_cash_collections_amounts CHECK (applied_amount > 0
        AND amount_received >= applied_amount AND change_returned = amount_received - applied_amount),
    UNIQUE KEY uq_retail_cash_collections_order (retail_order_id),
    UNIQUE KEY uq_retail_cash_collections_actor_key (confirmed_by_account_id, idempotency_key),
    KEY idx_retail_cash_collections_location_date (location_id, business_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
