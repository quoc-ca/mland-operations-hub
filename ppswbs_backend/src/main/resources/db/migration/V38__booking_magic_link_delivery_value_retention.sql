-- Track the one-time scrub of the raw bearer URL from notification snapshots.
-- The protected hash remains available for lifecycle enforcement and audit-safe lookup.
ALTER TABLE booking_magic_links
    ADD COLUMN delivery_value_scrubbed_at DATETIME(6) NULL AFTER revoked_by_account_id;
