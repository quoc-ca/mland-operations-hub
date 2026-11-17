-- Development-data remediation: assign tracking UUIDs where old mapped work
-- items have no usable audit correlation. These do not recreate historical logs.
-- Preserve existing UUIDs, work-item identities, decisions and timestamps.
UPDATE ota_reconciliation_work_items
SET audit_reference = UUID(), updated_at = updated_at
WHERE mapping_id IS NOT NULL
  AND (audit_reference IS NULL OR CHAR_LENGTH(audit_reference) <> 36 OR NOT REGEXP_LIKE(audit_reference,
       '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$', 'c'));

ALTER TABLE ota_reconciliation_work_items
    ADD CONSTRAINT chk_ota_mapped_work_item_correlation CHECK (
        mapping_id IS NULL OR (
            audit_reference IS NOT NULL
            AND CHAR_LENGTH(audit_reference) = 36
            AND REGEXP_LIKE(audit_reference,
                '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$', 'c')
        )
    );
