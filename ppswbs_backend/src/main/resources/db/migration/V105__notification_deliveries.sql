-- Outcome of each notification the mail consumer handled (Feature 007 minimal delivery). One row per
-- notification_reference, so a handoff delivered twice sends one email. No recipient, ticket or link is stored here.
CREATE TABLE notification_deliveries (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    notification_reference VARCHAR(64) NOT NULL,
    booking_reference VARCHAR(64) NOT NULL,
    notification_kind VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    sent_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_nd_reference (notification_reference),
    KEY idx_nd_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
