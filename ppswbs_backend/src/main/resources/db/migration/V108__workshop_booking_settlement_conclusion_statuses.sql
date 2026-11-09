-- 003.1 T088 (owner decisions D13/D14, 2026-11-05): Settlement records the terminal
-- service outcome (COMPLETED, NO_SHOW) and the Mland-remedy refund status
-- (REFUND_PENDING, REFUNDED) on the booking. Append the new values; existing values
-- and their order are unchanged, so stored rows are not rewritten.
ALTER TABLE workshop_bookings
    MODIFY COLUMN booking_status
        ENUM('PENDING_PAYMENT', 'CONFIRMED', 'EXPIRED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')
        NOT NULL DEFAULT 'PENDING_PAYMENT',
    MODIFY COLUMN payment_status
        ENUM('UNPAID', 'DEPOSIT_PAID', 'FULLY_PAID', 'REFUND_PENDING', 'REFUNDED')
        NOT NULL DEFAULT 'UNPAID';
