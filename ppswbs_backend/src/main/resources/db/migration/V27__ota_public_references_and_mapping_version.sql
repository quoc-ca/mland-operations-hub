ALTER TABLE ota_channel_connections
    ADD COLUMN public_reference CHAR(36) NULL;
UPDATE ota_channel_connections
SET public_reference = UUID()
WHERE public_reference IS NULL;
ALTER TABLE ota_channel_connections
    MODIFY COLUMN public_reference CHAR(36) NOT NULL,
    ADD CONSTRAINT uq_ota_connection_public_reference UNIQUE (public_reference);

ALTER TABLE ota_channel_mappings
    ADD COLUMN public_reference CHAR(36) NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 1;
UPDATE ota_channel_mappings
SET public_reference = UUID()
WHERE public_reference IS NULL;
ALTER TABLE ota_channel_mappings
    MODIFY COLUMN public_reference CHAR(36) NOT NULL,
    ADD CONSTRAINT uq_ota_mapping_public_reference UNIQUE (public_reference),
    ADD CONSTRAINT chk_ota_mapping_version_positive CHECK (version > 0);

ALTER TABLE ota_channel_price_snapshots
    ADD COLUMN public_reference CHAR(36) NULL;
UPDATE ota_channel_price_snapshots
SET public_reference = UUID()
WHERE public_reference IS NULL;
ALTER TABLE ota_channel_price_snapshots
    MODIFY COLUMN public_reference CHAR(36) NOT NULL,
    ADD CONSTRAINT uq_ota_price_public_reference UNIQUE (public_reference);

ALTER TABLE ota_reconciliation_work_items
    ADD COLUMN public_reference CHAR(36) NULL;
UPDATE ota_reconciliation_work_items
SET public_reference = UUID()
WHERE public_reference IS NULL;
ALTER TABLE ota_reconciliation_work_items
    MODIFY COLUMN public_reference CHAR(36) NOT NULL,
    ADD CONSTRAINT uq_ota_recon_work_item_public_reference UNIQUE (public_reference);
