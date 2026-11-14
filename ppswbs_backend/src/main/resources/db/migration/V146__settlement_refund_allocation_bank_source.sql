-- V146: 003.3 T115 (FR-038, FR-050, Constitution V). A ticket-folio correction is capped by the ticket's real
-- collected money (its booking-time share plus confirmed counter/VNPay collections) and refunded by bank
-- transfer. Cash, bank transfers and a group payment's per-ticket share have no VNPay transaction number or
-- create date of their own, so these allocations leave both columns empty instead of carrying invented values.
-- Rows already written keep their values; VNPay-backed allocations still record the original provider facts.
ALTER TABLE settlement_refund_allocations
    MODIFY COLUMN original_provider_transaction_number VARCHAR(15) NULL,
    MODIFY COLUMN original_provider_create_date VARCHAR(14) NULL;
