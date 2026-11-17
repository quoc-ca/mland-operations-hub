ALTER TABLE ota_addon_bill_links
    ADD COLUMN addon_type VARCHAR(32) NULL,
    ADD COLUMN item_code VARCHAR(100) NULL,
    ADD COLUMN item_description VARCHAR(500) NULL,
    ADD COLUMN customer_consent_reference VARCHAR(128) NULL,
    ADD COLUMN accepted_at DATETIME(6) NULL;

