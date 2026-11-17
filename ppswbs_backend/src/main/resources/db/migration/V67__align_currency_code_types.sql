-- Currency codes are modelled as bounded String values (VARCHAR(3)) in JPA.
-- Preserve all existing ISO codes while aligning legacy CHAR columns.
ALTER TABLE ota_addon_bill_links MODIFY COLUMN currency_code VARCHAR(3) NOT NULL;
ALTER TABLE ota_channel_bookings MODIFY COLUMN currency_code VARCHAR(3) NOT NULL;
ALTER TABLE ota_channel_price_snapshots MODIFY COLUMN currency_code VARCHAR(3) NOT NULL;
ALTER TABLE ota_customer_payment_evidence MODIFY COLUMN currency_code VARCHAR(3) NOT NULL;
ALTER TABLE ota_payout_ledger_entries MODIFY COLUMN currency_code VARCHAR(3) NOT NULL;
ALTER TABLE ota_payout_positions MODIFY COLUMN currency_code VARCHAR(3) NOT NULL;
ALTER TABLE payment_ingress_receipts MODIFY COLUMN currency_code VARCHAR(3) NULL;
ALTER TABLE payment_owner_registrations MODIFY COLUMN currency_code VARCHAR(3) NOT NULL;
ALTER TABLE settlement_addon_bills MODIFY COLUMN currency_code VARCHAR(3) NOT NULL;
ALTER TABLE settlement_charges MODIFY COLUMN currency_code VARCHAR(3) NOT NULL;
ALTER TABLE settlement_customer_cancellation_snapshots MODIFY COLUMN currency_code VARCHAR(3) NOT NULL;
ALTER TABLE settlement_evidence_conclusions MODIFY COLUMN currency_code VARCHAR(3) NOT NULL;
ALTER TABLE settlement_final_bills MODIFY COLUMN currency_code VARCHAR(3) NOT NULL;
ALTER TABLE settlement_final_payment_attempts MODIFY COLUMN currency_code VARCHAR(3) NOT NULL;
ALTER TABLE settlement_mland_remedy_snapshots MODIFY COLUMN currency_code VARCHAR(3) NOT NULL;
ALTER TABLE settlement_refund_allocations MODIFY COLUMN currency_code VARCHAR(3) NOT NULL;
ALTER TABLE settlement_refund_policy_snapshots MODIFY COLUMN currency_code VARCHAR(3) NOT NULL;
