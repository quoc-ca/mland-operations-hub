-- 003.1 FR-051 / T101 (decision 2026-11-13): drops the tables only the removed single direct booking flow
-- used. Their rows were deleted by V132 (dev/test data only, FR-043) and no entity maps them any more.
-- Kept, because code still reads them: workshop_bookings and booking_session_commitments (OTA bookings),
-- booking_invoices, booking_payment_attempts, booking_payment_receipts and booking_late_payment_evidence
-- (the 003.3 booking-wide settlement, open 003.3 decision of 2026-11-13), member_workshop_booking_quotes
-- (the Loyalty single-booking source it serves), booking_audit_evidence and booking_idempotency_records
-- (shared with the booking-group flow).

-- Single-booking tickets and their session entitlements; a group's tickets are ticket_bookings.
DROP TABLE IF EXISTS booking_ticket_session_entitlements;
DROP TABLE IF EXISTS booking_tickets;

-- Capacity holds of a single booking; a group holds through booking_group_line_sessions.
DROP TABLE IF EXISTS booking_capacity_holds;

-- Single-booking Magic Links and emails; groups use booking_group_magic_links and
-- booking_group_notification_intents.
DROP TABLE IF EXISTS booking_magic_links;
DROP TABLE IF EXISTS booking_notification_intents;

-- Deposit reconciliation of the removed 50% deposit flow; a group payment is reconciled by
-- GroupPaymentQueryDrRecoveryScheduler against its own attempt.
DROP TABLE IF EXISTS booking_deposit_reconciliations;
