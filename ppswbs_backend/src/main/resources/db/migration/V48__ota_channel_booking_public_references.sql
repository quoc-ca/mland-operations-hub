ALTER TABLE ota_channel_bookings
    ADD COLUMN public_reference CHAR(36) NULL;

UPDATE ota_channel_bookings
SET public_reference = UUID()
WHERE public_reference IS NULL;

ALTER TABLE ota_channel_bookings
    MODIFY COLUMN public_reference CHAR(36) NOT NULL,
    ADD CONSTRAINT uq_ota_channel_booking_public_reference UNIQUE (public_reference);
