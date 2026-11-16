-- V165: the outcome of the group voucher restoration that 003.3 triggers through the voucher and points facade when a
-- Mland-cancelled ticket is refunded or closed (003.3 T155, FR-042/FR-043): RESTORED, EXPIRED or ALREADY_RESTORED.
-- Nullable: remedies stored before this change (and tickets moved to a replacement session, which trigger nothing)
-- have no outcome. 003.3 only stores what the facade answered; it never writes voucher or points data.
-- No applied migration is edited.

ALTER TABLE settlement_ticket_mland_remedies
    ADD COLUMN voucher_restoration_outcome VARCHAR(24) NULL AFTER voucher_restored;
