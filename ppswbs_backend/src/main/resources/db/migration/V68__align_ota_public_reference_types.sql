-- OTA public references are exposed as bounded String values in the JPA model.
ALTER TABLE ota_channel_bookings MODIFY COLUMN public_reference VARCHAR(36) NOT NULL;
ALTER TABLE ota_channel_connections MODIFY COLUMN public_reference VARCHAR(36) NOT NULL;
ALTER TABLE ota_channel_mappings MODIFY COLUMN public_reference VARCHAR(36) NOT NULL;
ALTER TABLE ota_channel_price_snapshots MODIFY COLUMN public_reference VARCHAR(36) NOT NULL;
ALTER TABLE ota_payout_ledger_entries MODIFY COLUMN public_reference VARCHAR(36) NOT NULL;
ALTER TABLE ota_reconciliation_work_items MODIFY COLUMN public_reference VARCHAR(36) NOT NULL;
