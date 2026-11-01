ALTER TABLE vnpay_merchant_operations
    MODIFY COLUMN provider_response_code VARCHAR(2) NULL,
    MODIFY COLUMN provider_transaction_status VARCHAR(2) NULL,
    MODIFY COLUMN provider_transaction_type VARCHAR(2) NULL;
