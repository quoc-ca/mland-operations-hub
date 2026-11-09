-- A catalogue add-on is confirmed at the workshop by Staff/Manager. The system generates the consent reference when the
-- operator confirms that the customer agreed and keeps who and when. It evidences the operator's confirmation only; it is
-- not proof of the customer's own consent. Variable charges and legacy rows carry none.
ALTER TABLE settlement_charges
    ADD COLUMN consent_reference VARCHAR(64) NULL,
    ADD COLUMN consent_recorded_by BIGINT NULL,
    ADD COLUMN consent_recorded_at TIMESTAMP(6) NULL;

CREATE UNIQUE INDEX uq_settlement_charges_consent_reference ON settlement_charges (consent_reference);
