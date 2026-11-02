CREATE TABLE workshop_cancellation_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_reference VARCHAR(128) NOT NULL,
    session_reference VARCHAR(128) NOT NULL,
    affected_booking_references_json JSON NOT NULL,
    cancellation_reason_reference VARCHAR(1000) NOT NULL,
    operation_reference VARCHAR(128) NOT NULL,
    correlation_reference VARCHAR(128) NOT NULL,
    cancellation_case_reference VARCHAR(128) NOT NULL,
    published_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_workshop_cancellation_outbox_event UNIQUE (event_reference)
) ENGINE=InnoDB;
