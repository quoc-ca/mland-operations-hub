CREATE TABLE ticket_resend_throttles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_reference VARCHAR(64) NOT NULL,
    window_started_at TIMESTAMP(6) NOT NULL,
    request_count INT NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_ticket_resend_throttle_booking UNIQUE (booking_reference),
    CONSTRAINT chk_ticket_resend_throttle_count CHECK (request_count >= 0 AND request_count <= 3)
) ENGINE=InnoDB;
