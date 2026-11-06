ALTER TABLE settlement_charges
    ADD COLUMN description VARCHAR(200) NULL;

ALTER TABLE settlement_business_audits
    ADD COLUMN reason_reference VARCHAR(500) NULL;
