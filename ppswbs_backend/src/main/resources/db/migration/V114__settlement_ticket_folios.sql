-- V114: one folio per ticket booking (003.3 T093, FR-030).
-- A ticket's folio reuses settlement_final_bills: its booking_reference is the ticket code, which is unique,
-- so every charge, counter collection, refund and finalization rule of 003.3 already keys on it. What was
-- missing is which group a ticket's folio belongs to, for the group views and the later per-ticket refund
-- allocation of one shared booking-time payment.

ALTER TABLE settlement_final_bills
    ADD COLUMN group_reference VARCHAR(128) NULL DEFAULT NULL AFTER booking_reference,
    ADD KEY idx_sfb_group_reference (group_reference);
