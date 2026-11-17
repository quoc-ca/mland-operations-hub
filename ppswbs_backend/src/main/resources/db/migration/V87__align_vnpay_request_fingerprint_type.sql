ALTER TABLE vnpay_merchant_operations
    MODIFY COLUMN request_fingerprint VARCHAR(64) NOT NULL;
