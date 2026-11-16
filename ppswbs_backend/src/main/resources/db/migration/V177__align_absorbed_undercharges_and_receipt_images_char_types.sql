-- Align CHAR columns to VARCHAR for Hibernate JPA String mapping
ALTER TABLE settlement_absorbed_undercharges
    MODIFY COLUMN currency_code VARCHAR(3) NOT NULL;

ALTER TABLE settlement_refund_receipt_images
    MODIFY COLUMN id VARCHAR(36) NOT NULL;
