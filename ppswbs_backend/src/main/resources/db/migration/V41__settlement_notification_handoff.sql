CREATE TABLE settlement_notification_handoffs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    notification_reference VARCHAR(128) NOT NULL,
    settlement_reference VARCHAR(128) NOT NULL,
    notification_kind VARCHAR(64) NOT NULL,
    correlation_reference VARCHAR(128) NOT NULL,
    stored_recipient VARCHAR(320) NOT NULL,
    rendered_settlement_content TEXT NOT NULL,
    payment_action_link TEXT NULL,
    payment_action_link_expires_at TIMESTAMP(6) NULL,
    payload_expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    dispatched_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_settlement_notification_reference (notification_reference),
    KEY ix_settlement_notification_payload_expiry (payload_expires_at),
    KEY ix_settlement_notification_link_expiry (payment_action_link_expires_at),
    KEY ix_settlement_notification_dispatch (dispatched_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
