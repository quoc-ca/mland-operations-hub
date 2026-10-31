ALTER TABLE member_workshop_booking_quotes
    ADD COLUMN expires_at DATETIME(6) NULL AFTER created_at;

UPDATE member_workshop_booking_quotes
SET expires_at = DATE_ADD(created_at, INTERVAL 30 MINUTE)
WHERE expires_at IS NULL;

ALTER TABLE member_workshop_booking_quotes
    MODIFY COLUMN expires_at DATETIME(6) NOT NULL;
