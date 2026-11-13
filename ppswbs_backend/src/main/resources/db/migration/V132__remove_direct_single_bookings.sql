-- 003.1 FR-051 / T101 (decision 2026-11-13): a direct booking is only a booking group. Removes the
-- direct single bookings created by the removed flow; only dev/test data exists (FR-043), and
-- environments are reseeded. OTA bookings (booking_code 'OTA-...') are kept with their rows.
-- Tables are not dropped here: the 003.3 booking-wide settlement code that still maps them waits
-- for the open 003.3 decision (spec 003.3, decision 2026-11-13, "Open").

-- Rows whose foreign keys do not cascade go first.
DELETE FROM booking_deposit_reconciliations
WHERE attempt_id IN (
    SELECT a.id FROM booking_payment_attempts a
    JOIN workshop_bookings b ON b.id = a.booking_id
    WHERE b.booking_code NOT LIKE 'OTA-%');

DELETE FROM booking_late_payment_evidence
WHERE booking_id IN (
    SELECT b.id FROM workshop_bookings b WHERE b.booking_code NOT LIKE 'OTA-%');

-- Every Member quote belongs to the removed single-booking flow; group carts use workshop_cart_quotes.
DELETE FROM member_workshop_booking_quotes;

-- Session commitments, holds, invoices, payment attempts and receipts, tickets and their
-- entitlements, Magic Links and notification intents cascade from the booking.
DELETE FROM workshop_bookings WHERE booking_code NOT LIKE 'OTA-%';
