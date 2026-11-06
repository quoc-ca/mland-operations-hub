CREATE TABLE workshop_booking_attendance_finalization_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_reference VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    finalized_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_workshop_booking_attendance_finalization UNIQUE (booking_reference, event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
