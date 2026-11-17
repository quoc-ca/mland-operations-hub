ALTER TABLE payment_ingress_receipts
    MODIFY COLUMN response_code VARCHAR(2) NULL;

ALTER TABLE payment_ingress_receipts
    MODIFY COLUMN transaction_status VARCHAR(2) NULL;
