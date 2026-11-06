CREATE TABLE retail_payment_reconciliation (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    checkout_id VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    merchant_reference VARCHAR(128) NOT NULL,
    provider_transaction VARCHAR(64) NOT NULL,
    amount_vnd BIGINT NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    reason VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_retail_reconciliation_merchant UNIQUE (merchant_reference),
    CONSTRAINT uq_retail_reconciliation_provider UNIQUE (provider_transaction),
    CONSTRAINT fk_retail_reconciliation_checkout FOREIGN KEY (checkout_id) REFERENCES retail_grouped_checkouts(id)
);
