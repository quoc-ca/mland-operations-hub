ALTER TABLE booking_payment_attempts
    MODIFY COLUMN payment_url_snapshot VARCHAR(2048) NULL DEFAULT NULL;
