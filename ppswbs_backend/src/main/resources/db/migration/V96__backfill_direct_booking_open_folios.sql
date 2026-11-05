-- V96__backfill_direct_booking_open_folios.sql
-- Backfill single OPEN folios for existing confirmed direct Mland bookings without folios (owner decisions D1, D4, D5).
-- Booking OTA and cancelled/expired bookings are excluded. Idempotent on repeated runs.

INSERT INTO settlement_final_bills (
    bill_reference,
    booking_reference,
    post_promotion_amount_vnd,
    accepted_deposit_amount_vnd,
    remaining_amount_vnd,
    currency_code,
    lifecycle_status,
    frozen_at,
    created_at
)
SELECT
    CONCAT('SETTLE-BILL-', UUID()),
    wb.booking_code,
    bi.post_promotion_amount,
    bi.deposit_amount,
    bi.remaining_amount,
    'VND',
    'OPEN',
    COALESCE(wb.confirmed_at, wb.created_at, NOW(6)),
    NOW(6)
FROM workshop_bookings wb
JOIN booking_invoices bi ON bi.booking_id = wb.id
WHERE wb.booking_status = 'CONFIRMED'
  AND wb.booking_code NOT IN (SELECT booking_reference FROM settlement_final_bills)
  AND wb.booking_code NOT IN (
      SELECT mland_booking_reference
      FROM ota_channel_bookings
      WHERE mland_booking_reference IS NOT NULL
  );

