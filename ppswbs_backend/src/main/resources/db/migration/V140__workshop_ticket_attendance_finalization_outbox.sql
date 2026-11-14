DROP TABLE IF EXISTS workshop_booking_attendance_finalization_outbox;

CREATE TABLE workshop_ticket_attendance_finalization_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    ticket_reference VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    window_closed_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ticket_attendance_outbox_ref_type UNIQUE (ticket_reference, event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

