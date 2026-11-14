-- 003.2 FR-036 / T096: Operations records the sales channel of each ticket so it can refuse, with one typed
-- failure, to cancel, replace the sessions of or reissue the QR of an OTA ticket, whoever the caller is.
ALTER TABLE attendance_tickets
    ADD COLUMN sales_channel VARCHAR(16) NOT NULL DEFAULT 'DIRECT';

-- Tickets already issued for an OTA booking are marked from the OTA mapping of the booking reference.
UPDATE attendance_tickets t
    JOIN ota_channel_bookings o ON o.mland_booking_reference = t.booking_reference
SET t.sales_channel = 'OTA';
