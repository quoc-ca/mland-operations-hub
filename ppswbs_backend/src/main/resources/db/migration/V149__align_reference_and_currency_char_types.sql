-- Align CHAR columns to VARCHAR for Hibernate JPA String mapping
ALTER TABLE booking_buyer_verifications
    MODIFY COLUMN verification_reference VARCHAR(36) NOT NULL,
    MODIFY COLUMN buyer_verification_reference VARCHAR(36) NULL,
    MODIFY COLUMN otp_hash VARCHAR(64) NULL,
    MODIFY COLUMN otp_salt VARCHAR(32) NULL;

ALTER TABLE late_payment_reviews
    MODIFY COLUMN review_reference VARCHAR(36) NOT NULL;

ALTER TABLE booking_group_line_sessions
    MODIFY COLUMN session_reference VARCHAR(36) NOT NULL,
    MODIFY COLUMN hold_reference VARCHAR(36) NULL;

ALTER TABLE workshop_cart_quotes
    MODIFY COLUMN quote_ref VARCHAR(36) NOT NULL;

ALTER TABLE ticket_bookings
    MODIFY COLUMN operations_ticket_reference VARCHAR(36) NULL;

ALTER TABLE ring_materials
    MODIFY COLUMN currency VARCHAR(3) NOT NULL;

ALTER TABLE ring_shapes
    MODIFY COLUMN currency VARCHAR(3) NOT NULL;

ALTER TABLE ring_attachments
    MODIFY COLUMN currency VARCHAR(3) NOT NULL;

ALTER TABLE ring_gemstones
    MODIFY COLUMN currency VARCHAR(3) NOT NULL;

ALTER TABLE ring_designs
    MODIFY COLUMN currency VARCHAR(3) NULL;

ALTER TABLE settlement_folio_corrections
    MODIFY COLUMN currency_code VARCHAR(3) NOT NULL;
